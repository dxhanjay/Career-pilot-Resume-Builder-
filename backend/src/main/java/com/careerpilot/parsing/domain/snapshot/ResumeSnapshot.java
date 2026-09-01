package com.careerpilot.parsing.domain.snapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Everything the rubric is allowed to look at, in a shape it owns.
 *
 * <p>Deliberately separate from the parsing module's HTTP DTOs. A DTO is
 * shaped by what a client needs to render; this is shaped by what a rule engine
 * needs to reason about, and the two drift apart the moment either changes.
 * Being a plain record also means every rule stays testable from a literal
 * rather than from a database fixture.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record ResumeSnapshot(
        List<String> lines,
        List<SectionView> sections,
        ContactView contact,
        List<SkillView> skills,
        List<EducationView> education,
        List<ExperienceView> experience,
        Set<String> parseWarningCodes,
        Integer pageCount,
        Integer wordCount,
        Integer charCount,
        String mimeType,
        String filename
) {

    public ResumeSnapshot {
        lines = lines == null ? List.of() : List.copyOf(lines);
        sections = sections == null ? List.of() : List.copyOf(sections);
        skills = skills == null ? List.of() : List.copyOf(skills);
        education = education == null ? List.of() : List.copyOf(education);
        experience = experience == null ? List.of() : List.copyOf(experience);
        parseWarningCodes = parseWarningCodes == null ? Set.of() : Set.copyOf(parseWarningCodes);
    }

    /** The text of a line, or empty if the index is out of range. */
    public String lineAt(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index) : "";
    }

    /** Joined text of an inclusive line range, clamped to the document. */
    public String textOf(int startInclusive, int endInclusive) {
        int from = Math.max(0, startInclusive);
        int to = Math.min(lines.size() - 1, endInclusive);
        if (from > to) {
            return "";
        }
        return String.join("\n", lines.subList(from, to + 1)).strip();
    }

    public boolean hasSection(String type) {
        return sections.stream().anyMatch(section -> section.type().equals(type));
    }

    public SectionView section(String type) {
        return sections.stream()
                .filter(section -> section.type().equals(type))
                .findFirst()
                .orElse(null);
    }

    public record SectionView(
            String type,
            String headingText,
            int headingLine,
            int startLine,
            int endLine,
            int confidence,
            boolean core
    ) {
    }

    public record ContactView(
            String fullName,
            String email,
            String phone,
            String location,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl,
            int confidence,
            Integer lineStart,
            Integer lineEnd
    ) {
        public static ContactView none() {
            return new ContactView(null, null, null, null, null, null, null, 0, null, null);
        }
    }

    public record SkillView(
            String name,
            String normalizedName,
            String category,
            int confidence,
            Integer lineStart
    ) {
    }

    public record EducationView(
            String institution,
            String degree,
            String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            Integer lineStart,
            Integer lineEnd
    ) {
    }

    public record ExperienceView(
            String company,
            String jobTitle,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            String description,
            Integer lineStart,
            Integer lineEnd
    ) {
    }
}
