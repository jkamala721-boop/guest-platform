package com.guest_platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.guest_platform.service.photo.*;
import tools.jackson.databind.JsonNode;

/** Actual PostgreSQL migrations, HTTP authorization, metadata transactions; no live Storage. */
@SpringBootTest(properties={"app.photos.enabled=true", "app.photos.supabase-url=https://photos-test.supabase.co",
        "app.photos.service-role-key=test-only-placeholder", "app.photos.cleanup-delay-ms=3600000"})
class PropertyPhotoIntegrationTest extends PropertyOfferPostgresIntegrationTest {
    @MockitoBean PhotoStorage storage;
    @Autowired PropertyPhotoService photos;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.guest_platform.repository.PropertyRepository propertyRepository;
    @Autowired com.guest_platform.service.HostOperationalAccessService operationalAccess;
    @Autowired PhotoProcessor processor;
    @Autowired PhotoSettings settings;
    @BeforeEach void setupStorage() {
        when(storage.sign(anyList(),anyInt())).thenAnswer(invocation->{
            Map<String,String> result=new HashMap<>();
            for(String p : invocation.<List<String>>getArgument(0)) result.put(p,"https://photos-test.supabase.co/"+p+"?test=temporary");
            return result;
        });
    }
    MockMultipartFile image() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2100,1050,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("file","original.png","image/png",out.toByteArray());
    }
    UUID upload(Fixture f) throws Exception {
        return UUID.fromString(body(mvc.perform(multipart("/api/properties/{id}/photos",f.property()).file(image())
                .header("Authorization","Bearer "+f.auth())).andExpect(status().isCreated()).andReturn()).get("id").asText());
    }
    JsonNode gallery(Fixture f) throws Exception {
        return body(mvc.perform(get("/api/properties/{id}/photos",f.property()).header("Authorization","Bearer "+f.auth()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn()).get("photos");
    }
    @Test void uploadOptimizesAndRequiresOwnership() throws Exception {
        Fixture owner=fixture(), stranger=fixture();
        mvc.perform(multipart("/api/properties/{id}/photos",owner.property()).file(image())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/properties/{id}/photos",owner.property()).file(image()).header("Authorization","Bearer "+stranger.auth()))
                .andExpect(status().isNotFound());
        UUID id=upload(owner);
        var g=gallery(owner); assertEquals(1,g.size()); assertTrue(g.get(0).get("cover").asBoolean());
        assertEquals(480,g.get(0).get("thumbnailWidth").asInt()); assertEquals(1920,g.get(0).get("displayWidth").asInt());
        verify(storage,times(2)).upload(contains(id.toString()),argThat(b->b[0]==(byte)255 && b[1]==(byte)216));
        mvc.perform(get("/api/properties/{id}/photos",owner.property()).header("Authorization","Bearer "+stranger.auth())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/properties/{id}/photos/{photo}",owner.property(),id).header("Authorization","Bearer "+stranger.auth())).andExpect(status().isNotFound());
        mvc.perform(put("/api/properties/{id}/photos/cover",owner.property()).header("Authorization","Bearer "+stranger.auth())
                .contentType("application/json").content(json.writeValueAsString(Map.of("photoId",id)))).andExpect(status().isNotFound());
        mvc.perform(put("/api/properties/{id}/photos/order",owner.property()).header("Authorization","Bearer "+stranger.auth())
                .contentType("application/json").content(json.writeValueAsString(Map.of("photoIds",List.of(id))))).andExpect(status().isNotFound());
    }
    @Test void validatesBytesTypeAndDimensionsBeforeStorage() throws Exception {
        Fixture f=fixture();
        for(var file:List.of(new MockMultipartFile("file","fake.jpg","image/jpeg","not a photograph".getBytes()),
                new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes()),
                new MockMultipartFile("file","huge.jpg","image/jpeg",new byte[10*1024*1024+1]),
                new MockMultipartFile("file","wrong.jpg","image/jpeg",image().getBytes()))) {
            mvc.perform(multipart("/api/properties/{id}/photos",f.property()).file(file).header("Authorization","Bearer "+f.auth())).andExpect(status().isBadRequest());
        }
        assertEquals(0,count("select count(*) from property_photos where property_id=?",f.property()));
        verify(storage,never()).upload(anyString(),any());
    }
    @Test void orderingCoverAndDeletionAreAtomicAndRetryable() throws Exception {
        Fixture f=fixture(); UUID a=upload(f),b=upload(f);
        mvc.perform(put("/api/properties/{id}/photos/order",f.property()).header("Authorization","Bearer "+f.auth()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("photoIds",List.of(b,a))))).andExpect(status().isNoContent());
        assertEquals(b.toString(),gallery(f).get(0).get("id").asText());
        mvc.perform(put("/api/properties/{id}/photos/order",f.property()).header("Authorization","Bearer "+f.auth()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("photoIds",List.of(a,a))))).andExpect(status().isConflict());
        mvc.perform(put("/api/properties/{id}/photos/cover",f.property()).header("Authorization","Bearer "+f.auth()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("photoId",b)))).andExpect(status().isNoContent());
        assertTrue(gallery(f).get(0).get("cover").asBoolean());
        mvc.perform(delete("/api/properties/{id}/photos/{photo}",f.property(),b).header("Authorization","Bearer "+f.auth())).andExpect(status().isNoContent());
        assertEquals(1,gallery(f).size()); assertTrue(gallery(f).get(0).get("cover").asBoolean());
        doThrow(new PhotoUnavailableException()).when(storage).delete(contains(b.toString()));
        jdbc.update("update property_photos set updated_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(301)),b);
        photos.cleanup(); assertEquals(1,count("select count(*) from property_photos where id=? and state='DELETE_PENDING'",b));
        doNothing().when(storage).delete(anyString()); photos.cleanup();
        assertEquals(0,count("select count(*) from property_photos where id=?",b));
        assertEquals(1,gallery(f).size());
    }
    @Test void uploadFailureNeverPublishesBrokenPhotoAndStaleRowsRecover() throws Exception {
        Fixture f=fixture();
        doAnswer(invocation->{ if(invocation.<String>getArgument(0).endsWith("display.jpg")) throw new PhotoUnavailableException(); return null; })
                .when(storage).upload(anyString(),any());
        mvc.perform(multipart("/api/properties/{id}/photos",f.property()).file(image()).header("Authorization","Bearer "+f.auth())).andExpect(status().isServiceUnavailable());
        assertEquals(0,gallery(f).size());
        assertEquals(1,count("select count(*) from property_photos where property_id=? and state='DELETE_PENDING'",f.property()));
        jdbc.update("update property_photos set updated_at=? where property_id=?",Timestamp.from(Instant.now().minusSeconds(301)),f.property());
        photos.cleanup(); assertEquals(0,count("select count(*) from property_photos where property_id=?",f.property()));
        UUID stale=UUID.randomUUID();
        jdbc.update("insert into property_photos(id,property_id,object_prefix,state,sort_order,is_cover,created_at,updated_at) values (?,?,?,'UPLOADING',0,false,?,?)",
                stale,f.property(),f.property()+"/"+stale,Timestamp.from(Instant.now().minusSeconds(7200)),Timestamp.from(Instant.now().minusSeconds(7200)));
        photos.cleanup(); assertEquals(0,count("select count(*) from property_photos where id=?",stale));
    }
    @Test void enforcesLimitIncludingInProgressReservations() throws Exception {
        Fixture f=fixture();
        for(int i=0;i<15;i++) {
            UUID id=UUID.randomUUID();
            jdbc.update("insert into property_photos(id,property_id,object_prefix,state,sort_order,is_cover,created_at,updated_at) values (?,?,?,'UPLOADING',0,false,now(),now())",id,f.property(),f.property()+"/"+id);
        }
        mvc.perform(multipart("/api/properties/{id}/photos",f.property()).file(image()).header("Authorization","Bearer "+f.auth())).andExpect(status().isConflict());
        verify(storage,never()).upload(anyString(),any());
    }
    @Test void offerAndGuestLinksAuthorizeGalleryAndSigningFailuresAreSafe() throws Exception {
        Fixture f=fixture(); upload(f);
        mvc.perform(get("/api/public/offers/{token}/photos",f.offerToken())).andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(1));
        var reservation=reserve(f,future());
        mvc.perform(get("/api/public/guest/{token}/photos",token(reservation))).andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(1));
        when(storage.sign(anyList(),anyInt())).thenThrow(new PhotoUnavailableException());
        mvc.perform(get("/api/public/offers/{token}/photos",f.offerToken())).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/public/offers/{token}",f.offerToken())).andExpect(status().isOk());
        mvc.perform(get("/api/public/guest/{token}",token(reservation))).andExpect(status().isOk());
        expireHold(reservation);
        mvc.perform(get("/api/public/guest/{token}/photos",token(reservation))).andExpect(status().isNotFound());
        jdbc.update("update property_offers set revoked_at=now() where id=?",f.offerId());
        mvc.perform(get("/api/public/offers/{token}/photos",f.offerToken())).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/guest/invalid/photos")).andExpect(status().isNotFound());
    }
    @Test void privateTableAndSingleCoverConstraintAreApplied() {
        assertEquals(Boolean.TRUE,jdbc.queryForObject("select relrowsecurity from pg_class where oid='public.property_photos'::regclass",Boolean.class));
        assertEquals(Boolean.FALSE,jdbc.queryForObject("select has_table_privilege('anon','public.property_photos','SELECT,INSERT,UPDATE,DELETE')",Boolean.class));
        assertEquals(1,count("select count(*) from pg_indexes where indexname='property_photos_one_cover_idx'"));
    }

    @Test void concurrentInstancesCannotExceedFifteenSlots() throws Exception {
        Fixture f=fixture();
        for(int i=0;i<14;i++) {
            UUID id=UUID.randomUUID();
            jdbc.update("insert into property_photos(id,property_id,object_prefix,state,sort_order,is_cover,created_at,updated_at) values (?,?,?,'UPLOADING',0,false,now(),now())",id,f.property(),f.property()+"/"+id);
        }
        // Separate services have independent semaphores, as on two application instances.
        var other=new PropertyPhotoService(jdbc,transactionManager,propertyRepository,operationalAccess,storage,processor,settings);
        var start=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> first=()->{start.await();try { photos.upload(f.hostId(),f.property(),image());return true; } catch(com.guest_platform.exception.ConflictException e) {return false;} };
            java.util.concurrent.Callable<Boolean> second=()->{start.await();try { other.upload(f.hostId(),f.property(),image());return true; } catch(com.guest_platform.exception.ConflictException e) {return false;} };
            var a=executor.submit(first);var b=executor.submit(second);start.countDown();
            assertNotEquals(a.get(30,java.util.concurrent.TimeUnit.SECONDS),b.get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(15,count("select count(*) from property_photos where property_id=? and state in ('READY','UPLOADING')",f.property()));
        } finally {executor.shutdownNow();}
    }

    @Test void signedLifetimeIsCappedAndLegacyGuestLinksRemainScoped() throws Exception {
        Fixture f=fixture();upload(f);
        jdbc.update("update property_offers set expires_at=? where id=?",Timestamp.from(Instant.now().plusSeconds(65)),f.offerId());
        mvc.perform(get("/api/public/offers/{token}/photos",f.offerToken())).andExpect(status().isOk());
        verify(storage).sign(anyList(),intThat(ttl->ttl>0 && ttl<=45));
        // Remove offer policy to exercise an existing host-created booking's usable link.
        var reservation=reserve(f,future());
        jdbc.update("update bookings set offer_id=null,checkout_hold_expires_at=null,reservation_confirmed_at=null,payment_deadline_at=null where id=?",id(reservation));
        mvc.perform(get("/api/public/guest/{token}/photos",token(reservation))).andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(1));
        jdbc.update("update guest_links set expires_at=? where booking_id=?",Timestamp.from(Instant.now().minusSeconds(1)),id(reservation));
        mvc.perform(get("/api/public/guest/{token}/photos",token(reservation))).andExpect(status().isNotFound());
    }
}
