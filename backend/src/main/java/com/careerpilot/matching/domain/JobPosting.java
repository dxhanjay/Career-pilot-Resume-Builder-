package com.careerpilot.matching.domain;

import com.careerpilot.parsing.domain.extract.SkillCategory;
import com.careerpilot.parsing.domain.extract.SkillLexicon;
import com.careerpilot.parsing.domain.section.LineModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A job posting read the way the matcher needs it: which skills it asks for,
 * which of those it insists on, and the line of the posting that asked.
 *
 * <p>Pure and deterministic. The same posting text always produces the same
 * requirements, which is what lets a match be explained rather than asserted.
 *
 * <p>The must-have / nice-to-have split is heuristic and says so. A posting that
 * writes "familiarity with Kubernetes a plus" under a heading called
 * "Requirements" is genuinely ambiguous, and the honest behaviour is to rank the
 * gap lower rather than to pretend the distinction was clean.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record JobPosting(
        List<String> lines,
        List<RequiredSkill> skills,
        String detectedTitle,
        Integer minimumYears,
        String seniority
) {

    /** Headings under which everything is treated as a hard requirement. */
    private static final Pattern REQUIRED_HEADING = Pattern.compile(
            "(?i)^\\s*(requirements?|required|must[- ]?haves?|qualifications?|"
                    + "minimum qualifications|what (you|we)('| a)?ll need|"
                    + "basic qualifications|essential|you have|who you are)\\b.{0,40}$");

    /** Headings that mark the optional half of a posting. */
    private static final Pattern OPTIONAL_HEADING = Pattern.compile(
            "(?i)^\\s*(nice[- ]to[- ]haves?|preferred|preferred qualifications|"
                    + "bonus|desirable|pluses?|good to have|advantageous|"
                    + "additionally|it would be great)\\b.{0,40}$");

    /** In-line softeners that demote a skill even under a hard heading. */
    private static final Pattern OPTIONAL_PHRASE = Pattern.compile(
            "(?i)(nice to have|a plus|bonus|preferred|desirable|would be great|"
                    + "familiarity with|exposure to|any of|advantage)");

    /** In-line intensifiers that promote a skill even under a soft heading. */
    private static final Pattern REQUIRED_PHRASE = Pattern.compile(
            "(?i)(must have|required|strong (experience|knowledge|proficiency)|"
                    + "proven (experience|track)|solid (experience|understanding)|"
                    + "expertise in|proficient in|deep knowledge)");

    private static final Pattern YEARS = Pattern.compile(
            "(?i)(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?(?:years?|yrs?)\\b");

    private static final Map<String, String> SENIORITY = new LinkedHashMap<>();

    static {
        SENIORITY.put("intern", "Internship");
        SENIORITY.put("internship", "Internship");
        SENIORITY.put("graduate", "Graduate");
        SENIORITY.put("entry level", "Entry level");
        SENIORITY.put("entry-level", "Entry level");
        SENIORITY.put("junior", "Junior");
        SENIORITY.put("associate", "Associate");
        SENIORITY.put("mid-level", "Mid-level");
        SENIORITY.put("senior", "Senior");
        SENIORITY.put("staff", "Staff");
        SENIORITY.put("principal", "Principal");
        SENIORITY.put("lead", "Lead");
        SENIORITY.put("head of", "Leadership");
        SENIORITY.put("director", "Leadership");
    }

    /**
     * Reads a posting.
     *
     * @param rawText the posting exactly as the user pasted it
     * @return the requirements it states, never null
     */
    public static JobPosting parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return empty();
        }
        LineModel model = LineModel.of(rawText);
        List<String> lines = model.lines().stream().map(line -> line.text()).toList();

        Map<String, RequiredSkill> found = new LinkedHashMap<>();
        boolean inOptionalBlock = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String stripped = line.strip();

            if (REQUIRED_HEADING.matcher(stripped).matches()) {
                inOptionalBlock = false;
                continue;
            }
            if (OPTIONAL_HEADING.matcher(stripped).matches()) {
                inOptionalBlock = true;
                continue;
            }

            boolean required = !inOptionalBlock;
            if (OPTIONAL_PHRASE.matcher(stripped).find()) {
                required = false;
            }
            if (REQUIRED_PHRASE.matcher(stripped).find()) {
                required = true;
            }

            // Ambiguous lexicon entries ("R", "Go", "C") are included because a
            // posting is dense with technology names and the surrounding words
            // disambiguate far better than they do in a resume's skills list.
            for (SkillLexicon.Hit hit : SkillLexicon.findIn(line, true)) {
                String canonical = hit.entry().canonical();
                RequiredSkill existing = found.get(canonical);
                if (existing == null) {
                    found.put(canonical, new RequiredSkill(
                            canonical, canonical, hit.entry().category(), required, 1, i,
                            stripped));
                } else {
                    // Repetition is the posting telling you what it cares about.
                    found.put(canonical, existing.mentionedAgain(required));
                }
            }
        }

        return new JobPosting(
                lines,
                List.copyOf(found.values()),
                detectTitle(lines),
                detectYears(rawText),
                detectSeniority(rawText));
    }

    public List<RequiredSkill> requiredSkills() {
        return skills.stream().filter(RequiredSkill::required).toList();
    }

    public List<RequiredSkill> optionalSkills() {
        return skills.stream().filter(skill -> !skill.required()).toList();
    }

    /**
     * The first substantial line, used as a fallback title when the user did not
     * supply one. Postings almost always open with the role name.
     */
    private static String detectTitle(List<String> lines) {
        return lines.stream()
                .map(String::strip)
                .filter(line -> line.length() >= 3 && line.length() <= 90)
                .filter(line -> line.chars().anyMatch(Character::isLetter))
                .findFirst()
                .orElse(null);
    }

    private static Integer detectYears(String rawText) {
        Matcher matcher = YEARS.matcher(rawText);
        Integer lowest = null;
        while (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            // 20+ years is a date range or a company age, not a requirement.
            if (years >= 1 && years <= 20 && (lowest == null || years < lowest)) {
                lowest = years;
            }
        }
        return lowest;
    }

    private static String detectSeniority(String rawText) {
        String lower = rawText.toLowerCase(Locale.ROOT);
        // Search only the opening, where the title lives. "senior" appearing in
        // the tenth bullet describes a colleague, not the role.
        String head = lower.substring(0, Math.min(400, lower.length()));
        for (Map.Entry<String, String> entry : SENIORITY.entrySet()) {
            if (head.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * One skill the posting asks for.
     *
     * @param mentions how many lines mention it; repetition raises gap priority
     * @param line     0-based line of the first mention
     * @param evidence that line, quoted
     */
    public record RequiredSkill(
            String normalizedName,
            String displayName,
            SkillCategory category,
            boolean required,
            int mentions,
            int line,
            String evidence
    ) {
        RequiredSkill mentionedAgain(boolean alsoRequired) {
            return new RequiredSkill(normalizedName, displayName, category,
                    required || alsoRequired, mentions + 1, line, evidence);
        }

        /**
         * How badly a gap here hurts, 0-100.
         *
         * <p>Required beats optional; repeated beats mentioned once. A concrete
         * language or framework outranks a soft skill, because a filter can be
         * set on the first and rarely is on the second.
         */
        public int priority() {
            int score = required ? 60 : 25;
            score += Math.min(20, (mentions - 1) * 7);
            score += switch (category) {
                case LANGUAGE, FRAMEWORK -> 15;
                case DATABASE, CLOUD_DEVOPS -> 10;
                case TOOL -> 5;
                default -> 0;
            };
            return Math.min(100, score);
        }
    }

    /** A posting with nothing in it. Returned for blank input rather than throwing. */
    static JobPosting empty() {
        return new JobPosting(List.of(), List.of(), null, null, null);
    }

    /** Convenience for callers that only need the canonical names. */
    public List<String> skillNames() {
        List<String> names = new ArrayList<>(skills.size());
        skills.forEach(skill -> names.add(skill.normalizedName()));
        return names;
    }
}
