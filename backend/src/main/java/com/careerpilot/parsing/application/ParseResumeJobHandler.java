package com.careerpilot.parsing.application;

import com.careerpilot.ats.application.AtsAnalysisService;
import com.careerpilot.jobs.application.JobHandler;
import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobType;
import com.careerpilot.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs {@link JobType#PARSE_RESUME} jobs.
 *
 * <p>Thin by design: the job engine owns claiming, retrying, and status; this
 * class owns only the mapping from "a job failed" to "should it be retried".
 * That classification is the whole reason the handler exists as a separate
 * class rather than being a lambda in the poller.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class ParseResumeJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ParseResumeJobHandler.class);

    private final ResumeParsingService parsingService;
    private final AtsAnalysisService atsAnalysisService;

    public ParseResumeJobHandler(ResumeParsingService parsingService,
                                 AtsAnalysisService atsAnalysisService) {
        this.parsingService = parsingService;
        this.atsAnalysisService = atsAnalysisService;
    }

    @Override
    public JobType handles() {
        return JobType.PARSE_RESUME;
    }

    @Override
    public UUID execute(Job job) {
        try {
            UUID parseId = parsingService.parse(job.getReferenceId());

            // Score it now, while the user is still watching the spinner. The
            // rubric is pure CPU over text this transaction just committed, so
            // it costs milliseconds and the report is ready the moment the job
            // reports success. A failure there is logged and swallowed — the
            // extracted text is valuable on its own.
            atsAnalysisService.analyzeQuietly(job.getReferenceId(), job.getUserId());

            return parseId;

        } catch (ResumeParsingService.ParseFailedException e) {
            // The service already decided. A document that no extractor can read
            // is permanent: the same bytes through the same extractors fail
            // identically, so retrying costs time and money for an outcome we
            // already know, and delays telling the user something actionable.
            throw new JobExecutionException(e.getMessage(), e, e.isTransient());

        } catch (StorageException e) {
            // The file is stored but temporarily unreachable - the provider is
            // down, or the network blipped. Genuinely worth another attempt,
            // and the only failure here that is.
            log.warn("Storage unavailable while parsing resume {}", job.getReferenceId());
            throw new JobExecutionException(
                    "Could not read the stored file; this will be retried", e, true);
        }
    }
}
