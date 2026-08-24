package com.guest_platform.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.AuthResponse;
import com.guest_platform.dto.HostResponse;
import com.guest_platform.dto.LoginRequest;
import com.guest_platform.dto.RegisterRequest;
import com.guest_platform.entity.Host;
import com.guest_platform.exception.ConflictException;
import com.guest_platform.exception.InvalidCredentialsException;
import com.guest_platform.repository.HostRepository;

@Service
public class AuthenticationService {

    private final HostRepository hostRepository;
    private final HostSessionService hostSessionService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(HostRepository hostRepository, HostSessionService hostSessionService,
            PasswordEncoder passwordEncoder) {
        this.hostRepository = hostRepository;
        this.hostSessionService = hostSessionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthenticatedSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (hostRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for this email address");
        }

        Host host = hostRepository.save(new Host(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), normalizeOptional(request.phone())));
        return createAuthenticatedResponse(host);
    }

    @Transactional
    public AuthenticatedSession login(LoginRequest request) {
        Host host = hostRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(Host::isActive)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return createAuthenticatedResponse(host);
    }

    private AuthenticatedSession createAuthenticatedResponse(Host host) {
        HostSessionService.SessionToken token = hostSessionService.create(host);
        return new AuthenticatedSession(token, new AuthResponse(token.expiresAt(), HostResponse.from(host)));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AuthenticatedSession(HostSessionService.SessionToken sessionToken, AuthResponse response) {
    }
}
