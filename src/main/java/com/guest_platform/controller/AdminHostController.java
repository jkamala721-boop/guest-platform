package com.guest_platform.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*;
import com.guest_platform.entity.*;
import com.guest_platform.service.AdminHostOperationsService;
import com.guest_platform.service.AdminHostNoteTimelineService;
import com.guest_platform.security.AdminPrincipal;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/hosts")
public class AdminHostController {
    private final AdminHostOperationsService service;
    private final AdminHostNoteTimelineService notes;
    public AdminHostController(AdminHostOperationsService service,AdminHostNoteTimelineService notes){this.service=service;this.notes=notes;}

    @GetMapping
    public AdminHostPageResponse list(@RequestParam(required=false) String q,
            @RequestParam(required=false) HostAccountStatus accountStatus,
            @RequestParam(required=false) HostVerificationStatus verificationStatus,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return service.list(q,accountStatus,verificationStatus,page,size);
    }

    @GetMapping("/{hostId}")
    public AdminHostDetailResponse detail(@PathVariable UUID hostId){return service.detail(hostId);}

    @PostMapping("/{hostId}/notes") public AdminHostNoteResponse createNote(@PathVariable UUID hostId,
            @Valid @RequestBody AdminHostNoteRequest request,Authentication authentication){
        return notes.create(((AdminPrincipal)authentication.getPrincipal()).id(),hostId,request.type(),request.content());}
    @GetMapping("/{hostId}/notes") public AdminHostNotePageResponse notes(@PathVariable UUID hostId,
            @RequestParam(required=false) AdminHostNoteType type,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size){return notes.notes(hostId,type,page,size);}
    @GetMapping("/{hostId}/timeline") public AdminHostTimelineResponse timeline(@PathVariable UUID hostId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return notes.timeline(hostId,page,size);}
}
