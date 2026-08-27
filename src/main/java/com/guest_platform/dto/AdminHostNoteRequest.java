package com.guest_platform.dto;
import com.guest_platform.entity.AdminHostNoteType; import jakarta.validation.constraints.*;
public record AdminHostNoteRequest(@NotNull AdminHostNoteType type,@NotBlank @Size(max=5000) String content) {}
