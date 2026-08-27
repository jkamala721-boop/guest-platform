package com.guest_platform.dto;
import java.util.List;
public record AdminHostNotePageResponse(List<AdminHostNoteResponse> items,int page,int size,long totalElements,int totalPages) {}
