package com.guest_platform.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.PropertyResponse;
import com.guest_platform.dto.PropertyUpsertRequest;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.PropertyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> create(Authentication authentication,
            @Valid @RequestBody PropertyUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(propertyService.create(CurrentHost.id(authentication), request));
    }

    @GetMapping
    public List<PropertyResponse> list(Authentication authentication) {
        return propertyService.list(CurrentHost.id(authentication));
    }

    @GetMapping("/{propertyId}")
    public PropertyResponse get(Authentication authentication, @PathVariable UUID propertyId) {
        return propertyService.get(CurrentHost.id(authentication), propertyId);
    }

    @PutMapping("/{propertyId}")
    public PropertyResponse update(Authentication authentication, @PathVariable UUID propertyId,
            @Valid @RequestBody PropertyUpsertRequest request) {
        return propertyService.update(CurrentHost.id(authentication), propertyId, request);
    }

    @DeleteMapping("/{propertyId}")
    public ResponseEntity<Void> deactivate(Authentication authentication, @PathVariable UUID propertyId) {
        propertyService.deactivate(CurrentHost.id(authentication), propertyId);
        return ResponseEntity.noContent().build();
    }
}
