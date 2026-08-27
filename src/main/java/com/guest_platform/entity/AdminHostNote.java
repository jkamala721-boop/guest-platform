package com.guest_platform.entity;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="admin_host_notes") public class AdminHostNote {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="host_id",nullable=false) private Host host;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_admin_id",nullable=false) private AdminUser author;
 @Enumerated(EnumType.STRING) @Column(name="note_type",nullable=false,length=30) private AdminHostNoteType type;
 @Column(nullable=false,length=5000) private String content;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected AdminHostNote(){}
 public AdminHostNote(Host host,AdminUser author,AdminHostNoteType type,String content){this.host=host;this.author=author;this.type=type;this.content=content;}
 @PrePersist void create(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();}
 public UUID getId(){return id;} public Host getHost(){return host;} public AdminUser getAuthor(){return author;}
 public AdminHostNoteType getType(){return type;} public String getContent(){return content;} public Instant getCreatedAt(){return createdAt;}
}
