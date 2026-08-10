package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.DocumentLine;
import com.careerpilot.parsing.domain.section.HeadingLexicon;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the contact block from a parsed resume.
 *
 * <p>Email, phone, and links are found by pattern and are effectively certain.
 * The name is not: nothing in a resume marks it, and it is identified by being
 * the first line that looks like a person's name rather than anything else. The
 * heuristic below is deliberately conservative — a wrong name shown back on the
 * "here's what the machine saw" screen destroys trust in every other number on
 * the page, so no name is preferable to a confident wrong one.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class ContactExtractor {

    /** RFC-shaped enough for resumes; deliberately not a full RFC 5322 parser. */
    private static final Pattern EMAIL =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]*[a-zA-Z]{2,}");

    /** Optional country code, then 9–14 more digits with any separators. */
    private static final Pattern PHONE =
            Pattern.compile("(\\+?\\d{1,3}[\\s.-]?)?(\\(?\\d{2,5}\\)?[\\s.-]?){2,5}\\d{2,5}");

    private static final Pattern LINKEDIN =
            Pattern.compile("(?i)(?:https?://)?(?:www\\.)?linkedin\\.com/[\\w/%-]+");

    private static final Pattern GITHUB =
            Pattern.compile("(?i)(?:https?://)?(?:www\\.)?github\\.com/[\\w/.-]+");

    private static final Pattern GENERIC_URL =
            Pattern.compile("(?i)(?:https?://|www\\.)[\\w.-]+\\.[a-z]{2,}(?:/[\\w./%-]*)?");

    /** "Bengaluru, Karnataka" or "Pune, Maharashtra, India". */
    private static final Pattern LOCATION =
            Pattern.compile("^[\\p{L}][\\p{L}.'\\s-]{1,40}(,\\s*[\\p{L}][\\p{L}.'\\s-]{1,40}){1,2}$");

    /** How far into a resume with no contact section to keep looking. */
    private static final int FALLBACK_SCAN_LINES = 8;

    /**
     * Document titles that templates print above the name.
     *
     * <p>These pass every structural test a name passes — two capitalised words,
     * no digits, no contact details, top of the page — and the heading lexicon
     * does not catch them because they name a document, not a section. Without
     * this list the most common Indian and European resume templates all report
     * "Curriculum Vitae" as the candidate's name.
     */
    private static final java.util.Set<String> NOT_A_NAME = java.util.Set.of(
            "curriculum vitae", "curriculum vitæ", "resume", "résumé", "cv",
            "biodata", "bio data", "personal resume", "my resume",
            "student resume", "professional resume");

    private static final int MIN_PHONE_DIGITS = 9;
    private static final int MAX_PHONE_DIGITS = 15;

    private ContactExtractor() {
    }

    /**
     * Extracts the contact block.
     *
     * <p>Reads the {@link SectionType#CONTACT} section when segmentation found
     * one, and otherwise the opening lines of the document. Email is the one
     * exception: if none is present in the block it is searched for across the
     * whole resume, because an address in a footer is still the candidate's
     * address and the pattern cannot produce a false positive.
     *
     * @param model    the normalised document
     * @param sections the segmentation of that document
     * @return what was found, never {@code null}
     */
    public static ContactDetails extract(LineModel model, List<ResumeSection> sections) {
        if (model == null || model.isEmpty()) {
            return ContactDetails.none();
        }

        ResumeSection block = findContactSection(sections);
        int start = block != null ? Math.max(0, block.startLine()) : 0;
        int end = block != null
                ? Math.min(model.size() - 1, block.endLine())
                : Math.min(model.size() - 1, FALLBACK_SCAN_LINES - 1);
        int blockConfidence = block != null ? block.confidence() : 40;

        List<DocumentLine> lines = model.range(start, end);

        String email = firstMatch(lines, EMAIL).orElseGet(
                () -> firstMatch(model.lines(), EMAIL).orElse(null));
        String phone = findPhone(lines);
        String linkedin = firstMatch(lines, LINKEDIN).orElse(null);
        String github = firstMatch(lines, GITHUB).orElse(null);
        String portfolio = findPortfolio(lines, linkedin, github);
        String location = findLocation(lines);

        NameGuess name = findName(lines);

        return new ContactDetails(
                name.value(), name.value() == null ? null : name.confidence(),
                email, phone, location, linkedin, github, portfolio,
                blockConfidence, start, end);
    }

    private static ResumeSection findContactSection(List<ResumeSection> sections) {
        if (sections == null) {
            return null;
        }
        return sections.stream()
                .filter(section -> section.type() == SectionType.CONTACT)
                .findFirst()
                .orElse(null);
    }

    private static Optional<String> firstMatch(List<DocumentLine> lines, Pattern pattern) {
        for (DocumentLine line : lines) {
            Matcher matcher = pattern.matcher(line.text());
            if (matcher.find()) {
                return Optional.of(matcher.group().trim());
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a phone number, rejecting digit runs that are not one.
     *
     * <p>The pattern alone matches date ranges and identifiers, so the digit
     * count is checked separately: fewer than nine digits is a year range or a
     * postcode, more than fifteen is not a phone number under E.164.
     */
    private static String findPhone(List<DocumentLine> lines) {
        for (DocumentLine line : lines) {
            Matcher matcher = PHONE.matcher(line.text());
            while (matcher.find()) {
                String candidate = matcher.group().trim();
                long digits = candidate.chars().filter(Character::isDigit).count();
                if (digits >= MIN_PHONE_DIGITS && digits <= MAX_PHONE_DIGITS) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Any personal link that is not already captured as LinkedIn or GitHub. */
    private static String findPortfolio(List<DocumentLine> lines, String linkedin, String github) {
        for (DocumentLine line : lines) {
            Matcher matcher = GENERIC_URL.matcher(line.text());
            while (matcher.find()) {
                String url = matcher.group().trim();
                String lower = url.toLowerCase(Locale.ROOT);
                if (lower.contains("linkedin.com") || lower.contains("github.com")) {
                    continue;
                }
                if (url.equals(linkedin) || url.equals(github)) {
                    continue;
                }
                return url;
            }
        }
        return null;
    }

    /**
     * Identifies a location line.
     *
     * <p>Requires the "City, Region" shape rather than guessing at single words,
     * and skips anything carrying contact details or digits. Without a gazetteer
     * this is the most that can be claimed honestly, so a resume that writes its
     * location any other way yields nothing here.
     */
    private static String findLocation(List<DocumentLine> lines) {
        for (DocumentLine line : lines) {
            String text = line.stripped();
            if (text.isEmpty() || line.hasContactDetails()) {
                continue;
            }
            if (text.chars().anyMatch(Character::isDigit)) {
                continue;
            }
            if (LOCATION.matcher(text).matches() && line.wordCount() <= 6) {
                return text;
            }
        }
        return null;
    }

    /**
     * Guesses which line is the candidate's name.
     *
     * <p>Scored rather than taken positionally, because the first line of a
     * resume is not reliably the name — templates open with "CURRICULUM VITAE",
     * a job title, or a decorative rule. A line qualifies only if it is short,
     * alphabetic, carries no contact details, and does not name a section.
     */
    private static NameGuess findName(List<DocumentLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            DocumentLine line = lines.get(i);
            String text = line.stripped();

            if (text.isEmpty() || line.hasContactDetails() || line.isBullet()) {
                continue;
            }
            if (line.wordCount() < 2 || line.wordCount() > 4) {
                continue;
            }
            if (text.chars().anyMatch(Character::isDigit)) {
                continue;
            }
            // "Professional Summary" names a section; "Curriculum Vitae" names
            // the document. Neither is a person.
            if (HeadingLexicon.resolve(text).isPresent()) {
                continue;
            }
            if (NOT_A_NAME.contains(text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\s]", "")
                    .trim().replaceAll("\\s+", " "))) {
                continue;
            }
            // Letters, spaces, and the punctuation that appears in real names.
            if (!text.matches("[\\p{L}][\\p{L}.'\\s-]*")) {
                continue;
            }

            int confidence = 55;
            if (i == 0) {
                confidence += 20;
            }
            if (line.isAllCaps() || line.isTitleCase()) {
                confidence += 15;
            }
            if (line.wordCount() <= 3) {
                confidence += 5;
            }

            return new NameGuess(text, Math.min(95, confidence));
        }

        return new NameGuess(null, 0);
    }

    private record NameGuess(String value, int confidence) {
    }
}
