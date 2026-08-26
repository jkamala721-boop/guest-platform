package com.guest_platform.security;

import java.util.UUID;
import com.guest_platform.entity.AdminRole;

public record AdminPrincipal(UUID id, AdminRole role) {}

