package com.guest_platform.repository;
import java.util.UUID; import java.util.List; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param; import com.guest_platform.entity.*;
public interface AdminHostNoteRepository extends JpaRepository<AdminHostNote,UUID>{
 @EntityGraph(attributePaths="author")
 @Query("select n from AdminHostNote n where n.host.id=:hostId and (:type is null or n.type=:type)")
 Page<AdminHostNote> findHostNotes(@Param("hostId") UUID hostId,@Param("type") AdminHostNoteType type,Pageable pageable);
 @EntityGraph(attributePaths="author") List<AdminHostNote> findByHostIdOrderByCreatedAtDesc(UUID hostId,Pageable pageable);
}
