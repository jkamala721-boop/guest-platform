package com.guest_platform.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*;
import com.guest_platform.entity.*;
import com.guest_platform.service.AdminHostOperationsService;

@RestController
@RequestMapping("/api/admin/hosts")
public class AdminHostController {
    private final AdminHostOperationsService service;
    public AdminHostController(AdminHostOperationsService service){this.service=service;}

    @GetMapping
    public AdminHostPageResponse list(@RequestParam(required=false) String q,
            @RequestParam(required=false) HostAccountStatus accountStatus,
            @RequestParam(required=false) HostVerificationStatus verificationStatus,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return service.list(q,accountStatus,verificationStatus,page,size);
    }

    @GetMapping("/{hostId}")
    public AdminHostDetailResponse detail(@PathVariable UUID hostId){return service.detail(hostId);}
}
