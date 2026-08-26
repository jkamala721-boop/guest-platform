package com.guest_platform.entity;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="host_verification_events")
public class HostVerificationEvent {
 @Id private UUID id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="verification_id",nullable=false) private HostVerification verification;
 @Column(name="actor_type",nullable=false,length=20) private String actorType; @Column(name="actor_id",nullable=false) private UUID actorId;
 @Column(name="event_type",nullable=false,length=80) private String eventType;
 @Enumerated(EnumType.STRING) @Column(name="previous_status",length=30) private HostVerificationStatus previousStatus;
 @Enumerated(EnumType.STRING) @Column(name="new_status",nullable=false,length=30) private HostVerificationStatus newStatus;
 @Column(length=1000) private String reason; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected HostVerificationEvent(){}
 public HostVerificationEvent(HostVerification v,String actorType,UUID actorId,String event,HostVerificationStatus previous,HostVerificationStatus next,String reason){this.verification=v;this.actorType=actorType;this.actorId=actorId;this.eventType=event;this.previousStatus=previous;this.newStatus=next;this.reason=reason;}
 @PrePersist void create(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();}
 public String getEventType(){return eventType;} public String getReason(){return reason;} public HostVerificationStatus getNewStatus(){return newStatus;}
}
