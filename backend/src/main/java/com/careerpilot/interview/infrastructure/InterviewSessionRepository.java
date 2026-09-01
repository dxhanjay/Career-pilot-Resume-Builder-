package com.careerpilot.interview.infrastructure;

import com.careerpilot.interview.domain.InterviewEnums;
import com.careerpilot.interview.domain.InterviewSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interview sessions, always scoped to their owner.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId);

    Page<InterviewSession> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<InterviewSession> findByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId, InterviewEnums.SessionStatus status);

    long countByUserId(UUID userId);

    /**
     * @return the mean completed-session score, or null when none are complete
     */
    @Query("""
            SELECT AVG(s.overallScore) FROM InterviewSession s
             WHERE s.userId = :userId AND s.overallScore IS NOT NULL
            """)
    Double averageScore(@Param("userId") UUID userId);
}
