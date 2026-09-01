package com.careerpilot.parsing.application;

import com.careerpilot.parsing.application.dto.ParseResultResponse;
import com.careerpilot.parsing.application.dto.StructuredParseResponse;
import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the read model every downstream rule engine works from.
 *
 * <p>One mapping, used by ATS scoring and job matching alike. Two copies of this
 * translation is how the same resume ends up with a skill the ATS report counted
 * and the match report did not.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class ResumeSnapshotProvider {

    private final ResumeParsingService parsingService;

    public ResumeSnapshotProvider(ResumeParsingService parsingService) {
        this.parsingService = parsingService;
    }

    /**
     * @throws com.careerpilot.common.exception.BusinessRuleViolationException
     *         if the resume has no successful parse
     */
    @Transactional(readOnly = true)
    public Snapshot forResume(UUID resumeId, UUID userId) {
        StructuredParseResponse structured = parsingService.getStructured(resumeId, userId);
        ParseResultResponse parse = parsingService.getResult(resumeId, userId);

        Set<String> warningCodes = new HashSet<>();
        if (parse.warnings() != null) {
            parse.warnings().forEach(warning -> warningCodes.add(warning.code()));
        }

        StructuredParseResponse.Contact contact = structured.contact();

        ResumeSnapshot snapshot = new ResumeSnapshot(
                structured.lines(),
                structured.sections().stream()
                        .map(section -> new ResumeSnapshot.SectionView(
                                section.type(), section.headingText(), section.headingLine(),
                                section.startLine(), section.endLine(), section.confidence(),
                                section.core()))
                        .toList(),
                contact == null
                        ? ResumeSnapshot.ContactView.none()
                        : new ResumeSnapshot.ContactView(
                                contact.fullName(), contact.email(), contact.phone(),
                                contact.location(), contact.linkedinUrl(), contact.githubUrl(),
                                contact.portfolioUrl(), contact.confidence(),
                                contact.sourceLineStart(), contact.sourceLineEnd()),
                structured.skills().stream()
                        .map(skill -> new ResumeSnapshot.SkillView(
                                skill.name(), skill.normalizedName(), skill.category(),
                                skill.confidence(), skill.sourceLineStart()))
                        .toList(),
                structured.education().stream()
                        .map(education -> new ResumeSnapshot.EducationView(
                                education.institution(), education.degree(),
                                education.fieldOfStudy(), education.startDate(),
                                education.endDate(), education.sourceLineStart(),
                                education.sourceLineEnd()))
                        .toList(),
                structured.experience().stream()
                        .map(experience -> new ResumeSnapshot.ExperienceView(
                                experience.company(), experience.jobTitle(),
                                experience.startDate(), experience.endDate(),
                                experience.current(), experience.description(),
                                experience.sourceLineStart(), experience.sourceLineEnd()))
                        .toList(),
                warningCodes,
                parse.pageCount() == null ? null : parse.pageCount().intValue(),
                parse.wordCount(),
                parse.charCount(),
                null,
                null);

        return new Snapshot(structured.parseId(), snapshot);
    }

    /**
     * The snapshot together with the parse it came from, so a caller storing a
     * result can record which extraction it was computed against.
     */
    public record Snapshot(UUID parseId, ResumeSnapshot resume) {
    }
}
