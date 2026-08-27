package com.guest_platform.repository;
import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import com.guest_platform.entity.HostAgreementVersion;
public interface HostAgreementVersionRepository extends JpaRepository<HostAgreementVersion,UUID>{
 Optional<HostAgreementVersion> findByVersion(String version); Optional<HostAgreementVersion> findFirstByActiveTrueOrderByEffectiveAtDesc();
 List<HostAgreementVersion> findAllByOrderByCreatedAtDesc();
 <T> Optional<T> findFirstByActiveTrueOrderByEffectiveAtDesc(Class<T> projectionType);
 @Modifying @Query("update HostAgreementVersion v set v.active=false where v.active=true and v.id<>:id") int deactivateOthers(@Param("id") UUID id);
 interface CurrentAgreementSummary { UUID getId(); String getVersion(); }
}
