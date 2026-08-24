package com.guest_platform.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guest_platform.service.HostSessionService;

/** Origin validation protects state-changing requests authenticated by an HttpOnly host-session cookie. */
@Component
public class CookieCsrfProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final HostSessionService hostSessionService;
    private final Set<String> allowedOrigins;
    private final ApiErrorWriter apiErrorWriter;

    public CookieCsrfProtectionFilter(HostSessionService hostSessionService,
            @Value("${app.security.cors.allowed-origins}") String origins, ApiErrorWriter apiErrorWriter) {
        this.hostSessionService = hostSessionService;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && hasSessionCookie(request) && !hasBearerAuthorization(request)
                && !isSameOrigin(request)) {
            apiErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "AUTH_FORBIDDEN",
                    "You do not have permission to perform this action.", false);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        return request.getCookies() != null && Arrays.stream(request.getCookies())
                .anyMatch(cookie -> hostSessionService.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank());
    }

    private boolean hasBearerAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private boolean isSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) return allowedOrigins.contains(origin);
        String referer = request.getHeader(HttpHeaders.REFERER);
        return referer != null && allowedOrigins.stream().anyMatch(referer::startsWith);
    }
}
