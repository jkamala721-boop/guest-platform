package com.guest_platform.entity;
import java.time.Instant; import java.time.temporal.ChronoUnit; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="host_agreement_acceptances",uniqueConstraints=@UniqueConstraint(columnNames={"host_id","agreement_version_id"}))
public class HostAgreementAcceptance {
 @Id private UUID id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="host_id",nullable=false) private Host host;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="agreement_version_id",nullable=false) private HostAgreementVersion agreementVersion;
 @Column(name="event_type",nullable=false,length=80) private String eventType;
 @Column(name="accepted_at",nullable=false,updatable=false) private Instant acceptedAt; @Column(name="ip_address_hash",length=64) private String ipAddressHash;
 @Column(name="user_agent_summary",length=250) private String userAgentSummary; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected HostAgreementAcceptance(){}
 public HostAgreementAcceptance(Host host,HostAgreementVersion version,String ipHash,String userAgent){this.host=host;this.agreementVersion=version;this.eventType="HOST_AGREEMENT_ACCEPTED";this.ipAddressHash=ipHash;this.userAgentSummary=userAgent;}
 @PrePersist void create(){if(id==null)id=UUID.randomUUID();Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);acceptedAt=createdAt=now;}
 public Instant getAcceptedAt(){return acceptedAt;} public HostAgreementVersion getAgreementVersion(){return agreementVersion;} public String getEventType(){return eventType;} public String getIpAddressHash(){return ipAddressHash;} public String getUserAgentSummary(){return userAgentSummary;}
}
