package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.domain.ResumeParse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link ResumeParse}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface ResumeParseRepository extends JpaRepository<ResumeParse, UUID> {

    /**
     * The most recent successful parse for a resume, scoped to its owner.
     *
     * <p>"Latest successful" rather than "latest": a resume parsed successfully
     * and then re-parsed unsuccessfully after a parser upgrade should still show
     * its working extraction rather than suddenly appearing broken to the user.
     *
     * @param resumeId the resume
     * @param userId   the requesting user
     * @return the newest successful parse, if any
     */
    @Query("""
            SELECT p FROM ResumeParse p
             WHERE p.resumeId = :resumeId
               AND p.userId = :userId
               AND p.status = com.careerpilot.parsing.domain.ParseStatus.SUCCEEDED
             ORDER BY p.createdAt DESC
             LIMIT 1
            """)
    Optional<ResumeParse> findLatestSuccessful(@Param("resumeId") UUID resumeId,
                                               @Param("userId") UUID userId);

    /**
     * The most recent attempt of any outcome, scoped to its owner.
     *
     * <p>Used to explain a failure: the user needs the error and the warnings
     * from the attempt that failed, not from an older one that worked.
     *
     * @param resumeId the resume
     * @param userId   the requesting user
     * @return the newest attempt, if any
     */
    @Query("""
            SELECT p FROM ResumeParse p
             WHERE p.resumeId = :resumeId
               AND p.userId = :userId
             ORDER BY p.createdAt DESC
             LIMIT 1
            """)
    Optional<ResumeParse> findLatestAttempt(@Param("resumeId") UUID resumeId,
                                            @Param("userId") UUID userId);
}
