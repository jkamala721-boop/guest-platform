package com.guest_platform.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guest_platform.service.HostSessionService;

public class BearerSessionAuthenticationFilter extends OncePerRequestFilter {

    private final HostSessionService hostSessionService;
    private final ApiErrorWriter apiErrorWriter;

    public BearerSessionAuthenticationFilter(HostSessionService hostSessionService, ApiErrorWriter apiErrorWriter) {
        this.hostSessionService = hostSessionService;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String cookieToken = tokenFromCookie(request);
        if (cookieToken != null) {
            authenticate(cookieToken, true, response, filterChain, request);
            return;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ") || authorization.length() == 7) {
            unauthorized(response, "AUTH_REQUIRED", "Please sign in to continue.");
            return;
        }

        authenticate(authorization.substring(7), false, response, filterChain, request);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.startsWith("/api/public/")
                || path.startsWith("/api/webhooks/")
                || path.equals("/api/health")
                || path.startsWith("/js/")
                || path.startsWith("/css/")
                || path.startsWith("/images/");
    }

    private void authenticate(String token, boolean fromCookie, HttpServletResponse response, FilterChain filterChain,
            HttpServletRequest request) throws IOException, ServletException {
        java.util.Optional<java.util.UUID> hostId = hostSessionService.findAuthenticatedHostId(token);
        if (hostId.isPresent()) {
            HostPrincipal principal = new HostPrincipal(hostId.get());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            unauthorized(response, fromCookie ? "AUTH_SESSION_EXPIRED" : "AUTH_REQUIRED",
                    fromCookie ? "Your session has expired. Please sign in again." : "Please sign in to continue.");
        }

        if (!response.isCommitted()) {
            filterChain.doFilter(request, response);
        }
    }

    private String tokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (hostSessionService.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }

    private void unauthorized(HttpServletResponse response, String code, String message) {
        try {
            apiErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, code, message, false);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write authentication response", exception);
        }
    }
}
