package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.DocumentLine;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts jobs and internships from a resume's experience section.
 *
 * <p>Company and title are the hardest fields in the whole parser. Nothing
 * marks them: "Google" and "Software Engineer" are both capitalised noun
 * phrases, and templates order them either way. The only reliable signal is
 * that job titles are built from a small, closed vocabulary of role words while
 * company names are not — so a header line naming a role is the title, and a
 * header line that does not is the company.
 *
 * <p>That resolves the common layouts, including the single-line form
 * "Software Engineer, Google | 2020 – 2022". It does not resolve an employer
 * called "The Engineer Group", which will be read as a title. Such an entry
 * scores lower for having no separate company, which is the honest outcome.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class ExperienceExtractor {

    /**
     * Words that mark a phrase as a job title.
     *
     * <p>Whole-token matched, so "lead" does not fire inside "leader" and
     * "intern" does not fire inside "internal".
     */
    private static final List<String> ROLE_WORDS = List.of(
            "engineer", "developer", "programmer", "intern", "internship", "trainee",
            "analyst", "manager", "designer", "consultant", "architect", "scientist",
            "administrator", "specialist", "associate", "assistant", "officer",
            "executive", "director", "lead", "head", "president", "founder",
            "cofounder", "co-founder", "freelancer", "contractor", "tester",
            "researcher", "sde", "swe", "qa", "devops", "fellow", "volunteer",
            "coordinator", "supervisor", "technician", "strategist", "apprentice");

    /** Suffixes that mark a phrase as an organisation. */
    private static final List<String> COMPANY_MARKERS = List.of(
            "inc", "inc.", "ltd", "ltd.", "llc", "llp", "pvt", "pvt.", "private",
            "limited", "corp", "corp.", "corporation", "company", "co.",
            "technologies", "technology", "solutions", "systems", "labs", "laboratories",
            "services", "consulting", "group", "holdings", "ventures", "studio",
            "software", "industries", "enterprises", "foundation", "gmbh");

    /** Separators that pack several fields onto one header line. */
    private static final String HEADER_SPLIT =
            "\\s*[|•·]\\s*|\\s+[\\u2013\\u2014]\\s+|\\s+-\\s+|,\\s+|\\s+at\\s+|\\s+@\\s+";

    private static final int BASE_CONFIDENCE = 35;
    private static final int WEIGHT_TITLE = 25;
    private static final int WEIGHT_COMPANY = 25;
    private static final int WEIGHT_DATE = 10;
    private static final int WEIGHT_DESCRIPTION = 5;
    private static final int MAX_CONFIDENCE = 95;

    private ExperienceExtractor() {
    }

    /**
     * Extracts every role in the document.
     *
     * @param model    the normalised document
     * @param sections the segmentation of that document
     * @return the roles found, in document order
     */
    public static List<ExperienceEntry> extract(LineModel model, List<ResumeSection> sections) {
        if (model == null || sections == null) {
            return List.of();
        }

        List<ExperienceEntry> entries = new ArrayList<>();

        for (ResumeSection section : sections) {
            if (section.type() != SectionType.EXPERIENCE) {
                continue;
            }
            for (SectionEntry entry : EntrySplitter.split(model, section)) {
                ExperienceEntry experience = parseEntry(entry);
                if (!experience.isEmpty()) {
                    entries.add(experience);
                }
            }
        }

        return List.copyOf(entries);
    }

    private static ExperienceEntry parseEntry(SectionEntry entry) {
        DateRange dates = DateRange.none();
        String jobTitle = null;
        String company = null;

        for (DocumentLine line : entry.headerLines()) {
            String text = line.stripped();

            if (dates.isEmpty()) {
                DateRange found = DateRangeParser.parse(text);
                if (!found.isEmpty()) {
                    dates = found;
                }
            }

            String withoutDates = DateRangeParser.stripDates(text);
            if (withoutDates == null || withoutDates.isBlank()) {
                continue;
            }

            for (String part : withoutDates.split(HEADER_SPLIT)) {
                String candidate = clean(part);
                if (candidate.isEmpty()) {
                    continue;
                }
                if (namesRole(candidate)) {
                    if (jobTitle == null) {
                        jobTitle = candidate;
                    }
                } else if (company == null) {
                    company = candidate;
                }
            }
        }

        String description = entry.description();

        int confidence = BASE_CONFIDENCE
                + (jobTitle != null ? WEIGHT_TITLE : 0)
                + (company != null ? WEIGHT_COMPANY : 0)
                + (!dates.isEmpty() ? WEIGHT_DATE : 0)
                + (description != null ? WEIGHT_DESCRIPTION : 0);

        return new ExperienceEntry(
                company, jobTitle,
                dates.start(), dates.end(), dates.current(),
                description,
                Math.min(MAX_CONFIDENCE, confidence),
                entry.startLine(), entry.endLine());
    }

    /**
     * Whether a phrase names a role rather than an organisation.
     *
     * <p>Decided by the <em>last</em> significant token, not by whether a marker
     * appears anywhere in the phrase. English noun phrases are head-final, so
     * the last word is the thing and everything before it modifies that thing:
     * "Software Engineer" is an engineer, "Engineer Solutions Pvt Ltd" is a Ltd.
     *
     * <p>Searching the whole phrase instead gets both wrong, because the words
     * overlap heavily — "software", "systems", "technology" and "solutions" are
     * all common in company names <em>and</em> in the most common job titles in
     * the industry. Scanning from the end resolves "Systems Engineer" and "Acme
     * Systems" correctly with one rule.
     */
    private static boolean namesRole(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }
        String[] words = phrase.toLowerCase(Locale.ROOT).split("\\s+");

        for (int i = words.length - 1; i >= 0; i--) {
            String word = words[i].replaceAll("^[^\\p{L}\\d]+|[^\\p{L}\\d.]+$", "");
            if (word.isEmpty()) {
                continue;
            }
            if (ROLE_WORDS.contains(word)) {
                return true;
            }
            if (COMPANY_MARKERS.contains(word)) {
                return false;
            }
        }

        // Neither: a bare name like "Google" or "Infosys". Treated as a company,
        // because an unrecognised proper noun in a job header is far more often
        // an employer than a title.
        return false;
    }

    private static String clean(String part) {
        return part == null
                ? ""
                : part.replaceAll("^[\\s,;:|\\-–—]+", "")
                .replaceAll("[\\s,;:|\\-–—]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
