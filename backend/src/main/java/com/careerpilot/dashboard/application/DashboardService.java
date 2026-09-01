package com.careerpilot.dashboard.application;

import com.careerpilot.ats.application.AtsAnalysisService;
import com.careerpilot.ats.application.dto.AtsAnalysisResponse;
import com.careerpilot.dashboard.application.dto.DashboardResponse;
import com.careerpilot.interview.application.InterviewService;
import com.careerpilot.matching.application.JobMatchingService;
import com.careerpilot.resume.application.ResumeService;
import com.careerpilot.resume.application.dto.ResumeResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The one call the signed-in home screen makes.
 *
 * <p>Composed from the other modules' application services rather than from
 * their tables. A dashboard is a view, and a view that queries six repositories
 * directly is six chances for it to disagree with the pages it links to.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class DashboardService {

    /** How many resumes the home screen shows before "see all". */
    private static final int RECENT_RESUMES = 5;

    private final ResumeService resumeService;
    private final AtsAnalysisService atsService;
    private final JobMatchingService matchingService;
    private final InterviewService interviewService;

    public DashboardService(ResumeService resumeService,
                            AtsAnalysisService atsService,
                            JobMatchingService matchingService,
                            InterviewService interviewService) {
        this.resumeService = resumeService;
        this.atsService = atsService;
        this.matchingService = matchingService;
        this.interviewService = interviewService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse forUser(UUID userId) {
        List<ResumeResponse> resumes = resumeService.list(userId,
                PageRequest.of(0, RECENT_RESUMES, Sort.by(Sort.Direction.DESC, "createdAt")))
                .content();

        // The primary resume is what the product's headline number is about. If
        // none is flagged, the most recent upload is the honest stand-in.
        ResumeResponse focus = resumes.stream()
                .filter(ResumeResponse::primary)
                .findFirst()
                .orElse(resumes.isEmpty() ? null : resumes.get(0));

        AtsAnalysisResponse latestAnalysis = null;
        if (focus != null) {
            try {
                latestAnalysis = atsService.getLatest(focus.id(), userId);
            } catch (RuntimeException e) {
                // Not analysed yet. The dashboard's job is to say so and offer
                // the button, not to fail.
                latestAnalysis = null;
            }
        }

        return new DashboardResponse(
                resumes,
                focus,
                latestAnalysis == null ? null : new DashboardResponse.ScoreSummary(
                        latestAnalysis.id(),
                        latestAnalysis.overallScore(),
                        latestAnalysis.band(),
                        latestAnalysis.bandLabel(),
                        latestAnalysis.bandSummary(),
                        latestAnalysis.problemCount(),
                        latestAnalysis.createdAt()),
                new DashboardResponse.Counts(
                        resumeService.count(userId),
                        matchingService.countPostings(userId),
                        matchingService.countMatches(userId),
                        interviewService.countSessions(userId)),
                interviewService.averageScore(userId));
    }
}
