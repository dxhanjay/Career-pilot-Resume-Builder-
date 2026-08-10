package com.careerpilot.parsing.domain.section;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps resume heading text to a {@link SectionType}.
 *
 * <p>Two tiers, and the difference between them is the point.
 *
 * <p><strong>Exact</strong> matches come from a phrase table. "Employment
 * History" is unambiguously experience, and knowing that is worth more than any
 * structural signal the line carries.
 *
 * <p><strong>Keyword</strong> matches handle the decorated variants templates
 * produce — "TECHNICAL SKILLS &amp; TOOLS", "Projects (Selected)". They are
 * scored lower because a keyword can appear in a line that is not a heading at
 * all: "Experience with distributed systems" contains "experience" and is body
 * text. The heading score in {@link SectionSegmenter} is what separates those,
 * and a weak lexicon match cannot carry a line over the threshold on its own.
 *
 * <p>Keyword order is significant. "Project Experience" contains both "project"
 * and "experience"; the table is ordered so the more specific term wins.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class HeadingLexicon {

    /** Anything that is not a letter, space, or ampersand is decoration. */
    private static final Pattern DECORATION = Pattern.compile("[^\\p{L}\\s&]");

    private static final Map<String, SectionType> EXACT = buildExact();

    /** Ordered most specific first — see the class note on "Project Experience". */
    private static final Map<String, SectionType> KEYWORDS = buildKeywords();

    private HeadingLexicon() {
    }

    /**
     * How strongly a line's text indicates a section.
     *
     * @param type      the section the heading names
     * @param exact     {@code true} for a full-phrase match, {@code false} for a keyword
     */
    public record Match(SectionType type, boolean exact) {
    }

    /**
     * Resolves heading text to a section type.
     *
     * @param headingText the candidate line, as written
     * @return the match, or empty if the text names no known section
     */
    public static Optional<Match> resolve(String headingText) {
        String canonical = canonicalise(headingText);
        if (canonical.isEmpty()) {
            return Optional.empty();
        }

        SectionType exact = EXACT.get(canonical);
        if (exact != null) {
            return Optional.of(new Match(exact, true));
        }

        for (Map.Entry<String, SectionType> entry : KEYWORDS.entrySet()) {
            if (containsWord(canonical, entry.getKey())) {
                return Optional.of(new Match(entry.getValue(), false));
            }
        }

        return Optional.empty();
    }

    /**
     * Reduces heading text to its comparable form.
     *
     * <p>Strips the decoration templates wrap headings in — bullets, rules,
     * colons, pipes, digits — lowercases, and collapses whitespace. "―― WORK
     * EXPERIENCE ――" and "Work Experience:" both become "work experience".
     *
     * @param headingText raw line text
     * @return the canonical form, possibly empty
     */
    static String canonicalise(String headingText) {
        if (headingText == null) {
            return "";
        }
        String cleaned = DECORATION.matcher(headingText).replaceAll(" ");
        return cleaned.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Whole-word containment, so "reference" does not match inside "referenced". */
    private static boolean containsWord(String canonical, String word) {
        int from = 0;
        while (true) {
            int at = canonical.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            boolean startsClean = at == 0 || canonical.charAt(at - 1) == ' ';
            int after = at + word.length();
            boolean endsClean = after == canonical.length() || canonical.charAt(after) == ' ';
            if (startsClean && endsClean) {
                return true;
            }
            from = at + 1;
        }
    }

    private static Map<String, SectionType> buildExact() {
        Map<String, SectionType> map = new LinkedHashMap<>();

        put(map, SectionType.CONTACT,
                "contact", "contact information", "contact details", "personal information",
                "personal details", "personal profile");

        put(map, SectionType.SUMMARY,
                "summary", "professional summary", "career summary", "executive summary",
                "profile", "professional profile", "about", "about me", "objective",
                "career objective", "professional objective", "personal statement",
                "summary of qualifications");

        put(map, SectionType.EDUCATION,
                "education", "academic background", "academics", "academic details",
                "academic qualifications", "educational qualifications", "qualifications",
                "education and training", "academic history", "educational background");

        put(map, SectionType.EXPERIENCE,
                "experience", "work experience", "working experience", "professional experience",
                "employment", "employment history", "employment experience", "work history",
                "career history", "professional background", "relevant experience",
                "industry experience", "internship", "internships", "internship experience",
                "industrial training", "professional experience and internships");

        put(map, SectionType.SKILLS,
                "skills", "skill set", "technical skills", "core skills", "key skills",
                "soft skills", "skills and abilities", "technical proficiencies",
                "technical expertise", "areas of expertise", "core competencies",
                "competencies", "technologies", "tech stack", "technical summary",
                "programming languages", "tools and technologies");

        put(map, SectionType.PROJECTS,
                "projects", "academic projects", "personal projects", "key projects",
                "major projects", "selected projects", "project experience",
                "projects undertaken", "notable projects", "project work");

        put(map, SectionType.CERTIFICATIONS,
                "certifications", "certification", "certificates", "licenses and certifications",
                "licences and certifications", "professional certifications",
                "courses and certifications", "training and certifications", "courses",
                "online courses");

        put(map, SectionType.ACHIEVEMENTS,
                "achievements", "accomplishments", "awards", "honors", "honours",
                "awards and achievements", "achievements and awards", "honors and awards",
                "honours and awards", "key achievements", "extracurricular achievements");

        put(map, SectionType.LANGUAGES,
                "languages", "languages known", "language proficiency", "spoken languages");

        put(map, SectionType.PUBLICATIONS,
                "publications", "research", "research papers", "papers",
                "research experience", "research and publications");

        put(map, SectionType.INTERESTS,
                "interests", "hobbies", "hobbies and interests", "interests and hobbies",
                "activities", "extracurricular activities", "volunteer experience",
                "volunteering", "positions of responsibility");

        put(map, SectionType.REFERENCES,
                "references", "referees");

        return Collections.unmodifiableMap(map);
    }

    private static Map<String, SectionType> buildKeywords() {
        Map<String, SectionType> map = new LinkedHashMap<>();
        // Specific before general: "project experience" must resolve to PROJECTS.
        map.put("project", SectionType.PROJECTS);
        map.put("projects", SectionType.PROJECTS);
        map.put("certification", SectionType.CERTIFICATIONS);
        map.put("certifications", SectionType.CERTIFICATIONS);
        map.put("certificate", SectionType.CERTIFICATIONS);
        map.put("certificates", SectionType.CERTIFICATIONS);
        map.put("achievement", SectionType.ACHIEVEMENTS);
        map.put("achievements", SectionType.ACHIEVEMENTS);
        map.put("award", SectionType.ACHIEVEMENTS);
        map.put("awards", SectionType.ACHIEVEMENTS);
        map.put("honors", SectionType.ACHIEVEMENTS);
        map.put("honours", SectionType.ACHIEVEMENTS);
        map.put("publication", SectionType.PUBLICATIONS);
        map.put("publications", SectionType.PUBLICATIONS);
        // "research" is deliberately absent: it is an ordinary word inside
        // employer names ("Microsoft Research"), and a keyword match plus
        // title case is enough to promote such a line to a heading. The exact
        // table still catches "Research" and "Research Experience" as headings.
        map.put("language", SectionType.LANGUAGES);
        map.put("languages", SectionType.LANGUAGES);
        map.put("education", SectionType.EDUCATION);
        map.put("academic", SectionType.EDUCATION);
        map.put("skill", SectionType.SKILLS);
        map.put("skills", SectionType.SKILLS);
        map.put("competencies", SectionType.SKILLS);
        map.put("technologies", SectionType.SKILLS);
        map.put("internship", SectionType.EXPERIENCE);
        map.put("internships", SectionType.EXPERIENCE);
        map.put("employment", SectionType.EXPERIENCE);
        map.put("experience", SectionType.EXPERIENCE);
        map.put("summary", SectionType.SUMMARY);
        map.put("objective", SectionType.SUMMARY);
        // "profile" is deliberately absent, for the same reason as "research":
        // "GitHub Profile" and "LinkedIn Profile" are link labels, not headings.
        map.put("interest", SectionType.INTERESTS);
        map.put("interests", SectionType.INTERESTS);
        map.put("hobbies", SectionType.INTERESTS);
        map.put("volunteer", SectionType.INTERESTS);
        map.put("reference", SectionType.REFERENCES);
        map.put("references", SectionType.REFERENCES);
        map.put("contact", SectionType.CONTACT);
        // Not Map.copyOf: that leaves iteration order unspecified, and this
        // table's order is load-bearing.
        return Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, SectionType> map, SectionType type, String... phrases) {
        for (String phrase : phrases) {
            map.put(phrase, type);
        }
    }
}
