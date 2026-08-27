package com.guest_platform.dto;
import java.time.Instant; import java.util.UUID; import com.guest_platform.entity.*;
public record AdminHostNoteResponse(UUID noteId,UUID hostId,AdminHostNoteType type,String content,UUID authorAdminId,
 String authorName,AdminRole authorRole,Instant createdAt) {
 public static AdminHostNoteResponse from(AdminHostNote n){return new AdminHostNoteResponse(n.getId(),n.getHost().getId(),n.getType(),n.getContent(),n.getAuthor().getId(),n.getAuthor().getDisplayName(),n.getAuthor().getRole(),n.getCreatedAt());}
}
