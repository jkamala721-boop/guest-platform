package com.guest_platform.dto;
import java.time.Instant; import java.util.UUID; import com.guest_platform.entity.HostAgreementVersion;
public record AgreementResponse(UUID id,String version,String title,String content,Instant effectiveAt,boolean materialChange,boolean active,boolean accepted,Instant acceptedAt){
 public static AgreementResponse of(HostAgreementVersion v,boolean accepted,Instant acceptedAt){return new AgreementResponse(v.getId(),v.getVersion(),v.getTitle(),v.getContent(),v.getEffectiveAt(),v.isMaterialChange(),v.isActive(),accepted,acceptedAt);}
}
