package com.guest_platform.service.photo;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;

@Component
public class PhotoProcessor {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public record Image(byte[] bytes, int width, int height) { }
    public record Variants(Image thumbnail, Image display) { }
    public Variants process(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES
                || !("image/jpeg".equals(file.getContentType()) || "image/png".equals(file.getContentType())))
            throw invalid();
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw invalid();
            boolean png = bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78
                    && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            boolean jpeg = bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255;
            if (!(png && "image/png".equals(file.getContentType()) || jpeg && "image/jpeg".equals(file.getContentType()))) throw invalid();
            if (png) rejectAnimatedPng(bytes);
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid();
                ImageReader reader = readers.next();
                try {
                    var warning = new java.util.concurrent.atomic.AtomicBoolean();
                    reader.addIIOReadWarningListener((source, message) -> warning.set(true));
                    reader.setInput(stream, false, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 10000 || height > 10000 || (long)width * height > 20_000_000
                            || reader.getNumImages(true) != 1) throw invalid();
                    // Decode only enough source pixels for the largest output. Dimensions are checked first.
                    var params = reader.getDefaultReadParam();
                    int sample = Math.max(1, Math.max(width, height) / 1920);
                    params.setSourceSubsampling(sample, sample, 0, 0);
                    BufferedImage decoded = reader.read(0, params);
                    if (decoded == null || warning.get()) throw invalid();
                    var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
                    var exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                    int orientation = exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)
                            ? exif.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
                    BufferedImage display = resize(decoded, 1920, orientation);
                    try { return new Variants(encode(resize(display, 480, 1)), encode(display)); }
                    finally { decoded.flush(); display.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (IOException | com.drew.imaging.ImageProcessingException | com.drew.metadata.MetadataException e) {
            throw invalid();
        } catch (RuntimeException e) {
            throw invalid();
        }
    }
    private void rejectAnimatedPng(byte[] data) {
        int offset=8;
        while (offset + 12 <= data.length) {
            long size = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(data, offset, 4).getInt());
            if (size > data.length - offset - 12) throw invalid();
            String name = new String(data, offset+4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("acTL".equals(name) || "fcTL".equals(name) || "fdAT".equals(name)) throw invalid();
            offset += (int)size+12;
            if ("IEND".equals(name)) return;
        }
        throw invalid();
    }
    private BufferedImage resize(BufferedImage source, int max, int orientation) {
        int w=source.getWidth(), h=source.getHeight();
        boolean swap=orientation>=5 && orientation<=8;
        int ow=swap?h:w, oh=swap?w:h;
        double scale=Math.min(1.0, (double)max/Math.max(ow,oh));
        BufferedImage result=new BufferedImage(Math.max(1,(int)Math.round(ow*scale)), Math.max(1,(int)Math.round(oh*scale)), BufferedImage.TYPE_INT_RGB);
        Graphics2D g=result.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0,0,result.getWidth(),result.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.scale((double)result.getWidth()/ow, (double)result.getHeight()/oh);
            AffineTransform transform=switch(orientation) {
                case 2 -> new AffineTransform(-1,0,0,1,w,0);
                case 3 -> new AffineTransform(-1,0,0,-1,w,h);
                case 4 -> new AffineTransform(1,0,0,-1,0,h);
                case 5 -> new AffineTransform(0,1,1,0,0,0);
                case 6 -> new AffineTransform(0,1,-1,0,h,0);
                case 7 -> new AffineTransform(0,-1,-1,0,h,w);
                case 8 -> new AffineTransform(0,-1,1,0,0,w);
                default -> new AffineTransform();
            };
            g.drawImage(source,transform,null);
        } finally { g.dispose(); }
        return result;
    }
    private Image encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ImageWriter writer=ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            var params=writer.getDefaultWriteParam(); params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); params.setCompressionQuality(0.84f);
            writer.write(null,new IIOImage(image,null,null),params);
        } finally { writer.dispose(); }
        return new Image(out.toByteArray(),image.getWidth(),image.getHeight());
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Choose a valid JPEG/PNG up to 10 MiB and 20 megapixels"); }
}
