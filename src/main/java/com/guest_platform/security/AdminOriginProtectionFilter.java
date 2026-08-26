package com.guest_platform.security;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guest_platform.service.AdminSessionService;

public class AdminOriginProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final AdminSessionService sessions;
    private final String allowedOrigin;
    private final ApiErrorWriter errors;

    public AdminOriginProtectionFilter(AdminSessionService sessions, String allowedOrigin, ApiErrorWriter errors) {
        this.sessions = sessions;
        this.allowedOrigin = normalize(allowedOrigin);
        this.errors = errors;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!SAFE.contains(request.getMethod()) && hasAdminCookie(request) && !validOrigin(request)) {
            errors.write(response, HttpServletResponse.SC_FORBIDDEN, "ADMIN_FORBIDDEN",
                    "You do not have permission to perform this admin action.", false);
            return;
        }
        chain.doFilter(request, response);
    }
    private boolean hasAdminCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        return java.util.Arrays.stream(request.getCookies())
                .anyMatch(cookie -> sessions.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank());
    }
    private boolean validOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) return allowedOrigin.equals(normalize(origin));
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null || referer.isBlank()) return false;
        try {
            URI uri = URI.create(referer);
            return allowedOrigin.equals(uri.getScheme() + "://" + uri.getAuthority());
        } catch (IllegalArgumentException exception) { return false; }
    }
    private String normalize(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
