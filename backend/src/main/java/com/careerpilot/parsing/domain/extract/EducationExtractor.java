package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.DocumentLine;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts qualifications from a resume's education section.
 *
 * <p>Works only inside {@link SectionType#EDUCATION}. Unlike skills, a degree
 * mentioned in an experience bullet is not a qualification the candidate holds
 * — "worked alongside PhD researchers" would otherwise award them a doctorate.
 *
 * <p>Each field is found by its own marker rather than by position, because
 * ordering varies: some templates put the institution first, some the degree,
 * some combine them on one line. Anything not found stays null, and the
 * confidence reports how much was recognised.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class EducationExtractor {

    /** Words that identify a line as naming an institution. */
    private static final List<String> INSTITUTION_MARKERS = List.of(
            "university", "college", "institute", "institution", "school", "academy",
            "polytechnic", "vidyalaya", "vidhyalaya", "iit", "nit", "iiit", "bits",
            "campus", "faculty");

    /**
     * Qualification names, longest first so "b.tech" is preferred over "b.t".
     *
     * <p>Bare "be", "ba", "ma" and "ms" are excluded deliberately: they are
     * ordinary English words and pronouns, and whole-token matching does not
     * save them — "ma" appears in "MA, USA" and "be" in any sentence. Their
     * dotted forms are included, which is how resumes actually write them.
     */
    private static final List<String> DEGREES = List.of(
            "bachelor of technology", "bachelor of engineering", "bachelor of science",
            "bachelor of commerce", "bachelor of arts", "master of technology",
            "master of science", "master of business administration", "master of arts",
            "higher secondary", "senior secondary", "secondary school",
            "b.tech", "btech", "b tech", "b.e.", "b.e", "b.sc", "bsc", "b.com", "bcom",
            "b.a.", "bca", "m.tech", "mtech", "m tech", "m.sc", "msc", "m.com",
            "mca", "mba", "ph.d", "phd", "doctorate", "diploma", "bachelor", "master",
            "intermediate", "hsc", "ssc", "class 12", "class 10", "12th", "10th");

    /** "CGPA: 8.7", "GPA 3.8/4.0", "85.4%", "First Class". */
    private static final Pattern GRADE = Pattern.compile(
            "(?i)\\b(?:cgpa|gpa|sgpa|percentage|marks|score)\\b\\s*[:\\-]?\\s*"
                    + "(\\d{1,2}(?:\\.\\d{1,2})?(?:\\s*/\\s*\\d{1,2}(?:\\.\\d{1,2})?)?)");

    private static final Pattern PERCENTAGE = Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{1,2})?)\\s*%");

    /** "B.Tech in Computer Science", "B.Tech, Computer Science". */
    private static final Pattern FIELD_AFTER_DEGREE = Pattern.compile(
            "(?i)\\b(?:in|of|,)\\s+([\\p{L}][\\p{L}\\s&.-]{2,60})");

    private static final int BASE_CONFIDENCE = 35;
    private static final int WEIGHT_INSTITUTION = 25;
    private static final int WEIGHT_DEGREE = 25;
    private static final int WEIGHT_DATE = 10;
    private static final int WEIGHT_GRADE = 5;
    private static final int MAX_CONFIDENCE = 95;

    private EducationExtractor() {
    }

    /**
     * Extracts every qualification in the document.
     *
     * @param model    the normalised document
     * @param sections the segmentation of that document
     * @return the qualifications found, in document order
     */
    public static List<EducationEntry> extract(LineModel model, List<ResumeSection> sections) {
        if (model == null || sections == null) {
            return List.of();
        }

        List<EducationEntry> entries = new ArrayList<>();

        for (ResumeSection section : sections) {
            if (section.type() != SectionType.EDUCATION) {
                continue;
            }
            for (SectionEntry entry : EntrySplitter.split(model, section)) {
                EducationEntry education = parseEntry(entry);
                if (!education.isEmpty()) {
                    entries.add(education);
                }
            }
        }

        return List.copyOf(entries);
    }

    private static EducationEntry parseEntry(SectionEntry entry) {
        String institution = null;
        String degree = null;
        String fieldOfStudy = null;
        String grade = null;
        DateRange dates = DateRange.none();

        for (DocumentLine line : entry.lines()) {
            String text = line.stripped();
            if (text.isEmpty()) {
                continue;
            }

            if (dates.isEmpty()) {
                DateRange found = DateRangeParser.parse(text);
                if (!found.isEmpty()) {
                    dates = found;
                }
            }

            if (grade == null) {
                grade = findGrade(text);
            }

            String withoutDates = DateRangeParser.stripDates(text);

            if (degree == null) {
                String matched = TextTokens.firstToken(withoutDates, DEGREES);
                if (matched != null) {
                    degree = verbatim(withoutDates, matched);
                    fieldOfStudy = findField(withoutDates, matched);
                }
            }

            if (institution == null && TextTokens.firstToken(withoutDates, INSTITUTION_MARKERS) != null) {
                institution = trimTrailingPunctuation(withoutDates);
            }
        }

        int confidence = BASE_CONFIDENCE
                + (institution != null ? WEIGHT_INSTITUTION : 0)
                + (degree != null ? WEIGHT_DEGREE : 0)
                + (!dates.isEmpty() ? WEIGHT_DATE : 0)
                + (grade != null ? WEIGHT_GRADE : 0);

        return new EducationEntry(
                institution, degree, fieldOfStudy,
                dates.start(), dates.end(), grade,
                Math.min(MAX_CONFIDENCE, confidence),
                entry.startLine(), entry.endLine());
    }

    /**
     * Recovers the degree as the candidate capitalised it.
     *
     * <p>Matching is lowercase, but "b.tech" is not what they wrote. Showing a
     * lowercased degree back on the parse-review screen looks like a bug.
     */
    private static String verbatim(String text, String lowercaseMatch) {
        int at = TextTokens.indexOfToken(text.toLowerCase(Locale.ROOT), lowercaseMatch);
        return at < 0 ? lowercaseMatch : text.substring(at, at + lowercaseMatch.length());
    }

    /**
     * Finds the subject named after the degree.
     *
     * <p>Searches only the text following the degree token. Searching the whole
     * line would pick up "in Bengaluru" from an address on the same line.
     */
    private static String findField(String text, String degreeMatch) {
        int at = TextTokens.indexOfToken(text.toLowerCase(Locale.ROOT), degreeMatch);
        if (at < 0) {
            return null;
        }
        String tail = text.substring(at + degreeMatch.length());
        Matcher matcher = FIELD_AFTER_DEGREE.matcher(tail);
        if (!matcher.find()) {
            return null;
        }
        String field = trimTrailingPunctuation(matcher.group(1));
        // An institution marker means this is the school, not the subject.
        if (TextTokens.firstToken(field, INSTITUTION_MARKERS) != null) {
            return null;
        }
        return field.isBlank() ? null : field;
    }

    private static String findGrade(String text) {
        Matcher matcher = GRADE.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", "");
        }
        Matcher percentage = PERCENTAGE.matcher(text);
        if (percentage.find()) {
            return percentage.group(1) + "%";
        }
        return null;
    }

    private static String trimTrailingPunctuation(String text) {
        return text == null ? null : text.replaceAll("[\\s,;:|\\-–—]+$", "").trim();
    }
}
