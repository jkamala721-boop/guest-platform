package com.guest_platform.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guest_platform.service.AdminSessionService;

public class AdminSessionAuthenticationFilter extends OncePerRequestFilter {
    private final AdminSessionService sessions;
    private final ApiErrorWriter errors;

    public AdminSessionAuthenticationFilter(AdminSessionService sessions, ApiErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/admin/auth/login".equals(request.getRequestURI())
                || "/api/admin/auth/logout".equals(request.getRequestURI());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String token = token(request);
        if (token == null) { chain.doFilter(request, response); return; }
        AdminSessionService.Lookup lookup = sessions.lookup(token);
        if (!lookup.authenticated()) {
            errors.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    lookup.expired() ? "ADMIN_SESSION_EXPIRED" : "ADMIN_AUTH_REQUIRED",
                    lookup.expired() ? "Your admin session has expired. Please sign in again."
                            : "Admin authentication is required.", false);
            return;
        }
        var principal = new AdminPrincipal(lookup.admin().getId(), lookup.admin().getRole());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + lookup.admin().getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (sessions.cookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) return cookie.getValue();
        }
        return null;
    }
}
