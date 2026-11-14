package com.guest_platform.security;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guest_platform.security.PublicRateLimiter.Category;
import com.guest_platform.repository.GuestLinkRepository;

/** Applies narrow abuse limits before public requests reach controllers. */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern GUEST_TOKEN_PATH = Pattern.compile("^/api/public/guest/([^/]+)(.*)$");
    private static final Pattern EXTENSION_PAYMENT_PATH = Pattern.compile("^/extensions/[^/]+/payments$");
    private final PublicRateLimiter rateLimiter;
    private final ApiErrorWriter apiErrorWriter;
    private final GuestLinkRepository guestLinkRepository;

    public PublicRateLimitFilter(PublicRateLimiter rateLimiter, ApiErrorWriter apiErrorWriter,
            GuestLinkRepository guestLinkRepository) {
        this.rateLimiter = rateLimiter;
        this.apiErrorWriter = apiErrorWriter;
        this.guestLinkRepository = guestLinkRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Optional<RequestLimit> limit = limitFor(request);
        if (limit.isPresent()) {
            RequestLimit requestLimit = limit.get();
            PublicRateLimiter.Decision decision = rateLimiter.check(requestLimit.category(), requestLimit.key());
            if (!decision.allowed()) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
                apiErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED",
                        "Too many attempts. Please wait and try again.", true);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Optional<RequestLimit> limitFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher guestPath = GUEST_TOKEN_PATH.matcher(path);
        if (!guestPath.matches()) return Optional.empty();
        String token = guestPath.group(1);
        String suffix = guestPath.group(2);
        String tokenKey = PublicRateLimiter.hashIdentifier("hostvero:guest-link:" + token);
        Optional<RequestLimit> requestLimit = Optional.empty();
        if ("GET".equals(method) && (suffix.isEmpty() || "/availability".equals(suffix)
                || "/receipt".equals(suffix) || "/receipt/document".equals(suffix))) {
            requestLimit = Optional.of(new RequestLimit(Category.GUEST_LINK, tokenKey));
        } else if ("PUT".equals(method) && "/registration".equals(suffix)) {
            requestLimit = Optional.of(new RequestLimit(Category.GUEST_REGISTRATION, tokenKey));
        } else if ("POST".equals(method) && "/email-verification".equals(suffix)) {
            requestLimit = Optional.of(new RequestLimit(Category.OTP_REQUEST, tokenKey));
        } else if ("POST".equals(method) && "/email-verification/confirm".equals(suffix)) {
            requestLimit = Optional.of(new RequestLimit(Category.OTP_VERIFY, tokenKey));
        } else if ("POST".equals(method) && "/returning-guest".equals(suffix)) {
            requestLimit = Optional.of(new RequestLimit(Category.RETURNING_GUEST_LOOKUP, tokenKey));
        } else if ("POST".equals(method) && "/returning-guest/confirm".equals(suffix)) {
            requestLimit = Optional.of(new RequestLimit(Category.RETURNING_GUEST_VERIFY, tokenKey));
        } else if ("POST".equals(method) && "/payments".equals(suffix)) {
            requestLimit = Optional.of(new RequestLimit(Category.PAYMENT_INITIALIZATION, tokenKey));
        } else if ("POST".equals(method) && ("/extend".equals(suffix) || "/book-again".equals(suffix))) {
            requestLimit = Optional.of(new RequestLimit(Category.GUEST_BOOKING_CHANGE, tokenKey));
        } else if ("POST".equals(method) && EXTENSION_PAYMENT_PATH.matcher(suffix).matches()) {
            requestLimit = Optional.of(new RequestLimit(Category.PAYMENT_INITIALIZATION, tokenKey));
        }
        if (requestLimit.isEmpty()) return requestLimit;

        // Invalid, attacker-chosen token strings must not allocate entries in the bounded limiter.
        String storedTokenHash = PublicRateLimiter.hashIdentifier(token);
        return guestLinkRepository.existsByTokenHash(storedTokenHash) ? requestLimit : Optional.empty();
    }

    private record RequestLimit(Category category, String key) { }
}
