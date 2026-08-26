package com.guest_platform.dto;
import java.time.Instant; import jakarta.validation.constraints.*;
public record AdminAgreementCreateRequest(@NotBlank @Size(max=40) String version,@NotBlank @Size(max=200) String title,
 @NotBlank @Size(max=200000) String content,@NotNull Instant effectiveAt,boolean materialChange,boolean activate) {}
