package com.careerpilot.admin.application;

import com.careerpilot.admin.application.dto.AdminDtos;
import com.careerpilot.ats.infrastructure.AtsAnalysisRepository;
import com.careerpilot.auth.domain.User;
import com.careerpilot.auth.domain.UserStatus;
import com.careerpilot.auth.infrastructure.UserRepository;
import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.interview.infrastructure.InterviewSessionRepository;
import com.careerpilot.jobs.domain.JobStatus;
import com.careerpilot.jobs.infrastructure.JobRepository;
import com.careerpilot.matching.infrastructure.JdMatchRepository;
import com.careerpilot.resume.infrastructure.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The administrator's read model and the two write actions they have.
 *
 * <p>This is the one place that deliberately reaches across module boundaries
 * into other modules' repositories. An operational dashboard needs totals from
 * every table, and the alternative — a counting method on every module's service
 * that only this class calls — spreads admin concerns through the whole codebase
 * to preserve a boundary nothing else is crossing. The access is read-only, and
 * the only writes are to the user aggregate this module legitimately owns
 * alongside auth.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final Duration RECENT_WINDOW = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final AtsAnalysisRepository analysisRepository;
    private final JdMatchRepository matchRepository;
    private final InterviewSessionRepository interviewRepository;
    private final JobRepository jobRepository;

    public AdminService(UserRepository userRepository,
                        ResumeRepository resumeRepository,
                        AtsAnalysisRepository analysisRepository,
                        JdMatchRepository matchRepository,
                        InterviewSessionRepository interviewRepository,
                        JobRepository jobRepository) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.analysisRepository = analysisRepository;
        this.matchRepository = matchRepository;
        this.interviewRepository = interviewRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Stats stats() {
        return new AdminDtos.Stats(
                userRepository.countActive(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.PENDING),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countCreatedSince(Instant.now().minus(RECENT_WINDOW)),
                resumeRepository.count(),
                analysisRepository.count(),
                matchRepository.count(),
                interviewRepository.count(),
                jobRepository.countByStatus(JobStatus.QUEUED),
                jobRepository.countByStatus(JobStatus.RUNNING),
                jobRepository.countByStatus(JobStatus.FAILED));
    }

    /**
     * @param query free text matched against email and name; null or blank lists everyone
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.UserRow> users(String query, Pageable pageable) {
        String term = query == null ? "" : query.strip();
        return PageResponse.from(userRepository.search(term, pageable), AdminDtos.UserRow::from);
    }

    /**
     * Suspends or reinstates an account.
     *
     * @param actingAdminId the administrator making the change, so they cannot
     *                      lock themselves out of the console
     * @throws BusinessRuleViolationException if an admin targets their own account
     */
    @Transactional
    public AdminDtos.UserRow updateStatus(UUID userId, UUID actingAdminId,
                                          AdminDtos.UpdateStatus.Action action) {
        if (userId.equals(actingAdminId)) {
            throw new BusinessRuleViolationException(
                    "You cannot change the status of your own account.");
        }

        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User"));

        switch (action) {
            case SUSPEND -> user.suspend();
            case REACTIVATE -> user.reactivate();
            default -> throw new BusinessRuleViolationException("Unsupported action");
        }

        log.info("Admin {} applied {} to user {}", actingAdminId, action, userId);
        return AdminDtos.UserRow.from(user);
    }
}
