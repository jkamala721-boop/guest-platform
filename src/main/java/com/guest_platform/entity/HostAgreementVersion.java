package com.guest_platform.entity;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="host_agreement_versions")
public class HostAgreementVersion {
 @Id private UUID id; @Column(nullable=false,unique=true,length=40) private String version; @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,columnDefinition="text") private String content; @Column(name="content_hash",nullable=false,length=64) private String contentHash;
 @Column(name="effective_at",nullable=false) private Instant effectiveAt; @Column(name="material_change",nullable=false) private boolean materialChange;
 @Column(nullable=false) private boolean active; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_admin_id") private AdminUser createdByAdmin;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected HostAgreementVersion(){}
 public HostAgreementVersion(String version,String title,String content,String hash,Instant effectiveAt,boolean materialChange,boolean active,AdminUser admin){this.version=version;this.title=title;this.content=content;this.contentHash=hash;this.effectiveAt=effectiveAt;this.materialChange=materialChange;this.active=active;this.createdByAdmin=admin;}
 @PrePersist void create(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();}
 public void activate(){active=true;} public void deactivate(){active=false;}
 public UUID getId(){return id;} public String getVersion(){return version;} public String getTitle(){return title;} public String getContent(){return content;}
 public String getContentHash(){return contentHash;} public Instant getEffectiveAt(){return effectiveAt;} public boolean isMaterialChange(){return materialChange;} public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;}
}
