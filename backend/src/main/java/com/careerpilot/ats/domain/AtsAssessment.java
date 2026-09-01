package com.careerpilot.ats.domain;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The result of running the rubric: one overall score, five category scores,
 * and every finding that produced them.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record AtsAssessment(
        int overallScore,
        ScoreBand band,
        Map<AtsCategory, Integer> categoryScores,
        List<RuleFinding> findings,
        String rubricVersion
) {

    /**
     * Most urgent first, and within a severity, most expensive first. A report
     * the user reads top-down should have them fixing the thing that costs the
     * most before the thing that costs the least.
     */
    private static final Comparator<RuleFinding> BY_URGENCY =
            Comparator.comparingInt((RuleFinding f) -> f.severity().rank()).reversed()
                    .thenComparing(Comparator.comparingInt(RuleFinding::pointsLost).reversed())
                    .thenComparing(RuleFinding::code);

    public AtsAssessment {
        categoryScores = categoryScores == null
                ? new EnumMap<>(AtsCategory.class)
                : new EnumMap<>(categoryScores);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * Builds an assessment from raw category deductions.
     *
     * @param deductions   points lost per category, on each category's own 0-100 scale
     * @param findings     every finding produced, passes included
     * @param rubricVersion the rule set that produced these numbers
     */
    public static AtsAssessment of(Map<AtsCategory, Integer> deductions,
                                   List<RuleFinding> findings,
                                   String rubricVersion) {
        Map<AtsCategory, Integer> scores = new EnumMap<>(AtsCategory.class);
        double weighted = 0;

        for (AtsCategory category : AtsCategory.values()) {
            int lost = deductions.getOrDefault(category, 0);
            int score = Math.max(0, Math.min(100, 100 - lost));
            scores.put(category, score);
            weighted += score * (category.weight() / 100.0);
        }

        int overall = (int) Math.round(weighted);
        List<RuleFinding> ordered = findings.stream().sorted(BY_URGENCY).toList();

        return new AtsAssessment(overall, ScoreBand.of(overall), scores, ordered, rubricVersion);
    }

    public int scoreFor(AtsCategory category) {
        return categoryScores.getOrDefault(category, 0);
    }

    public List<RuleFinding> problems() {
        return findings.stream().filter(RuleFinding::isProblem).toList();
    }

    public List<RuleFinding> passes() {
        return findings.stream().filter(finding -> !finding.isProblem()).toList();
    }
}
