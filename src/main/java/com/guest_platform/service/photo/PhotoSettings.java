package com.guest_platform.service.photo;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhotoSettings {
    public final boolean enabled;
    public final String origin, key, bucket;
    public PhotoSettings(@Value("${app.photos.enabled:false}") boolean enabled,
            @Value("${app.photos.supabase-url:}") String origin,
            @Value("${app.photos.service-role-key:}") String key,
            @Value("${app.photos.bucket:hostvero-property-photos}") String bucket) {
        this.enabled = enabled;
        this.origin = origin.replaceAll("/+$", ""); this.key = key; this.bucket = bucket;
        if (!this.origin.isBlank()) {
            URI uri = URI.create(this.origin);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()))
                throw new IllegalArgumentException("Storage URL must be an HTTPS origin");
        }
        if (enabled && (this.origin.isBlank() || key.isBlank()))
            throw new IllegalArgumentException("Enabled photographs require backend Storage configuration");
        if (!"hostvero-property-photos".equals(bucket))
            throw new IllegalArgumentException("Unexpected property photograph bucket");
    }
    public void requireEnabled() { if (!enabled) throw new PhotoUnavailableException(); }
    public String imagePolicy(String policy) {
        return enabled ? policy.replace("img-src 'self' data:;", "img-src 'self' data: " + origin + ";") : policy;
    }
}
