package com.guest_platform.controller;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.guest_platform.security.CurrentHost;
import com.guest_platform.service.*;
import com.guest_platform.service.photo.PropertyPhotoService;

@RestController
public class PropertyPhotoController {
    private final PropertyPhotoService photos;
    private final PropertyOfferService offers;
    private final GuestLinkService links;
    public PropertyPhotoController(PropertyPhotoService photos, PropertyOfferService offers, GuestLinkService links) {
        this.photos=photos; this.offers=offers; this.links=links;
    }
    public record Order(@NotNull @Size(max=15) List<@NotNull UUID> photoIds) { }
    public record Cover(@NotNull UUID photoId) { }
    private ResponseEntity<PropertyPhotoService.Gallery> response(PropertyPhotoService.Gallery gallery) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gallery);
    }
    @GetMapping("/api/properties/{id}/photos")
    public ResponseEntity<PropertyPhotoService.Gallery> list(Authentication auth,@PathVariable UUID id) {
        return response(photos.hostGallery(CurrentHost.id(auth),id));
    }
    @PostMapping(value="/api/properties/{id}/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,UUID> upload(Authentication auth,@PathVariable UUID id,@RequestPart("file") MultipartFile file) {
        return Map.of("id",photos.upload(CurrentHost.id(auth),id,file));
    }
    @PutMapping("/api/properties/{id}/photos/order") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void order(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Order request) {
        photos.reorder(CurrentHost.id(auth),id,request.photoIds());
    }
    @PutMapping("/api/properties/{id}/photos/cover") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cover(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Cover request) {
        photos.cover(CurrentHost.id(auth),id,request.photoId());
    }
    @DeleteMapping("/api/properties/{id}/photos/{photo}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,@PathVariable UUID id,@PathVariable UUID photo) {
        photos.delete(CurrentHost.id(auth),id,photo);
    }
    @GetMapping("/api/public/offers/{token}/photos")
    public ResponseEntity<PropertyPhotoService.Gallery> offer(@PathVariable String token) {
        var scope=offers.galleryAccess(token); return response(photos.gallery(scope.propertyId(),scope.expiresAt()));
    }
    @GetMapping("/api/public/guest/{token}/photos")
    public ResponseEntity<PropertyPhotoService.Gallery> guest(@PathVariable String token) {
        var scope=links.galleryAccess(token); return response(photos.gallery(scope.propertyId(),scope.expiresAt()));
    }
}
