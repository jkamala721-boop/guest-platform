package com.guest_platform.service.photo;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PhotoProcessorTest {
    private final PhotoProcessor processor=new PhotoProcessor();
    byte[] jpeg() throws Exception {
        var source=new BufferedImage(80,40,BufferedImage.TYPE_INT_RGB);
        var g=source.createGraphics(); g.setColor(Color.RED);g.fillRect(0,0,40,40);g.setColor(Color.BLUE);g.fillRect(40,0,40,40);g.dispose();
        var out=new ByteArrayOutputStream();ImageIO.write(source,"jpeg",out);return out.toByteArray();
    }
    byte[] oriented(byte[] jpeg,int orientation) throws Exception {
        // JPEG APP1, Exif header, little-endian TIFF, one orientation entry.
        byte[] exif={69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,(byte)orientation,0,0,0,0,0,0,0};
        var out=new ByteArrayOutputStream();out.write(jpeg,0,2);out.write(255);out.write(225);out.write(0);out.write(exif.length+2);out.write(exif);out.write(jpeg,2,jpeg.length-2);return out.toByteArray();
    }
    @Test void appliesAllExifOrientationsAndStripsMetadata() throws Exception {
        for(int orientation=1;orientation<=8;orientation++) {
            var variants=processor.process(new MockMultipartFile("file","camera.jpg","image/jpeg",oriented(jpeg(),orientation)));
            assertEquals(orientation>=5?40:80,variants.display().width());
            assertEquals(orientation>=5?80:40,variants.display().height());
            var metadata=com.drew.imaging.ImageMetadataReader.readMetadata(new ByteArrayInputStream(variants.display().bytes()));
            assertNull(metadata.getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory.class));
            BufferedImage result=ImageIO.read(new ByteArrayInputStream(variants.display().bytes()));
            // Orientation 6 rotates the red left half to the top half.
            if(orientation==6) assertTrue(new Color(result.getRGB(20,10)).getRed()>200);
        }
    }
    @Test void rejectsDimensionBombAndAnimatedPng() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),"png",out);
        byte[] png=out.toByteArray();
        byte[] huge=png.clone(); ByteBuffer.wrap(huge,16,8).putInt(5000).putInt(5000);
        assertThrows(IllegalArgumentException.class,()->processor.process(new MockMultipartFile("file","huge.png","image/png",huge)));
        var animated=new ByteArrayOutputStream(); animated.write(png,0,33);
        animated.write(new byte[]{0,0,0,8,97,99,84,76,0,0,0,2,0,0,0,0,0,0,0,0}); animated.write(png,33,png.length-33);
        assertThrows(IllegalArgumentException.class,()->processor.process(new MockMultipartFile("file","animation.png","image/png",animated.toByteArray())));
    }
    @Test void flattensTransparencyAndNeverUpscales() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,20,BufferedImage.TYPE_INT_ARGB),"png",out);
        var result=processor.process(new MockMultipartFile("file","transparent.png","image/png",out.toByteArray()));
        assertEquals(10,result.thumbnail().width()); assertEquals(20,result.display().height());
        var pixel=new Color(ImageIO.read(new ByteArrayInputStream(result.display().bytes())).getRGB(5,5));
        assertTrue(pixel.getRed()>245 && pixel.getGreen()>245 && pixel.getBlue()>245);
    }
    @Test void rejectsTruncatedJpegRatherThanPublishingPartiallyDecodedPixels() throws Exception {
        byte[] valid=jpeg();
        byte[] truncated=java.util.Arrays.copyOf(valid,valid.length-20);
        assertThrows(IllegalArgumentException.class,()->processor.process(new MockMultipartFile("file","truncated.jpg","image/jpeg",truncated)));
    }
}
