package com.guest_platform.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guest_platform.service.HostSessionService;

public class BearerSessionAuthenticationFilter extends OncePerRequestFilter {

    private final HostSessionService hostSessionService;

    public BearerSessionAuthenticationFilter(HostSessionService hostSessionService) {
        this.hostSessionService = hostSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ") || authorization.length() == 7) {
            unauthorized(response);
            return;
        }

        String token = authorization.substring(7);
        hostSessionService.findAuthenticatedHostId(token).ifPresentOrElse(hostId -> {
            HostPrincipal principal = new HostPrincipal(hostId);
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }, () -> unauthorized(response));

        if (!response.isCommitted()) {
            filterChain.doFilter(request, response);
        }
    }

    private void unauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired access token\"}");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write authentication response", exception);
        }
    }
}
