package com.guest_platform.repository;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.guest_platform.entity.HostAgreementAcceptance;
public interface HostAgreementAcceptanceRepository extends JpaRepository<HostAgreementAcceptance,UUID>{
 Optional<HostAgreementAcceptance> findByHostIdAndAgreementVersionId(UUID hostId,UUID agreementVersionId);
 boolean existsByHostIdAndAgreementVersionId(UUID hostId,UUID agreementVersionId);
}
