package com.guest_platform.repository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.guest_platform.entity.ReturningGuestVerificationChallenge;
public interface ReturningGuestVerificationChallengeRepository extends JpaRepository<ReturningGuestVerificationChallenge, UUID> {
    Optional<ReturningGuestVerificationChallenge> findFirstByGuestLinkIdOrderByCreatedAtDesc(UUID guestLinkId);
}
