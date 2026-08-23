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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.GuestCreateRequest;
import com.guest_platform.dto.GuestListResponse;
import com.guest_platform.dto.GuestRemovalResponse;
import com.guest_platform.dto.GuestResponse;
import com.guest_platform.dto.GuestUpdateRequest;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.GuestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @PostMapping
    public ResponseEntity<GuestResponse> create(Authentication authentication,
            @Valid @RequestBody GuestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guestService.create(CurrentHost.id(authentication), request));
    }

    @GetMapping
    public List<GuestListResponse> list(Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String idType) {
        return guestService.list(CurrentHost.id(authentication), query, nationality, idType);
    }

    @GetMapping("/{guestId}")
    public GuestResponse get(Authentication authentication, @PathVariable UUID guestId) {
        return guestService.get(CurrentHost.id(authentication), guestId);
    }

    @PutMapping("/{guestId}")
    public GuestResponse update(Authentication authentication, @PathVariable UUID guestId,
            @Valid @RequestBody GuestUpdateRequest request) {
        return guestService.update(CurrentHost.id(authentication), guestId, request);
    }

    @DeleteMapping("/{guestId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID guestId) {
        guestService.delete(CurrentHost.id(authentication), guestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{guestId}/remove")
    public GuestRemovalResponse remove(Authentication authentication, @PathVariable UUID guestId) {
        return guestService.remove(CurrentHost.id(authentication), guestId);
    }
}
