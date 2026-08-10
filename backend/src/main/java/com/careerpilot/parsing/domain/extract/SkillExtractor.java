package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.DocumentLine;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds skills in a parsed resume.
 *
 * <p>Searches the whole document rather than only the skills section. A
 * candidate who writes "built a payment service in Java with Spring Boot" under
 * a job has demonstrated Java more convincingly than one who lists it — and a
 * resume with no skills section at all is common enough that restricting the
 * search there would produce empty results for real people.
 *
 * <p>Where a skill was found changes how much it is trusted, though, and
 * ordinary-English skill names ("C", "Go", "Excel") are only matched inside a
 * skills section at all. See {@link SkillLexicon} for that trade.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class SkillExtractor {

    /** Listed under a skills heading — the candidate is claiming it outright. */
    private static final int CONFIDENCE_IN_SKILLS_SECTION = 95;

    /** An ordinary-English name under a skills heading; the heading disambiguates. */
    private static final int CONFIDENCE_AMBIGUOUS_IN_SKILLS_SECTION = 80;

    /** Mentioned in experience or a project — evidenced, but incidental. */
    private static final int CONFIDENCE_ELSEWHERE = 75;

    /** Found in a document with no recognised structure at all. */
    private static final int CONFIDENCE_UNSTRUCTURED = 60;

    private SkillExtractor() {
    }

    /**
     * Extracts every skill mentioned in the document.
     *
     * <p>A skill appears once in the result however many times it is written.
     * Where the same skill is found in several places, the highest-confidence
     * occurrence wins and its line pointer is the one kept — so the evidence
     * quoted back to the user is the strongest mention, not the first.
     *
     * @param model    the normalised document
     * @param sections the segmentation of that document
     * @return detected skills, ordered by first appearance
     */
    public static List<DetectedSkill> extract(LineModel model, List<ResumeSection> sections) {
        if (model == null || model.isEmpty() || sections == null || sections.isEmpty()) {
            return List.of();
        }

        SectionType[] lineSections = mapLinesToSections(model, sections);
        Map<String, DetectedSkill> best = new LinkedHashMap<>();

        for (DocumentLine line : model.lines()) {
            if (line.isBlank()) {
                continue;
            }

            SectionType section = lineSections[line.index()];
            boolean inSkillsSection = section == SectionType.SKILLS;

            // An unstructured document has no skills section to disambiguate
            // with, so ordinary-English names stay excluded there too.
            for (SkillLexicon.Hit hit : SkillLexicon.findIn(line.text(), inSkillsSection)) {
                int confidence = confidenceFor(hit.entry(), section);
                String verbatim = line.text().substring(hit.start(), hit.end());

                DetectedSkill candidate = new DetectedSkill(
                        verbatim, hit.entry().canonical(), hit.entry().category(),
                        confidence, line.index(), line.index());

                best.merge(hit.entry().canonical(), candidate,
                        (existing, incoming) ->
                                incoming.confidence() > existing.confidence() ? incoming : existing);
            }
        }

        return List.copyOf(new ArrayList<>(best.values()));
    }

    private static int confidenceFor(SkillLexicon.Entry entry, SectionType section) {
        if (section == SectionType.SKILLS) {
            return entry.ambiguous()
                    ? CONFIDENCE_AMBIGUOUS_IN_SKILLS_SECTION
                    : CONFIDENCE_IN_SKILLS_SECTION;
        }
        return section == SectionType.UNKNOWN ? CONFIDENCE_UNSTRUCTURED : CONFIDENCE_ELSEWHERE;
    }

    /**
     * Builds a line-index to section-type lookup.
     *
     * <p>Sections cover the document contiguously, so this is a fill rather than
     * a search — and it turns a per-line scan of the section list into an array
     * read, which matters on a document with many sections.
     */
    private static SectionType[] mapLinesToSections(LineModel model, List<ResumeSection> sections) {
        SectionType[] lineSections = new SectionType[model.size()];
        java.util.Arrays.fill(lineSections, SectionType.UNKNOWN);

        for (ResumeSection section : sections) {
            int from = Math.max(0, section.startLine());
            int to = Math.min(model.size() - 1, section.endLine());
            for (int i = from; i <= to; i++) {
                lineSections[i] = section.type();
            }
            // The heading line itself belongs to its own section, so that a
            // heading like "SKILLS: Java, Python" is read as skills content.
            if (section.hasHeading() && section.headingLine() < lineSections.length) {
                lineSections[section.headingLine()] = section.type();
            }
        }

        return lineSections;
    }
}
