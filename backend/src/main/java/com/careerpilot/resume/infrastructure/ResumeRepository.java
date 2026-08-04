package com.careerpilot.resume.infrastructure;

import com.careerpilot.resume.domain.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link Resume}.
 *
 * <p><strong>Every read method takes {@code userId}.</strong> There is
 * deliberately no {@code findById(UUID)} in this interface's vocabulary — the
 * ownership predicate lives inside each query, so it cannot be forgotten at a
 * call site.
 *
 * <p>That is the structural defence against IDOR, which is the vulnerability
 * most likely to actually appear in this codebase: every resource is user-owned
 * and addressed by UUID, so one repository call that omits the owner exposes
 * another user's CV. A code review can miss a missing comparison; a method that
 * does not exist cannot be called.
 *
 * <p>{@code JpaRepository} still inherits {@code findById}. The ArchUnit rules
 * cannot forbid an inherited method, so this is a review convention rather than
 * a mechanical one — but the named alternatives make doing it correctly the path
 * of least resistance.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    /**
     * Finds a non-deleted resume owned by a specific user.
     *
     * <p>Returns empty both when the resume does not exist and when it belongs
     * to somebody else. The caller maps both to a 404, which is what keeps the
     * two cases indistinguishable to an enumerating client.
     *
     * @param id     resume identifier
     * @param userId the requesting user
     * @return the resume, if it exists and is theirs
     */
    @Query("SELECT r FROM Resume r WHERE r.id = :id AND r.userId = :userId AND r.deletedAt IS NULL")
    Optional<Resume> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * A user's resumes, newest first.
     *
     * @param userId   the owner
     * @param pageable page request
     * @return one page of resumes
     */
    @Query("SELECT r FROM Resume r WHERE r.userId = :userId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    Page<Resume> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Finds a live resume with identical content (FR-RES-05).
     *
     * <p>Scoped per user: two people uploading the same public CV template is
     * not a conflict, whereas the same person uploading the same file twice is.
     *
     * @param userId   the owner
     * @param checksum SHA-256 of the candidate file
     * @return the existing resume, if this content was already uploaded
     */
    @Query("""
            SELECT r FROM Resume r
             WHERE r.userId = :userId
               AND r.checksumSha256 = :checksum
               AND r.deletedAt IS NULL
            """)
    Optional<Resume> findByUserIdAndChecksum(@Param("userId") UUID userId,
                                             @Param("checksum") String checksum);

    /**
     * The highest version number a user has used.
     *
     * <p>Per-user rather than global, so a student's first upload is "version 1"
     * regardless of how many other people have signed up.
     *
     * @param userId the owner
     * @return the current maximum, or empty if they have never uploaded
     */
    @Query("SELECT MAX(r.version) FROM Resume r WHERE r.userId = :userId")
    Optional<Short> findMaxVersionByUserId(@Param("userId") UUID userId);

    /**
     * The user's current primary resume, if any.
     *
     * @param userId the owner
     * @return the primary resume
     */
    @Query("SELECT r FROM Resume r WHERE r.userId = :userId AND r.primary = true AND r.deletedAt IS NULL")
    Optional<Resume> findPrimaryByUserId(@Param("userId") UUID userId);

    /**
     * Counts a user's live resumes, for quota enforcement.
     *
     * @param userId the owner
     * @return how many resumes they currently hold
     */
    @Query("SELECT COUNT(r) FROM Resume r WHERE r.userId = :userId AND r.deletedAt IS NULL")
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Resumes soft-deleted before a cut-off, for the purge job.
     *
     * <p>Returns entities rather than deleting directly, because the stored file
     * must be removed from the storage provider before the row that names it
     * disappears. Deleting the row first would orphan the object permanently —
     * nothing would remain to say it existed.
     *
     * @param cutoff soft-deleted before this instant
     * @return resumes eligible for permanent removal
     */
    @Query("SELECT r FROM Resume r WHERE r.deletedAt IS NOT NULL AND r.deletedAt < :cutoff")
    List<Resume> findPurgeable(@Param("cutoff") Instant cutoff);

    /**
     * Clears the primary flag across a user's resumes.
     *
     * <p>A bulk update rather than load-and-modify: a partial unique index
     * permits only one primary per user, so the old flag must be cleared and the
     * new one set within a single transaction or the insert violates it.
     *
     * @param userId the owner
     * @return number of rows changed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Resume r SET r.primary = false WHERE r.userId = :userId AND r.primary = true")
    int clearPrimaryForUser(@Param("userId") UUID userId);
}
