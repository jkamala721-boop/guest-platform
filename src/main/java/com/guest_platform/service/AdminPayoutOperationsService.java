package com.guest_platform.service;
import java.util.*; import java.util.function.Function; import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException; import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException; import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.dto.AdminPayoutDtos; import com.guest_platform.entity.*;
import com.guest_platform.exception.*; import com.guest_platform.repository.*;

@Service public class AdminPayoutOperationsService {
 private final HostPayoutRepository payouts; private final HostPayoutSettingsRepository settings;
 private final AdminUserRepository admins; private final AdminAuditService audit;
 public AdminPayoutOperationsService(HostPayoutRepository p,HostPayoutSettingsRepository s,AdminUserRepository a,AdminAuditService audit){payouts=p;settings=s;admins=a;this.audit=audit;}
 @Transactional(readOnly=true) public AdminPayoutDtos.PageResponse list(UUID hostId,HostPayoutStatus status,PaymentProvider provider,PayoutMethod method,String q,int page,int size){
  if(page<0||size<1||size>100)throw new IllegalArgumentException("Invalid pagination");
  String search=q==null||q.isBlank()?null:"%"+q.trim().toLowerCase(Locale.ROOT)+"%";
  Page<HostPayout> result=payouts.searchAdmin(hostId,status,provider,method,search,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id"))));
  Map<UUID,HostPayoutSettings> destinations=settings.findAllByHostIdIn(result.getContent().stream().map(p->p.getHost().getId()).collect(Collectors.toSet())).stream().collect(Collectors.toMap(HostPayoutSettings::getHostId,Function.identity()));
  return new AdminPayoutDtos.PageResponse(result.getContent().stream().map(p->item(p,destinations.get(p.getHost().getId()))).toList(),page,size,result.getTotalElements(),result.getTotalPages());
 }
 @Transactional(readOnly=true) public AdminPayoutDtos.Detail detail(UUID id){HostPayout p=payouts.findAdminDetailById(id).orElseThrow(this::notFound);return detail(p,settings.findByHostId(p.getHost().getId()).orElse(null));}
 @Transactional public AdminPayoutDtos.Detail confirm(UUID adminId,UUID payoutId,String reference,String note){
  AdminUser admin=financeAdmin(adminId);String ref=required(reference,100,"ADMIN_PAYOUT_REFERENCE_REQUIRED");String safeNote=optional(note,1000);
  HostPayout p=payouts.findForUpdateById(payoutId).orElseThrow(this::notFound);
  if(p.getStatus()==HostPayoutStatus.PAID&&"manual_confirmed".equals(p.getProviderStatus())&&ref.equals(p.getTransferCode()))return detail(p,settings.findByHostId(p.getHost().getId()).orElse(null));
  if(!p.confirmManual(ref))throw invalidState();
  try{payouts.saveAndFlush(p);}catch(DataIntegrityViolationException e){throw new LifecycleConflictException("HOST_PAYOUT_INVALID_STATE","That external payout reference is already in use.");}
  audit.record(admin,AdminAuditService.HOST_PAYOUT_MANUAL_CONFIRMED,"HOST_PAYOUT",payoutId.toString(),safeNote==null?"Reference: "+ref:"Reference: "+ref+"; "+safeNote);
  return detail(p,settings.findByHostId(p.getHost().getId()).orElse(null));
 }
 @Transactional public AdminPayoutDtos.Detail markFailed(UUID adminId,UUID payoutId,String reason){
  AdminUser admin=financeAdmin(adminId);String safe=required(reason,500,"VALIDATION_ERROR");HostPayout p=payouts.findForUpdateById(payoutId).orElseThrow(this::notFound);
  boolean already=p.getStatus()==HostPayoutStatus.FAILED&&!p.isRetryable()&&safe.equals(p.getFailureReason());if(!p.markManuallyFailed(safe))throw invalidState();
  if(!already)audit.record(admin,AdminAuditService.HOST_PAYOUT_MARKED_FAILED,"HOST_PAYOUT",payoutId.toString(),safe);
  return detail(p,settings.findByHostId(p.getHost().getId()).orElse(null));
 }
 private AdminPayoutDtos.Detail detail(HostPayout p,HostPayoutSettings s){return new AdminPayoutDtos.Detail(item(p,s),p.getProviderStatus(),p.getAttemptCount(),p.isRetryable(),p.getLastAttemptAt(),p.getUpdatedAt());}
 private AdminPayoutDtos.Item item(HostPayout p,HostPayoutSettings s){String last4=s==null?null:p.getPayoutMethod()==PayoutMethod.MPESA?s.getMpesaPhoneLast4():s.getAccountNumberLast4();String masked=last4==null?null:(p.getPayoutMethod()==PayoutMethod.MPESA?"M-Pesa •••• ":"Bank •••• ")+last4;Payment payment=p.getPayment();return new AdminPayoutDtos.Item(p.getId(),p.getHost().getId(),p.getHost().getEmail(),p.getHost().getFullName(),payment.getBooking().getId(),p.getAmount(),p.getCurrency(),p.getStatus(),payment.getProvider(),p.getPayoutMethod(),masked,last4,p.getTransferCode(),p.getCreatedAt(),p.getCompletedAt(),p.getFailureReason());}
 private AdminUser financeAdmin(UUID id){AdminUser a=admins.findById(id).orElseThrow(()->new AccessDeniedException("Admin unavailable"));if(a.getRole()!=AdminRole.FINANCE&&a.getRole()!=AdminRole.SUPER_ADMIN)throw new AccessDeniedException("Finance role required");return a;}
 private String required(String v,int max,String code){if(v==null||v.isBlank())throw new LifecycleConflictException(code,"A value is required.");v=v.trim();if(v.length()>max)throw new IllegalArgumentException("Value is too long");return v;}
 private String optional(String v,int max){if(v==null||v.isBlank())return null;v=v.trim();if(v.length()>max)throw new IllegalArgumentException("Value is too long");return v;}
 private LifecycleNotFoundException notFound(){return new LifecycleNotFoundException("ADMIN_PAYOUT_NOT_FOUND","Payout was not found.");}
 private LifecycleConflictException invalidState(){return new LifecycleConflictException("HOST_PAYOUT_INVALID_STATE","Payout cannot be changed from its current state.");}
}
