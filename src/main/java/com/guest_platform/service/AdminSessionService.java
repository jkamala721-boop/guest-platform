package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.entity.AdminSession;
import com.guest_platform.entity.AdminUser;
import com.guest_platform.repository.AdminSessionRepository;

@Service
public class AdminSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AdminSessionRepository repository;
    private final Duration duration;
    private final boolean secure;
    private final String cookieName;

    public AdminSessionService(AdminSessionRepository repository,
            @Value("${app.admin.session-duration-hours:12}") long durationHours,
            @Value("${app.admin.cookie-secure:${app.auth.cookie-secure:false}}") boolean secure,
            @Value("${app.admin.cookie-name:HOSTVERO_ADMIN_SESSION}") String cookieName) {
        this.repository = repository;
        this.duration = Duration.ofHours(Math.max(1, durationHours));
        this.secure = secure;
        this.cookieName = cookieName;
    }

    @Transactional
    public SessionToken create(AdminUser admin) {
        Instant now = Instant.now();
        repository.revokeActiveByAdminId(admin.getId(), now);
        String token = newToken();
        Instant expiresAt = now.plus(duration);
        repository.save(new AdminSession(admin, hash(token), expiresAt));
        return new SessionToken(token, expiresAt);
    }

    @Transactional
    public Lookup lookup(String rawToken) {
        Optional<AdminSession> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) return Lookup.invalid();
        AdminSession session = found.get();
        Instant now = Instant.now();
        if (!session.isUsableAt(now)) return Lookup.expiredSession();
        session.touch(now);
        return Lookup.authenticated(session.getAdminUser());
    }

    @Transactional
    public Optional<AdminUser> revoke(String rawToken) {
        return repository.findByTokenHash(hash(rawToken)).map(session -> {
            session.revoke(Instant.now());
            return session.getAdminUser();
        });
    }

    public ResponseCookie sessionCookie(SessionToken token) {
        return ResponseCookie.from(cookieName, token.value()).httpOnly(true).secure(secure).sameSite("Strict")
                .path("/").maxAge(duration).build();
    }
    public ResponseCookie clearCookie() {
        return ResponseCookie.from(cookieName, "").httpOnly(true).secure(secure).sameSite("Strict")
                .path("/").maxAge(Duration.ZERO).build();
    }
    public String cookieName() { return cookieName; }

    private String newToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
    public record SessionToken(String value, Instant expiresAt) {}
    public record Lookup(AdminUser admin, boolean expired) {
        static Lookup authenticated(AdminUser admin) { return new Lookup(admin, false); }
        static Lookup invalid() { return new Lookup(null, false); }
        static Lookup expiredSession() { return new Lookup(null, true); }
        public boolean authenticated() { return admin != null; }
    }
}
