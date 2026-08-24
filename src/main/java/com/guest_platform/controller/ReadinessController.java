package com.guest_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.service.ReadinessService;

/** Host-authenticated readiness signal; public health remains intentionally lightweight. */
@RestController
@RequestMapping("/api/internal")
public class ReadinessController {

    private final ReadinessService readinessService;

    public ReadinessController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping(value = "/readiness", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> readiness() {
        return readinessService.isReady() ? ResponseEntity.ok("UP")
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("DOWN");
    }
}
