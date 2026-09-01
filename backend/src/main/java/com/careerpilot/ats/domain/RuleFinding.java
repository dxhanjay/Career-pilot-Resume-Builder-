package com.careerpilot.ats.domain;

/**
 * One observation about a resume, with the text that produced it.
 *
 * <p>FR-ATS-03. {@code evidence} is not decoration: it is the difference
 * between "your bullets are weak" and "line 24 reads <em>Responsible for
 * testing</em>". The first is an opinion; the second can be checked and fixed.
 *
 * <p>Pure data. The rule engine produces these, the application layer persists
 * them, and neither step needs a Spring context to be tested.
 *
 * @param code            stable identifier, safe to branch on in a client
 * @param category        which of the five areas this belongs to
 * @param severity        how much it costs; {@link AtsSeverity#PASS} for things done right
 * @param title           one line, shown as the heading
 * @param detail          what was observed and why it matters
 * @param recommendation  what to do about it; null for a PASS
 * @param evidence        verbatim text from the resume, or null for a whole-document finding
 * @param lineStart       0-based first line of the evidence, or null
 * @param lineEnd         0-based last line of the evidence, or null
 * @param pointsLost      deduction inside the category's own 0-100 scale
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record RuleFinding(
        String code,
        AtsCategory category,
        AtsSeverity severity,
        String title,
        String detail,
        String recommendation,
        String evidence,
        Integer lineStart,
        Integer lineEnd,
        int pointsLost
) {

    /** Truncation guard: an "evidence" quote longer than this is a section, not a quote. */
    private static final int MAX_EVIDENCE_CHARS = 600;

    public RuleFinding {
        if (evidence != null && evidence.length() > MAX_EVIDENCE_CHARS) {
            evidence = evidence.substring(0, MAX_EVIDENCE_CHARS - 1) + "…";
        }
    }

    /** A problem that refers to specific lines. */
    public static RuleFinding problem(String code, AtsCategory category, AtsSeverity severity,
                                      String title, String detail, String recommendation,
                                      String evidence, Integer lineStart, Integer lineEnd,
                                      int pointsLost) {
        return new RuleFinding(code, category, severity, title, detail, recommendation,
                evidence, lineStart, lineEnd, pointsLost);
    }

    /** A problem about the document as a whole, with no single line to point at. */
    public static RuleFinding problem(String code, AtsCategory category, AtsSeverity severity,
                                      String title, String detail, String recommendation,
                                      int pointsLost) {
        return new RuleFinding(code, category, severity, title, detail, recommendation,
                null, null, null, pointsLost);
    }

    /** Something the resume already gets right. Costs nothing and is still reported. */
    public static RuleFinding pass(String code, AtsCategory category, String title, String detail) {
        return new RuleFinding(code, category, AtsSeverity.PASS, title, detail,
                null, null, null, null, 0);
    }

    public boolean isProblem() {
        return severity.isProblem();
    }
}
