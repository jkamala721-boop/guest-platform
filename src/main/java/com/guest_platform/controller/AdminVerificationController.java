package com.guest_platform.controller;
import java.time.Instant; import java.util.*; import jakarta.validation.Valid; import org.springframework.format.annotation.DateTimeFormat; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*; import com.guest_platform.entity.HostVerificationStatus; import com.guest_platform.security.AdminPrincipal; import com.guest_platform.service.HostVerificationService;
@RestController @RequestMapping("/api/admin")
public class AdminVerificationController {
 private final HostVerificationService service; public AdminVerificationController(HostVerificationService service){this.service=service;}
 @GetMapping("/hosts/{hostId}/verification") public AdminHostVerificationResponse get(@PathVariable UUID hostId){return service.adminGet(hostId);}
 @GetMapping("/verifications") public List<AdminHostVerificationResponse> list(@RequestParam(required=false) HostVerificationStatus status,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant submittedFrom,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant reviewedFrom,@RequestParam(required=false) String search){return service.list(status,submittedFrom,reviewedFrom,search);}
 @PostMapping("/hosts/{hostId}/verification/start-review") public AdminHostVerificationResponse start(Authentication a,@PathVariable UUID hostId){return service.startReview(adminId(a),hostId);}
 @PostMapping("/hosts/{hostId}/verification/approve") public AdminHostVerificationResponse approve(Authentication a,@PathVariable UUID hostId){return service.approve(adminId(a),hostId);}
 @PostMapping("/hosts/{hostId}/verification/reject") public AdminHostVerificationResponse reject(Authentication a,@PathVariable UUID hostId,@Valid @RequestBody ReasonRequest request){return service.reject(adminId(a),hostId,request.reason());}
 @PostMapping("/hosts/{hostId}/suspend") public AdminHostVerificationResponse suspend(Authentication a,@PathVariable UUID hostId,@Valid @RequestBody ReasonRequest request){return service.suspend(adminId(a),hostId,request.reason());}
 @PostMapping("/hosts/{hostId}/reactivate") public AdminHostVerificationResponse reactivate(Authentication a,@PathVariable UUID hostId){return service.reactivate(adminId(a),hostId);}
 private UUID adminId(Authentication a){return ((AdminPrincipal)a.getPrincipal()).id();}
}
