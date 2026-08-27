package com.guest_platform.repository;
import java.util.UUID; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import com.guest_platform.entity.HostVerificationEvent;
public interface HostVerificationEventRepository extends JpaRepository<HostVerificationEvent,UUID>{
 @EntityGraph(attributePaths="verification") @Query("select e from HostVerificationEvent e where e.verification.host.id=:hostId")
 Page<HostVerificationEvent> findTimeline(@Param("hostId") UUID hostId,Pageable pageable);
}
