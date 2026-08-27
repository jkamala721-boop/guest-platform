package com.guest_platform.controller;
import java.util.UUID; import jakarta.validation.Valid; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*; import com.guest_platform.entity.*; import com.guest_platform.security.AdminPrincipal; import com.guest_platform.service.AdminPayoutOperationsService;
@RestController @RequestMapping("/api/admin/payouts") public class AdminPayoutController {
 private final AdminPayoutOperationsService service; public AdminPayoutController(AdminPayoutOperationsService s){service=s;}
 @GetMapping public AdminPayoutDtos.PageResponse list(@RequestParam(required=false) UUID hostId,@RequestParam(required=false) HostPayoutStatus status,@RequestParam(required=false) PaymentProvider provider,@RequestParam(required=false) PayoutMethod payoutMethod,@RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.list(hostId,status,provider,payoutMethod,q,page,size);}
 @GetMapping("/{payoutId}") public AdminPayoutDtos.Detail detail(@PathVariable UUID payoutId){return service.detail(payoutId);}
 @PostMapping("/{payoutId}/manual-confirm") public AdminPayoutDtos.Detail confirm(@PathVariable UUID payoutId,@Valid @RequestBody AdminManualPayoutConfirmRequest r,Authentication a){return service.confirm(id(a),payoutId,r.externalReference(),r.note());}
 @PostMapping("/{payoutId}/mark-failed") public AdminPayoutDtos.Detail fail(@PathVariable UUID payoutId,@Valid @RequestBody AdminMarkPayoutFailedRequest r,Authentication a){return service.markFailed(id(a),payoutId,r.reason());}
 private UUID id(Authentication a){return ((AdminPrincipal)a.getPrincipal()).id();}
}
