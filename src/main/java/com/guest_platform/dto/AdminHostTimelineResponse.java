package com.guest_platform.dto;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record AdminHostTimelineResponse(List<Item> items,int page,int size,long totalElements,int totalPages){
 public record Item(Instant timestamp,String category,String eventType,String title,String summary,String actorType,
  UUID actorId,String relatedEntityType,String relatedEntityId) {}
}
