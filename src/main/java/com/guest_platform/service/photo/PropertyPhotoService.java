package com.guest_platform.service.photo;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import com.guest_platform.repository.PropertyRepository;
import com.guest_platform.service.*;
import com.guest_platform.exception.*;

/** Short database transactions coordinate metadata; remote I/O never holds property locks. */
@Service
public class PropertyPhotoService {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final PropertyRepository properties;
    private final HostOperationalAccessService access;
    private final PhotoStorage storage;
    private final PhotoProcessor processor;
    private final PhotoSettings settings;
    private final Semaphore uploads = new Semaphore(1);
    private final java.util.concurrent.atomic.AtomicBoolean cleanupRunning = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ExecutorService cleanupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("property-photo-cleanup").daemon(true).factory());
    public PropertyPhotoService(JdbcTemplate db, PlatformTransactionManager manager, PropertyRepository properties,
            HostOperationalAccessService access, PhotoStorage storage, PhotoProcessor processor, PhotoSettings settings) {
        this.db=db; transactions=new TransactionTemplate(manager); this.properties=properties;
        this.access=access; this.storage=storage; this.processor=processor; this.settings=settings;
    }
    private record Row(UUID id, UUID property, String prefix, String state, int order, boolean cover,
            int tw, int th, int dw, int dh) { }
    public record Photo(UUID id, int sortOrder, boolean cover, String thumbnailUrl, String displayUrl,
            int thumbnailWidth, int thumbnailHeight, int displayWidth, int displayHeight, Instant expiresAt) { }
    public record Gallery(boolean enabled, List<Photo> photos) { }
    private <T> T tx(Supplier<T> work) { return transactions.execute(s -> work.get()); }
    private UUID owner(UUID host, UUID property, boolean mutation) {
        if (mutation) access.requireAccess(host);
        var p = properties.findByIdAndHostId(property,host)
                .orElseThrow(() -> new ResourceNotFoundException("Property was not found"));
        if (mutation) {
            properties.findForUpdateById(property).orElseThrow();
            if (!p.getHost().isActive()) throw new ResourceNotFoundException("Property was not found");
        }
        return p.getId();
    }
    private List<Row> rows(UUID property) {
        return db.query("select * from property_photos where property_id=? and state='READY' order by sort_order,id",
                (rs,n)->row(rs),property);
    }
    private Row row(ResultSet r) throws SQLException {
        return new Row(r.getObject("id",UUID.class),r.getObject("property_id",UUID.class),r.getString("object_prefix"),
                r.getString("state"),r.getInt("sort_order"),r.getBoolean("is_cover"),r.getInt("thumb_width"),
                r.getInt("thumb_height"),r.getInt("display_width"),r.getInt("display_height"));
    }
    public Gallery hostGallery(UUID host, UUID property) {
        tx(()->owner(host,property,false)); return gallery(property,Instant.now().plusSeconds(145));
    }
    public Gallery gallery(UUID property, Instant authorizedUntil) {
        if (!settings.enabled) return new Gallery(false,List.of());
        List<Row> photos=rows(property);
        if (photos.isEmpty()) return new Gallery(true,List.of());
        Instant start=Instant.now();
        // Reserve the provider's 20s request budget so URLs cannot outlive token authorization.
        int ttl=(int)Math.min(120,Duration.between(start,authorizedUntil).getSeconds()-20);
        if (ttl<1) return new Gallery(true,List.of());
        List<String> paths=photos.stream().flatMap(p->java.util.stream.Stream.of(p.prefix+"/thumb.jpg",p.prefix+"/display.jpg")).toList();
        Map<String,String> urls=storage.sign(paths,ttl);
        return new Gallery(true,photos.stream().map(p->new Photo(p.id,p.order,p.cover,urls.get(p.prefix+"/thumb.jpg"),
                urls.get(p.prefix+"/display.jpg"),p.tw,p.th,p.dw,p.dh,start.plusSeconds(ttl))).toList());
    }
    public UUID upload(UUID host, UUID property, MultipartFile file) {
        settings.requireEnabled();
        tx(()->owner(host,property,true));
        if (!uploads.tryAcquire()) throw new PhotoUnavailableException();
        UUID id=UUID.randomUUID(); String prefix=property+"/"+id;
        boolean reserved=false;
        try {
            var variants=processor.process(file);
            tx(()->{
                owner(host,property,true);
                if (db.queryForObject("select count(*) from property_photos where property_id=? and state in ('READY','UPLOADING')",Integer.class,property)>=15)
                    throw new ConflictException("A property can have at most 15 photographs");
                db.update("insert into property_photos(id,property_id,object_prefix,state,sort_order,is_cover,created_at,updated_at) values (?,?,?,'UPLOADING',0,false,?,?)",
                        id,property,prefix,Timestamp.from(Instant.now()),Timestamp.from(Instant.now())); return null;
            });
            reserved=true;
            storage.upload(prefix+"/thumb.jpg",variants.thumbnail().bytes());
            storage.upload(prefix+"/display.jpg",variants.display().bytes());
            tx(()->{
                owner(host,property,true);
                var ready=rows(property);
                int position=ready.stream().mapToInt(Row::order).max().orElse(-1)+1;
                int changed=db.update("update property_photos set state='READY',sort_order=?,is_cover=?,thumb_width=?,thumb_height=?,thumb_bytes=?,display_width=?,display_height=?,display_bytes=?,updated_at=? where id=? and state='UPLOADING'",
                        position,ready.isEmpty(),variants.thumbnail().width(),variants.thumbnail().height(),variants.thumbnail().bytes().length,
                        variants.display().width(),variants.display().height(),variants.display().bytes().length,Timestamp.from(Instant.now()),id);
                if(changed!=1) throw new ConflictException("Upload expired; please retry"); return null;
            });
            return id;
        } catch (RuntimeException e) {
            if (reserved) {
                // If this DB write fails, the durable UPLOADING row is reclaimed after one hour.
                try { db.update("update property_photos set state='DELETE_PENDING',is_cover=false,updated_at=? where id=? and state='UPLOADING'",Timestamp.from(Instant.now()),id); }
                catch (RuntimeException ignored) { /* durable reservation remains for reconciliation */ }
            }
            throw e;
        } finally { uploads.release(); }
    }
    public void reorder(UUID host, UUID property, List<UUID> ids) {
        settings.requireEnabled(); tx(()->{
            owner(host,property,true); var current=rows(property);
            if(ids==null || ids.size()!=current.size() || new HashSet<>(ids).size()!=ids.size()
                    || !new HashSet<>(ids).equals(new HashSet<>(current.stream().map(Row::id).toList())))
                throw new ConflictException("Photographs changed. Refresh and retry.");
            for(int i=0;i<ids.size();i++) db.update("update property_photos set sort_order=?,updated_at=? where id=?",i,Timestamp.from(Instant.now()),ids.get(i));
            return null;
        });
    }
    public void cover(UUID host, UUID property, UUID photo) {
        settings.requireEnabled(); tx(()->{
            owner(host,property,true);
            if(rows(property).stream().noneMatch(p->p.id.equals(photo))) throw new ResourceNotFoundException("Photograph was not found");
            db.update("update property_photos set is_cover=false where property_id=? and is_cover=true",property);
            db.update("update property_photos set is_cover=true,updated_at=? where id=?",Timestamp.from(Instant.now()),photo); return null;
        });
    }
    public void delete(UUID host, UUID property, UUID photo) {
        settings.requireEnabled(); tx(()->{
            owner(host,property,true);
            int count=db.update("update property_photos set state='DELETE_PENDING',is_cover=false,updated_at=? where property_id=? and id=?",Timestamp.from(Instant.now()),property,photo);
            if(count==0) throw new ResourceNotFoundException("Photograph was not found");
            var remaining=rows(property);
            if(!remaining.isEmpty() && remaining.stream().noneMatch(Row::cover))
                db.update("update property_photos set is_cover=true where id=?",remaining.getFirst().id);
            return null;
        });
    }
    @Scheduled(fixedDelayString="${app.photos.cleanup-delay-ms:60000}", initialDelayString="${app.photos.cleanup-delay-ms:60000}")
    public void scheduleCleanup() {
        if (!settings.enabled || !cleanupRunning.compareAndSet(false,true)) return;
        try {
            cleanupExecutor.submit(() -> {
                try { cleanup(); }
                catch (RuntimeException ignored) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Photograph cleanup deferred; retrying on next scheduled pass");
                } finally { cleanupRunning.set(false); }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { cleanupRunning.set(false); }
    }
    @jakarta.annotation.PreDestroy
    public void stopCleanup() { cleanupExecutor.shutdownNow(); }

    public void cleanup() {
        if(!settings.enabled) return;
        // A five-minute grace protects against timed-out remote requests finishing after a delete.
        // One hour is a conservative crash boundary for unfinished upload reservations.
        List<Row> candidates=db.query("select * from property_photos where (state='DELETE_PENDING' and updated_at<?) or (state='UPLOADING' and updated_at<?) order by updated_at limit 20",
                (r,n)->row(r),Timestamp.from(Instant.now().minusSeconds(300)),Timestamp.from(Instant.now().minusSeconds(3600)));
        for(Row candidate:candidates) {
            try {
                boolean claimed=tx(()->{
                    properties.findForUpdateById(candidate.property).orElseThrow();
                    return db.update("update property_photos set state='DELETE_PENDING',is_cover=false where id=? and (state='DELETE_PENDING' or (state='UPLOADING' and updated_at<?))",
                            candidate.id,Timestamp.from(Instant.now().minusSeconds(3600)))==1;
                });
                if(!claimed) continue;
                storage.delete(candidate.prefix);
                db.update("delete from property_photos where id=? and state='DELETE_PENDING'",candidate.id);
            } catch (RuntimeException ignored) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Photograph cleanup deferred; retrying on next scheduled pass");
            }
        }
    }
}
