package com.careerpilot.resume.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Permanently removes resumes past their retention window.
 *
 * <p>This is how NFR-PRIV-01 is actually satisfied. Soft delete alone is not
 * deletion — it hides a row while keeping the file, the filename, and the
 * content indefinitely. Without this job the product would tell users their
 * resume was deleted while retaining it forever, which is both a broken promise
 * and a growing storage bill.
 *
 * <p>Separated from {@code ResumeService} so the schedule is visible in one
 * place and the purge logic stays directly callable from a test without waiting
 * for a trigger.
 *
 * <p><strong>Not safe for multiple replicas as written.</strong> Every instance
 * would run this on its own timer, so with two containers the purge runs twice
 * — harmless here, because deleting an already-deleted file is a no-op and the
 * row delete is idempotent, but it is wasted work and it would not stay harmless
 * for a job with side effects. The job table introduced in Phase 6 provides
 * {@code FOR UPDATE SKIP LOCKED} leader election; this should move onto it when
 * a second replica is added. Recorded here rather than discovered then.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class ResumePurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumePurgeScheduler.class);

    private final ResumeService resumeService;

    public ResumePurgeScheduler(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * Runs nightly at 03:15 UTC.
     *
     * <p>Off the hour deliberately. Scheduling on the hour puts this in company
     * with every other cron on the machine and with the provider's own
     * maintenance windows; an odd minute avoids the pile-up.
     *
     * <p>Exceptions are caught rather than allowed to propagate. An escaping
     * exception from a {@code @Scheduled} method is logged by Spring but the
     * schedule continues, so the practical difference is only how the failure
     * reads in the log — and a message naming this job is worth more at 3am than
     * a bare stack trace.
     */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    public void purgeDeletedResumes() {
        try {
            int purged = resumeService.purgeDeleted();
            if (purged > 0) {
                log.info("Retention purge removed {} resume(s)", purged);
            }
        } catch (RuntimeException e) {
            log.error("Retention purge failed; it will run again tomorrow", e);
        }
    }
}
