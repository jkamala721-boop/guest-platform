package com.guest_platform.repository;
import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType;
import com.guest_platform.entity.HostVerification;
public interface HostVerificationRepository extends JpaRepository<HostVerification,UUID>{
 Optional<HostVerification> findByHostId(UUID hostId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from HostVerification v where v.host.id=:hostId") Optional<HostVerification> findForUpdateByHostId(@Param("hostId") UUID hostId);
 List<HostVerification> findAllByOrderBySubmittedAtDesc();
}
