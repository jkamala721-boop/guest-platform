package com.guest_platform.service.photo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SupabasePhotoStorageTest {
    final PhotoSettings settings=new PhotoSettings(true,"https://fixture.supabase.co","test-only-key","hostvero-property-photos");
    final HttpClient client=mock(HttpClient.class);
    final SupabasePhotoStorage storage=new SupabasePhotoStorage(settings,new ObjectMapper(),client);
    final String prefix=UUID.randomUUID()+"/"+UUID.randomUUID();
    @SuppressWarnings("unchecked")
    void response(int code,String body) throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    HttpRequest request() throws Exception {
        var capture=ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(capture.capture(),any(HttpResponse.BodyHandler.class));return capture.getValue();
    }
    String body(HttpRequest request) throws Exception {
        var result=new CompletableFuture<String>(); var out=new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);out.writeBytes(bytes); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(out.toString(StandardCharsets.UTF_8)); }
        });return result.get(2,TimeUnit.SECONDS);
    }
    @Test void uploadUsesPrivateImmutableObjectAndNeverTransforms() throws Exception {
        response(200,"{}"); storage.upload(prefix+"/display.jpg",new byte[]{1,2});
        var request=request();assertEquals("POST",request.method());
        assertEquals("/storage/v1/object/hostvero-property-photos/"+prefix+"/display.jpg",request.uri().getPath());
        assertEquals("false",request.headers().firstValue("x-upsert").orElseThrow());
        assertEquals("private, no-store, max-age=0",request.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(request.headers().firstValue("Authorization").isPresent());
    }
    @Test void deletionUsesSupabaseBulkApiAndIsRetryable() throws Exception {
        response(200,"[]");storage.delete(prefix);
        var request=request();assertEquals("DELETE",request.method());
        assertEquals("/storage/v1/object/hostvero-property-photos",request.uri().getPath());
        var parsed=new ObjectMapper().readTree(body(request));
        assertEquals(prefix+"/thumb.jpg",parsed.path("prefixes").get(0).asText());
        assertEquals(prefix+"/display.jpg",parsed.path("prefixes").get(1).asText());
    }
    @Test void signsVariantsInOneRequestAndRejectsForeignProviderUrls() throws Exception {
        String object=prefix+"/thumb.jpg";
        response(200,"[{\"path\":\""+object+"\",\"signedURL\":\"/object/sign/hostvero-property-photos/"+object+"?token=fixture\"}]");
        var signed=storage.sign(List.of(object),120);
        assertEquals("https://fixture.supabase.co/storage/v1/object/sign/hostvero-property-photos/"+object+"?token=fixture",signed.get(object));
        assertEquals(120,new ObjectMapper().readTree(body(request())).get("expiresIn").asInt());
        reset(client);response(200,"[{\"path\":\""+object+"\",\"signedURL\":\"https://foreign.example/x\"}]");
        assertThrows(PhotoUnavailableException.class,()->storage.sign(List.of(object),120));
    }
    @Test void failureMessagesDoNotContainProviderBodiesOrCredential() throws Exception {
        response(403,"sensitive provider details");
        var error=assertThrows(PhotoUnavailableException.class,()->storage.upload(prefix+"/thumb.jpg",new byte[]{1}));
        assertFalse(error.toString().contains("sensitive"));assertFalse(error.toString().contains(settings.key));
        assertThrows(PhotoUnavailableException.class,()->storage.delete(prefix));
    }
    @Test void configurationRejectsNonOriginUrlsAndCspUsesOnlyConfiguredOrigin() {
        assertThrows(IllegalArgumentException.class,()->new PhotoSettings(true,"https://fixture.supabase.co/path","test","hostvero-property-photos"));
        assertThrows(IllegalArgumentException.class,()->new PhotoSettings(true,"","","hostvero-property-photos"));
        assertEquals("img-src 'self' data: https://fixture.supabase.co; connect-src 'self';",settings.imagePolicy("img-src 'self' data:; connect-src 'self';"));
    }
}
