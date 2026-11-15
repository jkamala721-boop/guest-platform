package com.guest_platform.service.photo;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Backend-only adapter. Never logs provider bodies or credentials; never creates buckets. */
@Component
public class SupabasePhotoStorage implements PhotoStorage {
    private final PhotoSettings settings;
    private final ObjectMapper json;
    private final HttpClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public SupabasePhotoStorage(PhotoSettings settings, ObjectMapper json) {
        this(settings,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    SupabasePhotoStorage(PhotoSettings settings, ObjectMapper json, HttpClient client) {
        this.settings=settings; this.json=json; this.client=client;
    }
    private String path(String path) {
        if (!path.matches("[a-f0-9-]{36}/[a-f0-9-]{36}/(thumb|display)\\.jpg"))
            throw new IllegalArgumentException("Invalid photograph object path");
        return settings.bucket + "/" + path;
    }
    private HttpResponse<String> send(String route, String method, byte[] body, String type) {
        settings.requireEnabled();
        try {
            var request = HttpRequest.newBuilder(URI.create(settings.origin + "/storage/v1/" + route))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + settings.key)
                    .header("apikey", settings.key).header("Content-Type", type)
                    .header("x-upsert", "false")
                    .header("Cache-Control", "private, no-store, max-age=0")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new PhotoUnavailableException(); }
          catch (Exception e) { throw new PhotoUnavailableException(); }
    }
    @Override public void upload(String path, byte[] jpeg) {
        var response = send("object/" + path(path), "POST", jpeg, "image/jpeg");
        if (response.statusCode() / 100 != 2) throw new PhotoUnavailableException();
    }
    @Override public void delete(String prefix) {
        path(prefix + "/thumb.jpg");
        var response = send("object/" + settings.bucket, "DELETE",
                json.writeValueAsBytes(Map.of("prefixes", java.util.List.of(prefix + "/thumb.jpg", prefix + "/display.jpg"))), "application/json");
        if (response.statusCode() / 100 != 2) throw new PhotoUnavailableException();
    }
    @Override public Map<String,String> sign(java.util.List<String> paths, int seconds) {
        paths.forEach(this::path);
        var response = send("object/sign/" + settings.bucket, "POST",
                json.writeValueAsBytes(Map.of("expiresIn", seconds, "paths", paths)), "application/json");
        if (response.statusCode() / 100 != 2) throw new PhotoUnavailableException();
        try {
            Map<String,String> result = new java.util.HashMap<>();
            for (var entry : json.readTree(response.body())) {
                String object = entry.path("path").asText(), signed = entry.path("signedURL").asText();
                if (!paths.contains(object) || !signed.startsWith("/object/sign/" + path(object) + "?token=")
                        || signed.contains("#")) throw new PhotoUnavailableException();
                result.put(object, settings.origin + "/storage/v1" + signed);
            }
            if (result.size() != paths.size()) throw new PhotoUnavailableException();
            return result;
        } catch (Exception e) { throw new PhotoUnavailableException(); }
    }
}
