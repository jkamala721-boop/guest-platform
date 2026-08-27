package com.guest_platform.service;
import java.util.*; import org.springframework.data.domain.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import com.guest_platform.dto.*; import com.guest_platform.entity.*; import com.guest_platform.exception.LifecycleNotFoundException; import com.guest_platform.repository.*;

@Service public class AdminHostNoteTimelineService {
 private final HostRepository hosts; private final AdminUserRepository admins; private final AdminHostNoteRepository notes;
 private final HostVerificationEventRepository verificationEvents; private final HostAgreementAcceptanceRepository acceptances;
 private final BookingRepository bookings; private final HostPayoutRepository payouts; private final AdminAuditService audit;
 private final AdminAuditLogRepository auditRows;
 public AdminHostNoteTimelineService(HostRepository h,AdminUserRepository a,AdminHostNoteRepository n,HostVerificationEventRepository v,
  HostAgreementAcceptanceRepository ac,BookingRepository b,HostPayoutRepository p,AdminAuditService audit,AdminAuditLogRepository auditRows){hosts=h;admins=a;notes=n;verificationEvents=v;acceptances=ac;bookings=b;payouts=p;this.audit=audit;this.auditRows=auditRows;}

 @Transactional public AdminHostNoteResponse create(UUID adminId,UUID hostId,AdminHostNoteType type,String content){
  Host host=host(hostId);AdminUser author=admins.findById(adminId).orElseThrow(()->new IllegalArgumentException("Admin unavailable"));
  String safe=content==null?"":content.trim();if(safe.isEmpty()||safe.length()>5000)throw new IllegalArgumentException("Note content is required and must not exceed 5000 characters");
  AdminHostNote note=notes.save(new AdminHostNote(host,author,type,safe));
  audit.record(author,AdminAuditService.ADMIN_HOST_NOTE_CREATED,"HOST_NOTE",note.getId().toString(),"hostId="+hostId+"; type="+type);
  return AdminHostNoteResponse.from(note);
 }
 @Transactional(readOnly=true) public AdminHostNotePageResponse notes(UUID hostId,AdminHostNoteType type,int page,int size){
  host(hostId);validate(page,size);Page<AdminHostNote> result=notes.findHostNotes(hostId,type,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
  return new AdminHostNotePageResponse(result.map(AdminHostNoteResponse::from).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
 }
 @Transactional(readOnly=true) public AdminHostTimelineResponse timeline(UUID hostId,int page,int size){
  host(hostId);validate(page,size);long requested=(long)(page+1)*size;if(requested>10000)throw new IllegalArgumentException("Timeline pagination window is too large");
  Pageable window=PageRequest.of(0,(int)requested,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")));List<AdminHostTimelineResponse.Item> all=new ArrayList<>();
  Page<AdminHostNote> noteRows=notes.findHostNotes(hostId,null,window);noteRows.forEach(n->all.add(item(n.getCreatedAt(),"NOTE","ADMIN_HOST_NOTE_CREATED","Internal "+n.getType().name().toLowerCase(Locale.ROOT)+" note",n.getContent(),"ADMIN",n.getAuthor().getId(),"HOST_NOTE",n.getId().toString())));
  Page<HostVerificationEvent> verificationRows=verificationEvents.findTimeline(hostId,window);verificationRows.forEach(e->all.add(item(e.getCreatedAt(),"VERIFICATION",e.getEventType(),verificationTitle(e.getEventType()),safeReason(e.getReason()),e.getActorType(),e.getActorId(),"HOST_VERIFICATION",e.getVerification().getId().toString())));
  Page<HostAgreementAcceptance> acceptanceRows=acceptances.findByHostId(hostId,window);acceptanceRows.forEach(a->all.add(item(a.getAcceptedAt(),"AGREEMENT",a.getEventType(),"Host agreement accepted","Agreement version "+a.getAgreementVersion().getVersion()+" accepted","HOST",hostId,"HOST_AGREEMENT",a.getAgreementVersion().getId().toString())));
  Page<Booking> bookingRows=bookings.findByHostId(hostId,window);bookingRows.forEach(b->all.add(item(b.getUpdatedAt(),"BOOKING",bookingEvent(b),bookingTitle(b),"Booking status: "+b.getStatus(),"SYSTEM",null,"BOOKING",b.getId().toString())));
  Page<HostPayout> payoutRows=payouts.findByHostId(hostId,window);Set<String> payoutIds=new HashSet<>();payoutRows.forEach(p->payoutIds.add(p.getId().toString()));Map<String,AdminAuditLog> payoutActions=payoutIds.isEmpty()?Map.of():auditRows.findPayoutActions(payoutIds).stream().collect(java.util.stream.Collectors.toMap(AdminAuditLog::getEntityId,java.util.function.Function.identity(),(newest,ignored)->newest));
  payoutRows.forEach(p->{AdminAuditLog action=payoutActions.get(p.getId().toString());all.add(item(p.getUpdatedAt(),"PAYOUT",action==null?payoutEvent(p):action.getAction(),payoutTitle(p),payoutSummary(p),action==null?"SYSTEM":"ADMIN",action==null||action.getAdminUser()==null?null:action.getAdminUser().getId(),"HOST_PAYOUT",p.getId().toString()));});
  Page<AdminAuditLog> accountRows=auditRows.findHostAccountTimeline(hostId.toString(),window);accountRows.forEach(a->all.add(item(a.getCreatedAt(),"ACCOUNT",a.getAction(),a.getAction().equals(AdminAuditService.HOST_SUSPENDED)?"Host suspended":"Host reactivated",safeReason(a.getReason()),"ADMIN",a.getAdminUser()==null?null:a.getAdminUser().getId(),"HOST",hostId.toString())));
  all.sort(Comparator.comparing(AdminHostTimelineResponse.Item::timestamp).reversed().thenComparing(AdminHostTimelineResponse.Item::relatedEntityId));
  long total=noteRows.getTotalElements()+verificationRows.getTotalElements()+acceptanceRows.getTotalElements()+bookingRows.getTotalElements()+payoutRows.getTotalElements()+accountRows.getTotalElements();int from=Math.min(page*size,all.size()),to=Math.min(from+size,all.size());
  return new AdminHostTimelineResponse(List.copyOf(all.subList(from,to)),page,size,total,(int)((total+size-1)/size));
 }
 private Host host(UUID id){return hosts.findById(id).orElseThrow(()->new LifecycleNotFoundException("ADMIN_HOST_NOT_FOUND","Host was not found."));}
 private void validate(int page,int size){if(page<0||size<1||size>100)throw new IllegalArgumentException("Invalid pagination");}
 private AdminHostTimelineResponse.Item item(java.time.Instant at,String category,String type,String title,String summary,String actorType,UUID actorId,String entityType,String entityId){return new AdminHostTimelineResponse.Item(at,category,type,title,summary,actorType,actorId,entityType,entityId);}
 private String verificationTitle(String type){return switch(type){case "HOST_VERIFICATION_SUBMITTED"->"Verification submitted";case "HOST_VERIFICATION_REVIEW_STARTED"->"Verification review started";case "HOST_VERIFICATION_APPROVED"->"Verification approved";case "HOST_VERIFICATION_REJECTED"->"Verification rejected";default->"Verification updated";};}
 private String safeReason(String reason){return reason==null?null:reason.substring(0,Math.min(reason.length(),500));}
 private String bookingEvent(Booking b){return b.getStatus()==BookingStatus.CANCELLED?"BOOKING_CANCELLED":b.getStatus()==BookingStatus.CONFIRMED?"BOOKING_CONFIRMED":"BOOKING_CREATED";}
 private String bookingTitle(Booking b){return b.getStatus()==BookingStatus.CANCELLED?"Booking cancelled":b.getStatus()==BookingStatus.CONFIRMED?"Booking confirmed":"Booking created";}
 private String payoutEvent(HostPayout p){if("manual_confirmed".equals(p.getProviderStatus()))return AdminAuditService.HOST_PAYOUT_MANUAL_CONFIRMED;if("manual_failure".equals(p.getProviderStatus()))return AdminAuditService.HOST_PAYOUT_MARKED_FAILED;return "HOST_PAYOUT_"+p.getStatus();}
 private String payoutTitle(HostPayout p){return switch(p.getStatus()){case PENDING->"Payout pending";case AVAILABLE->"Payout available";case PROCESSING->"Payout processing";case FAILED->"Payout failed";case PAID->"Payout paid";};}
 private String payoutSummary(HostPayout p){return "Payout status: "+p.getStatus()+"; amount: "+p.getAmount()+" "+p.getCurrency();}
}
