package com.careerpilot.matching.infrastructure;

import com.careerpilot.matching.domain.JobDescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Job postings, scoped to their owner and excluding soft-deleted rows.
 *
 * <p>There is deliberately no {@code findById}-by-id-alone finder here. Every
 * read takes the user id, so a controller cannot accidentally serve one user's
 * saved posting to another.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {

    @Query("""
            SELECT j FROM JobDescription j
             WHERE j.id = :id AND j.userId = :userId AND j.deletedAt IS NULL
            """)
    Optional<JobDescription> findActive(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT j FROM JobDescription j
             WHERE j.userId = :userId AND j.deletedAt IS NULL
            """)
    Page<JobDescription> findAllActive(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT COUNT(j) FROM JobDescription j
             WHERE j.userId = :userId AND j.deletedAt IS NULL
            """)
    long countActive(@Param("userId") UUID userId);
}
