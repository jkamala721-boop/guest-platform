package com.guest_platform.dto;

import java.util.List;

public record AdminHostPageResponse(List<AdminHostListItem> items,int page,int size,long totalElements,int totalPages) {}
