package com.guest_platform.repository;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository; import com.guest_platform.entity.HostVerificationEvent;
public interface HostVerificationEventRepository extends JpaRepository<HostVerificationEvent,UUID>{}
