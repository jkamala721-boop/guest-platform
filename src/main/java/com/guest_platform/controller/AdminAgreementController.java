package com.guest_platform.controller;
import java.util.*; import jakarta.validation.Valid; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import com.guest_platform.dto.*; import com.guest_platform.security.AdminPrincipal; import com.guest_platform.service.HostAgreementService;
@RestController @RequestMapping("/api/admin/agreements")
public class AdminAgreementController {
 private final HostAgreementService service; public AdminAgreementController(HostAgreementService service){this.service=service;}
 @GetMapping public List<AgreementResponse> list(){return service.list();}
 @GetMapping("/{id}") public AgreementResponse get(@PathVariable UUID id){return service.get(id);}
 @PostMapping public AgreementResponse create(Authentication a,@Valid @RequestBody AdminAgreementCreateRequest request){return service.create(adminId(a),request);}
 @PostMapping("/{id}/activate") public AgreementResponse activate(Authentication a,@PathVariable UUID id){return service.activate(adminId(a),id);}
 private UUID adminId(Authentication a){return ((AdminPrincipal)a.getPrincipal()).id();}
}
