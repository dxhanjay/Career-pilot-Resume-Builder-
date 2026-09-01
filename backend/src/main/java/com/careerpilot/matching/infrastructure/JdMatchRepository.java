package com.careerpilot.matching.infrastructure;

import com.careerpilot.matching.domain.JdMatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stored match runs.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface JdMatchRepository extends JpaRepository<JdMatch, UUID> {

    @Query("""
            SELECT m FROM JdMatch m
             WHERE m.jobDescriptionId = :jobDescriptionId
               AND m.userId = :userId
             ORDER BY m.createdAt DESC
             LIMIT 1
            """)
    Optional<JdMatch> findLatestForPosting(@Param("jobDescriptionId") UUID jobDescriptionId,
                                           @Param("userId") UUID userId);

    Optional<JdMatch> findByIdAndUserId(UUID id, UUID userId);

    List<JdMatch> findByJobDescriptionIdAndUserIdOrderByCreatedAtDesc(UUID jobDescriptionId,
                                                                      UUID userId);

    Page<JdMatch> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
