package com.careerpilot.parsing.application.dto;

import com.careerpilot.parsing.domain.ParsedContact;
import com.careerpilot.parsing.domain.ParsedEducation;
import com.careerpilot.parsing.domain.ParsedExperience;
import com.careerpilot.parsing.domain.ParsedSkill;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "Here is exactly what the machine saw."
 *
 * <p>The product's central claim. Every field carries the line range it came
 * from, so the frontend can highlight the evidence inside the raw text rather
 * than asking the user to take the extraction on trust.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "StructuredParseResponse",
        description = "Sections and entities extracted from a resume, with source line evidence")
public record StructuredParseResponse(
        UUID parseId,
        UUID resumeId,
        List<String> lines,
        List<Section> sections,
        Contact contact,
        List<Skill> skills,
        List<Education> education,
        List<Experience> experience
) {

    /** A detected block of the document, with the confidence it is what we think. */
    public record Section(
            String type,
            String displayName,
            String headingText,
            int headingLine,
            int startLine,
            int endLine,
            int confidence,
            boolean core
    ) {
        public static Section from(ResumeSection section) {
            return new Section(
                    section.type().name(),
                    section.type().displayName(),
                    section.headingText(),
                    section.headingLine(),
                    section.startLine(),
                    section.endLine(),
                    section.confidence(),
                    section.type().isCore());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Contact(
            String fullName,
            Integer nameConfidence,
            String email,
            String phone,
            String location,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl,
            int confidence,
            Integer sourceLineStart,
            Integer sourceLineEnd
    ) {
        public static Contact from(ParsedContact contact) {
            return new Contact(
                    contact.getFullName(),
                    contact.getNameConfidence() == null ? null : (int) contact.getNameConfidence(),
                    contact.getEmail(),
                    contact.getPhone(),
                    contact.getLocation(),
                    contact.getLinkedinUrl(),
                    contact.getGithubUrl(),
                    contact.getPortfolioUrl(),
                    contact.getConfidence(),
                    contact.getSourceLineStart(),
                    contact.getSourceLineEnd());
        }
    }

    public record Skill(
            UUID id,
            String name,
            String normalizedName,
            String category,
            int confidence,
            Integer sourceLineStart,
            Integer sourceLineEnd
    ) {
        public static Skill from(ParsedSkill skill) {
            return new Skill(
                    skill.getId(),
                    skill.getSkillName(),
                    skill.getNormalizedName(),
                    skill.getCategory().name(),
                    skill.getConfidence(),
                    skill.getSourceLineStart(),
                    skill.getSourceLineEnd());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Education(
            UUID id,
            String institution,
            String degree,
            String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            String grade,
            int confidence,
            Integer sourceLineStart,
            Integer sourceLineEnd
    ) {
        public static Education from(ParsedEducation education) {
            return new Education(
                    education.getId(),
                    education.getInstitution(),
                    education.getDegree(),
                    education.getFieldOfStudy(),
                    education.getStartDate(),
                    education.getEndDate(),
                    education.getGrade(),
                    education.getConfidence(),
                    education.getSourceLineStart(),
                    education.getSourceLineEnd());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Experience(
            UUID id,
            String company,
            String jobTitle,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            String description,
            int confidence,
            Integer sourceLineStart,
            Integer sourceLineEnd
    ) {
        public static Experience from(ParsedExperience experience) {
            return new Experience(
                    experience.getId(),
                    experience.getCompany(),
                    experience.getJobTitle(),
                    experience.getStartDate(),
                    experience.getEndDate(),
                    experience.isCurrent(),
                    experience.getDescription(),
                    experience.getConfidence(),
                    experience.getSourceLineStart(),
                    experience.getSourceLineEnd());
        }
    }
}
