package com.guest_platform.controller;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.guest_platform.service.PropertyOfferService;
import com.guest_platform.service.PropertyOfferService.*;
import com.guest_platform.security.CurrentHost;

@RestController
public class PropertyOfferController {
    private final PropertyOfferService offers;
    public PropertyOfferController(PropertyOfferService offers) { this.offers = offers; }
    @PostMapping("/api/properties/{propertyId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedOffer create(Authentication auth, @PathVariable UUID propertyId, @Valid @RequestBody CreateRequest request) {
        return offers.create(CurrentHost.id(auth), propertyId, request);
    }
    @GetMapping("/api/properties/{propertyId}/offers")
    public List<OfferView> list(Authentication auth, @PathVariable UUID propertyId) { return offers.list(CurrentHost.id(auth), propertyId); }
    @PostMapping("/api/offers/{offerId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth, @PathVariable UUID offerId) { offers.revoke(CurrentHost.id(auth), offerId); }
    @GetMapping("/api/public/offers/{token}")
    public OfferView preview(@PathVariable String token) { return offers.preview(token); }
    @PostMapping("/api/public/offers/{token}/quote")
    public Quote quote(@PathVariable String token, @Valid @RequestBody Dates request) { return offers.quote(token, request); }
    @PostMapping("/api/public/offers/{token}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation reserve(@PathVariable String token, @Valid @RequestBody Dates request) { return offers.reserve(token, request); }
}
