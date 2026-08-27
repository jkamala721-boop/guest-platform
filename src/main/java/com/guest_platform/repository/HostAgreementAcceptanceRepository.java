package com.guest_platform.repository;
import java.time.Instant; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import com.guest_platform.entity.HostAgreementAcceptance;
public interface HostAgreementAcceptanceRepository extends JpaRepository<HostAgreementAcceptance,UUID>{
 Optional<HostAgreementAcceptance> findByHostIdAndAgreementVersionId(UUID hostId,UUID agreementVersionId);
 boolean existsByHostIdAndAgreementVersionId(UUID hostId,UUID agreementVersionId);
 @Query("select acceptance.host.id as hostId, acceptance.acceptedAt as acceptedAt from HostAgreementAcceptance acceptance where acceptance.agreementVersion.id=:versionId and acceptance.host.id in :hostIds")
 List<HostAgreementAcceptanceSummary> summarizeCurrentForHosts(@Param("versionId") UUID versionId,@Param("hostIds") Collection<UUID> hostIds);
 interface HostAgreementAcceptanceSummary { UUID getHostId(); Instant getAcceptedAt(); }
}
