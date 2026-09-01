package com.careerpilot.ats.infrastructure;

import com.careerpilot.ats.domain.AtsAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads of the analysis history.
 *
 * <p>Every finder takes a userId. Analyses are per-user data and an id-only
 * lookup is one refactor away from letting a user read someone else's report.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface AtsAnalysisRepository extends JpaRepository<AtsAnalysis, UUID> {

    @Query("""
            SELECT a FROM AtsAnalysis a
             WHERE a.resumeId = :resumeId
               AND a.userId = :userId
             ORDER BY a.createdAt DESC
             LIMIT 1
            """)
    Optional<AtsAnalysis> findLatest(@Param("resumeId") UUID resumeId,
                                     @Param("userId") UUID userId);

    Optional<AtsAnalysis> findByIdAndUserId(UUID id, UUID userId);

    List<AtsAnalysis> findByResumeIdAndUserIdOrderByCreatedAtDesc(UUID resumeId, UUID userId);

    Page<AtsAnalysis> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
