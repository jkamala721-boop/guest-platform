package com.guest_platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.guest_platform.config.PublicRateLimitProperties;
import com.guest_platform.config.PublicRateLimitProperties.Limit;

/** A bounded, sliding-window limiter for public routes. */
@Component
public class PublicRateLimiter {

    public enum Category {
        LOGIN, REGISTRATION, GUEST_LINK, OTP_REQUEST, OTP_VERIFY, PAYMENT_INITIALIZATION, PAYSTACK_WEBHOOK
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision permitted() { return new Decision(true, 0); }
        static Decision denied(long retryAfterSeconds) { return new Decision(false, retryAfterSeconds); }
    }

    private final PublicRateLimitProperties properties;
    private final Clock clock;
    private final Map<Category, Policy> policies;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Duration longestWindow;

    @Autowired
    public PublicRateLimiter(PublicRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PublicRateLimiter(PublicRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.policies = policies(properties);
        this.longestWindow = policies.values().stream().map(Policy::window)
                .max(Duration::compareTo).orElse(Duration.ofMinutes(1));
    }

    public Decision check(Category category, String clientKey) {
        if (!properties.isEnabled()) {
            return Decision.permitted();
        }
        Policy policy = policies.get(category);
        Instant now = clock.instant();
        String key = category.name() + ':' + clientKey;
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= positive(properties.getMaxBuckets(), 10_000)) {
                removeExpiredBuckets(now);
                if (buckets.size() >= positive(properties.getMaxBuckets(), 10_000)) {
                    return Decision.denied(1);
                }
            }
            bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        }
        synchronized (bucket) {
            Instant cutoff = now.minus(policy.window());
            while (!bucket.requests.isEmpty() && !bucket.requests.peekFirst().isAfter(cutoff)) {
                bucket.requests.removeFirst();
            }
            bucket.lastSeen = now;
            if (bucket.requests.size() >= policy.maxRequests()) {
                Instant retryAt = bucket.requests.peekFirst().plus(policy.window());
                long retrySeconds = Math.max(1, Duration.between(now, retryAt).toSeconds() + 1);
                return Decision.denied(retrySeconds);
            }
            bucket.requests.addLast(now);
            return Decision.permitted();
        }
    }

    public static String hashIdentifier(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Map<Category, Policy> policies(PublicRateLimitProperties source) {
        Map<Category, Policy> result = new EnumMap<>(Category.class);
        result.put(Category.LOGIN, policy(source.getLogin(), 10, 900));
        result.put(Category.REGISTRATION, policy(source.getRegistration(), 5, 3600));
        result.put(Category.GUEST_LINK, policy(source.getGuestLink(), 120, 300));
        result.put(Category.OTP_REQUEST, policy(source.getOtpRequest(), 5, 900));
        result.put(Category.OTP_VERIFY, policy(source.getOtpVerify(), 10, 900));
        result.put(Category.PAYMENT_INITIALIZATION, policy(source.getPaymentInitialization(), 10, 900));
        result.put(Category.PAYSTACK_WEBHOOK, policy(source.getPaystackWebhook(), 240, 60));
        return result;
    }

    private Policy policy(Limit limit, int defaultRequests, long defaultWindowSeconds) {
        return new Policy(positive(limit == null ? 0 : limit.getMaxRequests(), defaultRequests),
                Duration.ofSeconds(positive(limit == null ? 0 : limit.getWindowSeconds(), defaultWindowSeconds)));
    }

    private int positive(int value, int fallback) { return value > 0 ? value : fallback; }
    private long positive(long value, long fallback) { return value > 0 ? value : fallback; }

    private void removeExpiredBuckets(Instant now) {
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                return bucket.lastSeen != null && !bucket.lastSeen.plus(longestWindow).isAfter(now);
            }
        });
    }

    private record Policy(int maxRequests, Duration window) { }

    private static final class Bucket {
        private final Deque<Instant> requests = new ArrayDeque<>();
        private Instant lastSeen;
    }
}
