package com.careerpilot.matching.domain;

import com.careerpilot.parsing.domain.extract.SkillCategory;
import com.careerpilot.parsing.domain.extract.TextTokens;
import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compares a parsed resume against a parsed posting.
 *
 * <p>Requirement-level matching (ADR-0031): the unit of comparison is one stated
 * requirement, not the document as a whole. A cosine similarity between two bags
 * of words produces a number nobody can act on; "this posting asks for Docker
 * three times and your resume never says it" produces an evening's work.
 *
 * <p>Deterministic and offline. No model is consulted, so the same two documents
 * always produce the same percentage and the same ranked gaps.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class MatchEngine {

    /** Bump on any change to weights or rules; stored with every match. */
    public static final String VERSION = "1.0.0";

    private static final int WEIGHT_REQUIRED_SKILLS = 55;
    private static final int WEIGHT_OPTIONAL_SKILLS = 15;
    private static final int WEIGHT_TITLE = 15;
    private static final int WEIGHT_EXPERIENCE = 15;

    /** Above this many unasked-for skills, the resume is aimed elsewhere. */
    private static final int UNFOCUSED_EXTRA_SKILLS = 15;

    private static final int MAX_SUGGESTIONS = 8;

    private MatchEngine() {
    }

    /**
     * @param resume  the candidate's parsed resume
     * @param posting the posting they are considering
     * @return the comparison, never null
     */
    public static MatchOutcome match(ResumeSnapshot resume, JobPosting posting) {
        Map<String, ResumeSnapshot.SkillView> resumeSkills = new LinkedHashMap<>();
        for (ResumeSnapshot.SkillView skill : resume.skills()) {
            resumeSkills.putIfAbsent(skill.normalizedName(), skill);
        }

        List<MatchOutcome.SkillComparison> comparisons = new ArrayList<>();
        Set<String> askedFor = new HashSet<>();

        int requiredTotal = 0;
        int requiredMet = 0;
        int optionalTotal = 0;
        int optionalMet = 0;

        for (JobPosting.RequiredSkill wanted : posting.skills()) {
            askedFor.add(wanted.normalizedName());

            ResumeSnapshot.SkillView have = resumeSkills.get(wanted.normalizedName());
            // A skill can be present in the prose without reaching the skills
            // block. That is a real finding — the candidate has it and is not
            // being credited for it — so it counts as a match and produces a
            // "surface this" suggestion rather than a gap.
            Integer prose = have == null ? findInText(resume, wanted.displayName()) : null;
            boolean matched = have != null || prose != null;

            if (wanted.required()) {
                requiredTotal++;
                if (matched) {
                    requiredMet++;
                }
            } else {
                optionalTotal++;
                if (matched) {
                    optionalMet++;
                }
            }

            Integer resumeLine = have != null ? have.lineStart() : prose;
            comparisons.add(new MatchOutcome.SkillComparison(
                    wanted.normalizedName(),
                    have != null ? have.name() : wanted.displayName(),
                    wanted.category(),
                    matched ? SkillVerdict.MATCHED : SkillVerdict.MISSING,
                    wanted.required(),
                    wanted.priority(),
                    resumeLine == null ? null : resume.lineAt(resumeLine).strip(),
                    resumeLine,
                    wanted.evidence(),
                    wanted.line()));
        }

        resumeSkills.values().stream()
                .filter(skill -> !askedFor.contains(skill.normalizedName()))
                .forEach(skill -> comparisons.add(new MatchOutcome.SkillComparison(
                        skill.normalizedName(),
                        skill.name(),
                        categoryOf(skill.category()),
                        SkillVerdict.EXTRA,
                        false,
                        0,
                        skill.lineStart() == null ? null : resume.lineAt(skill.lineStart()).strip(),
                        skill.lineStart(),
                        null,
                        null)));

        int requiredScore = ratioScore(requiredMet, requiredTotal);
        int optionalScore = ratioScore(optionalMet, optionalTotal);
        int titleScore = titleScore(resume, posting);
        int experienceScore = experienceScore(resume, posting);

        int overall = (int) Math.round(
                requiredScore * (WEIGHT_REQUIRED_SKILLS / 100.0)
                        + optionalScore * (WEIGHT_OPTIONAL_SKILLS / 100.0)
                        + titleScore * (WEIGHT_TITLE / 100.0)
                        + experienceScore * (WEIGHT_EXPERIENCE / 100.0));

        comparisons.sort(Comparator
                .comparingInt((MatchOutcome.SkillComparison c) -> c.verdict().ordinal())
                .thenComparing(Comparator.comparingInt(
                        MatchOutcome.SkillComparison::priority).reversed())
                .thenComparing(MatchOutcome.SkillComparison::displayName));

        return new MatchOutcome(
                overall,
                MatchBand.of(overall),
                requiredScore,
                optionalScore,
                titleScore,
                experienceScore,
                comparisons,
                suggest(resume, posting, comparisons, titleScore),
                VERSION);
    }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    /**
     * A posting that states no requirements of a given kind scores 100 for it,
     * not 0. The candidate has failed nothing; the posting simply did not ask.
     */
    private static int ratioScore(int met, int total) {
        return total == 0 ? 100 : (int) Math.round(100.0 * met / total);
    }

    /**
     * Token overlap between the posting's title and the candidate's most recent
     * role titles.
     *
     * <p>Deliberately generous. An intern whose title was "Software Engineering
     * Intern" applying to "Software Engineer" should not be told the titles do
     * not match; the words that matter are the same.
     */
    private static int titleScore(ResumeSnapshot resume, JobPosting posting) {
        String target = posting.detectedTitle();
        if (target == null || resume.experience().isEmpty()) {
            return 50;
        }
        Set<String> wanted = significantTokens(target);
        if (wanted.isEmpty()) {
            return 50;
        }

        int best = 0;
        for (ResumeSnapshot.ExperienceView experience : resume.experience()) {
            if (experience.jobTitle() == null) {
                continue;
            }
            Set<String> have = significantTokens(experience.jobTitle());
            long shared = wanted.stream().filter(have::contains).count();
            best = Math.max(best, (int) Math.round(100.0 * shared / wanted.size()));
        }
        return best;
    }

    /**
     * Years of experience against the posting's stated minimum.
     *
     * <p>Overlapping roles are not double-counted: two concurrent part-time jobs
     * are not four years of experience, and a candidate who has been told they
     * have four will be caught out in the interview.
     */
    private static int experienceScore(ResumeSnapshot resume, JobPosting posting) {
        Integer required = posting.minimumYears();
        if (required == null) {
            return 100;
        }
        double actual = totalYears(resume);
        if (actual >= required) {
            return 100;
        }
        if (required == 0) {
            return 100;
        }
        return (int) Math.max(0, Math.round(100.0 * actual / required));
    }

    private static double totalYears(ResumeSnapshot resume) {
        List<ResumeSnapshot.ExperienceView> dated = resume.experience().stream()
                .filter(entry -> entry.startDate() != null)
                .sorted(Comparator.comparing(ResumeSnapshot.ExperienceView::startDate))
                .toList();

        double months = 0;
        LocalDate coveredTo = null;

        for (ResumeSnapshot.ExperienceView entry : dated) {
            LocalDate start = entry.startDate();
            LocalDate end = entry.endDate() != null ? entry.endDate()
                    : (entry.current() ? LocalDate.now() : start);
            if (end.isBefore(start)) {
                continue;
            }
            if (coveredTo != null && start.isBefore(coveredTo)) {
                start = coveredTo;
                if (end.isBefore(start)) {
                    continue;
                }
            }
            Period span = Period.between(start, end);
            months += span.getYears() * 12 + span.getMonths();
            coveredTo = (coveredTo == null || end.isAfter(coveredTo)) ? end : coveredTo;
        }
        return months / 12.0;
    }

    // ------------------------------------------------------------------
    // Suggestions — grounded, never invented
    // ------------------------------------------------------------------

    private static List<MatchOutcome.Suggestion> suggest(
            ResumeSnapshot resume,
            JobPosting posting,
            List<MatchOutcome.SkillComparison> comparisons,
            int titleScore) {

        List<MatchOutcome.Suggestion> suggestions = new ArrayList<>();

        // 1. Skills the candidate demonstrably has that never reach the skills
        //    block. The cheapest possible win: no new claim, better placement.
        boolean hasSkillsSection = resume.hasSection("SKILLS");
        comparisons.stream()
                .filter(c -> c.verdict() == SkillVerdict.MATCHED && c.resumeLine() != null)
                .filter(c -> !inSkillsSection(resume, c.resumeLine()))
                .limit(3)
                .forEach(c -> suggestions.add(new MatchOutcome.Suggestion(
                        MatchOutcome.Suggestion.KIND_SURFACE_SKILL,
                        "Move \"" + c.displayName() + "\" into your skills section",
                        "This posting asks for " + c.displayName() + " and you have used it — but "
                                + "it only appears inside a bullet. Keyword screening leans on the "
                                + "skills block, so a skill mentioned only in prose matches less "
                                + "reliably than one listed."
                                + (hasSkillsSection ? "" : " You have no skills section at all yet."),
                        c.resumeEvidence(),
                        "Add \"" + c.displayName() + "\" to your skills list, keeping the bullet "
                                + "as the evidence for it.",
                        c.resumeLine())));

        // 2. Top gaps. Never phrased as "add this" — that would be an invitation
        //    to fabricate. Phrased as what closing it would take.
        comparisons.stream()
                .filter(c -> c.verdict() == SkillVerdict.MISSING && c.required())
                .sorted(Comparator.comparingInt(
                        MatchOutcome.SkillComparison::priority).reversed())
                .limit(3)
                .forEach(c -> suggestions.add(new MatchOutcome.Suggestion(
                        MatchOutcome.Suggestion.KIND_LEARN,
                        "Gap: " + c.displayName(),
                        "The posting asks for this and nothing in your resume shows it. If you "
                                + "have used it and simply did not write it down, add it with the "
                                + "project that proves it. If you have not, a small public project "
                                + "is a faster route to a truthful line than a course certificate.",
                        null,
                        null,
                        c.jdLine())));

        // 3. Bullets that already describe relevant work but state no outcome.
        //    The rewrite leaves the metric as a placeholder: the candidate is the
        //    only party who knows the number, and guessing one is fabrication.
        Set<String> postingVocabulary = posting.skills().stream()
                .map(JobPosting.RequiredSkill::normalizedName)
                .collect(java.util.stream.Collectors.toSet());

        for (int i = 0; i < resume.lines().size() && suggestions.size() < MAX_SUGGESTIONS; i++) {
            String line = resume.lineAt(i);
            if (!isBullet(line) || countWords(line) < 5) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            boolean relevant = postingVocabulary.stream()
                    .anyMatch(skill -> TextTokens.indexOfToken(lower, skill) >= 0);
            if (!relevant || hasNumber(line)) {
                continue;
            }
            String stripped = stripBullet(line);
            suggestions.add(new MatchOutcome.Suggestion(
                    MatchOutcome.Suggestion.KIND_QUANTIFY,
                    "Quantify a bullet this posting cares about",
                    "This bullet covers something the posting explicitly asks for, which makes it "
                            + "one of your strongest — and it states no outcome. A number here is "
                            + "worth more than a number anywhere else on the page.",
                    stripped,
                    stripped + " — [add the scale or result: how many users, how much faster, "
                            + "how many records, over what period]",
                    i));
            break;
        }

        // 4. Title mirroring. The posting's own words, applied to the candidate's
        //    own summary line — not a new claim.
        if (titleScore < 50 && posting.detectedTitle() != null) {
            suggestions.add(new MatchOutcome.Suggestion(
                    MatchOutcome.Suggestion.KIND_MIRROR_TITLE,
                    "Mirror the posting's job title",
                    "Your titles and this posting's title share few words. Recruiters and filters "
                            + "both scan for the role name. Where it is truthful, using the "
                            + "posting's vocabulary for work you have actually done costs nothing "
                            + "and matches better.",
                    null,
                    "Consider a one-line summary naming the target role, for example: "
                            + "\"Aspiring " + posting.detectedTitle().strip() + "\" — only if it "
                            + "describes what you are genuinely aiming at.",
                    null));
        }

        // 5. A resume aimed at a different role entirely.
        long extras = comparisons.stream().filter(c -> c.verdict() == SkillVerdict.EXTRA).count();
        if (extras > UNFOCUSED_EXTRA_SKILLS) {
            suggestions.add(new MatchOutcome.Suggestion(
                    MatchOutcome.Suggestion.KIND_REPHRASE,
                    "This resume is aimed wider than this posting",
                    extras + " of your listed skills are never mentioned in this posting. A "
                            + "general-purpose resume reads as unfocused against a specific role.",
                    null,
                    "Lead with the skills this posting names and move the rest down, rather than "
                            + "removing them.",
                    null));
        }

        return suggestions.size() > MAX_SUGGESTIONS
                ? suggestions.subList(0, MAX_SUGGESTIONS)
                : suggestions;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Finds a skill name in the resume text when it never reached the skills
     * block.
     *
     * @return the 0-based line, or null
     */
    private static Integer findInText(ResumeSnapshot resume, String skillName) {
        String needle = skillName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < resume.lines().size(); i++) {
            if (TextTokens.indexOfToken(resume.lineAt(i).toLowerCase(Locale.ROOT), needle) >= 0) {
                return i;
            }
        }
        return null;
    }

    private static boolean inSkillsSection(ResumeSnapshot resume, int line) {
        return resume.sections().stream()
                .filter(section -> section.type().equals("SKILLS"))
                .anyMatch(section -> line >= section.startLine() && line <= section.endLine());
    }

    private static SkillCategory categoryOf(String name) {
        try {
            return SkillCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SkillCategory.TOOL;
        }
    }

    /** Words worth comparing in a job title — stop words removed. */
    private static Set<String> significantTokens(String title) {
        Set<String> stop = Set.of("a", "an", "the", "and", "or", "of", "for", "to", "in",
                "at", "with", "on", "i", "ii", "iii", "1", "2", "3", "-");
        Set<String> tokens = new HashSet<>();
        for (String token : title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+")) {
            if (token.length() > 1 && !stop.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isBullet(String line) {
        return line != null
                && line.stripLeading().matches("^[\\-\\u2013\\u2014*\\u2022\\u25AA\\u25CF].*");
    }

    private static String stripBullet(String line) {
        return line.stripLeading()
                .replaceFirst("^[\\-\\u2013\\u2014*\\u2022\\u25AA\\u25CF]\\s*", "")
                .strip();
    }

    private static boolean hasNumber(String line) {
        return line.chars().anyMatch(Character::isDigit);
    }

    private static int countWords(String line) {
        String stripped = stripBullet(line);
        return stripped.isBlank() ? 0 : stripped.split("\\s+").length;
    }
}
