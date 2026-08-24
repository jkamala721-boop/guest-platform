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

/** Applies narrow abuse limits before public requests reach controllers. */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern GUEST_TOKEN_PATH = Pattern.compile("^/api/public/guest/([^/]+)(.*)$");
    private final PublicRateLimiter rateLimiter;
    private final ApiErrorWriter apiErrorWriter;

    public PublicRateLimitFilter(PublicRateLimiter rateLimiter, ApiErrorWriter apiErrorWriter) {
        this.rateLimiter = rateLimiter;
        this.apiErrorWriter = apiErrorWriter;
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
        String ip = request.getRemoteAddr() == null || request.getRemoteAddr().isBlank() ? "unknown" : request.getRemoteAddr();
        if ("POST".equals(method) && "/api/auth/login".equals(path)) return Optional.of(new RequestLimit(Category.LOGIN, ip));
        if ("POST".equals(method) && "/api/auth/register".equals(path)) return Optional.of(new RequestLimit(Category.REGISTRATION, ip));
        if ("POST".equals(method) && "/api/webhooks/paystack".equals(path)) return Optional.of(new RequestLimit(Category.PAYSTACK_WEBHOOK, ip));
        Matcher guestPath = GUEST_TOKEN_PATH.matcher(path);
        if (!guestPath.matches()) return Optional.empty();
        String tokenKey = ip + ':' + PublicRateLimiter.hashIdentifier(guestPath.group(1));
        String suffix = guestPath.group(2);
        if ("GET".equals(method) && suffix.isEmpty()) return Optional.of(new RequestLimit(Category.GUEST_LINK, tokenKey));
        if ("POST".equals(method) && "/email-verification".equals(suffix)) return Optional.of(new RequestLimit(Category.OTP_REQUEST, tokenKey));
        if ("POST".equals(method) && "/email-verification/confirm".equals(suffix)) return Optional.of(new RequestLimit(Category.OTP_VERIFY, tokenKey));
        if ("POST".equals(method) && "/returning-guest".equals(suffix)) return Optional.of(new RequestLimit(Category.RETURNING_GUEST_LOOKUP, tokenKey));
        if ("POST".equals(method) && "/returning-guest/confirm".equals(suffix)) return Optional.of(new RequestLimit(Category.RETURNING_GUEST_VERIFY, tokenKey));
        if ("POST".equals(method) && "/payments".equals(suffix)) return Optional.of(new RequestLimit(Category.PAYMENT_INITIALIZATION, tokenKey));
        return Optional.empty();
    }

    private record RequestLimit(Category category, String key) { }
}
