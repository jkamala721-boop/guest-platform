package com.guest_platform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guest_platform.dto.PublicGuestLinkResponse;
import com.guest_platform.service.GuestLinkService;

@RestController
@RequestMapping("/api/public/guest")
public class PublicGuestLinkController {

    private final GuestLinkService guestLinkService;

    public PublicGuestLinkController(GuestLinkService guestLinkService) {
        this.guestLinkService = guestLinkService;
    }

    @GetMapping("/{token}")
    public PublicGuestLinkResponse resolve(@PathVariable String token) {
        return guestLinkService.resolvePublic(token);
    }
}
