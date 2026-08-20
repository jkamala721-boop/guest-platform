package com.guest_platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.entity.Host;
import com.guest_platform.entity.HostSession;
import com.guest_platform.repository.HostSessionRepository;

@Service
public class HostSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HostSessionRepository hostSessionRepository;
    private final Duration sessionTtl;

    public HostSessionService(HostSessionRepository hostSessionRepository,
            @Value("${app.auth.session-ttl-hours}") long sessionTtlHours) {
        this.hostSessionRepository = hostSessionRepository;
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Transactional
    public SessionToken create(Host host) {
        Instant expiresAt = Instant.now().plus(sessionTtl);
        String rawToken = newToken();
        hostSessionRepository.save(new HostSession(host, hash(rawToken), expiresAt));
        return new SessionToken(rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findAuthenticatedHostId(String rawToken) {
        return hostSessionRepository.findByTokenHashAndExpiresAtAfter(hash(rawToken), Instant.now())
                .filter(session -> session.getHost().isActive())
                .map(session -> session.getHost().getId());
    }

    @Transactional
    public void revoke(String rawToken) {
        hostSessionRepository.deleteByTokenHash(hash(rawToken));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SessionToken(String value, Instant expiresAt) {
    }
}
