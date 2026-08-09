package com.guest_platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public String health() {
        return "{\"status\":\"ok\",\"message\":\"Guest Platform API is running\"}";
    }
}