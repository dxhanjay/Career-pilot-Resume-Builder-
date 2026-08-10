package com.careerpilot.parsing.domain.section;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Divides resume text into sections by locating its headings.
 *
 * <p>Entirely deterministic: the same text always yields the same sections. That
 * is a product requirement, not an implementation detail. Risk R3 in the PRD is
 * that a user re-uploads an unchanged resume and sees a different score; a
 * segmenter that guessed differently on different runs would make that
 * unavoidable no matter how careful the scoring above it was.
 *
 * <h2>How a heading is identified</h2>
 *
 * <p>A candidate line must first survive the disqualifiers in
 * {@link #isDisqualified}: blank lines, bullets, prose-length lines, lines
 * ending in sentence punctuation, and lines carrying contact details are not
 * headings, whatever else they look like.
 *
 * <p>It must then <em>name a known section</em>. A line the lexicon cannot
 * resolve is never treated as a heading, even one set in bold capitals on its
 * own. This is the deliberate limit of the rule-based approach: "Where I've
 * Been" is a real heading that this will miss. The design choice is to report
 * that honestly — through low section coverage — rather than to guess, and to
 * let the LLM repair pass in Phase 7 handle the cases rules cannot.
 *
 * <p>Finally it must score at least {@link #HEADING_THRESHOLD} across the
 * structural signals in {@link #scoreHeading}. The threshold is set so that a
 * keyword match alone cannot promote a line: "Experience with distributed
 * systems, including Kafka" resolves to EXPERIENCE in the lexicon but is long,
 * ends in prose, and scores far below the bar.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class SectionSegmenter {

    /** Minimum score for a line to be accepted as a heading. */
    static final int HEADING_THRESHOLD = 60;

    /** An exact lexicon phrase is the strongest evidence available. */
    private static final int SCORE_EXACT_MATCH = 50;

    /** A keyword match is real evidence, but not enough on its own. */
    private static final int SCORE_KEYWORD_MATCH = 28;

    private static final int SCORE_ALL_CAPS = 18;
    private static final int SCORE_TITLE_CASE = 9;
    private static final int SCORE_SHORT_LINE = 12;
    private static final int SCORE_SHORT_CHARS = 8;
    private static final int SCORE_BLANK_LINE_BEFORE = 10;
    private static final int SCORE_CONTENT_FOLLOWS = 6;
    private static final int SCORE_TRAILING_COLON = 5;

    /** Headings are short. Beyond this a line is being used as a sentence. */
    private static final int MAX_HEADING_WORDS = 6;

    /** Short even for a heading — "SKILLS", "EDUCATION". */
    private static final int SHORT_LINE_WORDS = 3;

    /**
     * Character length below which a line is heading-shaped.
     *
     * <p>Scored separately from word count because they catch different things.
     * "TECHNICAL SKILLS &amp; TOOLS" is four words but only 24 characters, and
     * plenty of resumes run sections together with no blank line between them —
     * without this signal such a heading falls just under the threshold.
     */
    private static final int SHORT_LINE_CHARS = 30;

    /** Above this, a line is prose regardless of how it is capitalised. */
    private static final int MAX_HEADING_CHARS = 60;

    private SectionSegmenter() {
    }

    /**
     * Segments a document into sections.
     *
     * <p>The returned list is ordered by position and covers the document
     * without gaps. Content above the first heading becomes a headless
     * {@link SectionType#CONTACT} section; a document with no detectable
     * headings becomes a single {@link SectionType#UNKNOWN} section spanning
     * everything, so that lexicon-based extraction can still run over it.
     *
     * @param model the normalised document
     * @return the sections found, empty only when the document itself is empty
     */
    public static List<ResumeSection> segment(LineModel model) {
        if (model == null || model.isEmpty()) {
            return List.of();
        }

        List<Heading> headings = findHeadings(model);

        if (headings.isEmpty()) {
            // No structure we can recognise. Reported as one unknown block with
            // zero confidence rather than as a failure: the text is still there,
            // and skills can still be found in it.
            return List.of(ResumeSection.headless(
                    SectionType.UNKNOWN, 0, model.size() - 1, 0));
        }

        List<ResumeSection> sections = new ArrayList<>(headings.size() + 1);

        int firstHeadingLine = headings.get(0).line();
        if (firstHeadingLine > 0) {
            sections.add(ResumeSection.headless(
                    SectionType.CONTACT, 0, firstHeadingLine - 1,
                    contactConfidence(model, firstHeadingLine)));
        }

        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int contentStart = heading.line() + 1;
            int contentEnd = (i + 1 < headings.size())
                    ? headings.get(i + 1).line() - 1
                    : model.size() - 1;

            sections.add(ResumeSection.withHeading(
                    heading.type(), model.line(heading.line()).stripped(), heading.line(),
                    contentStart, contentEnd, heading.score()));
        }

        return List.copyOf(sections);
    }

    private static List<Heading> findHeadings(LineModel model) {
        List<Heading> headings = new ArrayList<>();

        for (DocumentLine line : model.lines()) {
            if (isDisqualified(line)) {
                continue;
            }

            Optional<HeadingLexicon.Match> match = HeadingLexicon.resolve(line.text());
            if (match.isEmpty()) {
                continue;
            }

            int score = scoreHeading(line, match.get(), model);
            if (score >= HEADING_THRESHOLD) {
                headings.add(new Heading(line.index(), match.get().type(), score));
            }
        }

        return headings;
    }

    /**
     * Structural reasons a line cannot be a heading.
     *
     * <p>Applied before scoring, because each of these is decisive on its own.
     * A bulleted line is content by definition; a line holding an email address
     * is contact detail even when it is short and capitalised.
     *
     * @param line the candidate
     * @return {@code true} if the line cannot be a heading
     */
    static boolean isDisqualified(DocumentLine line) {
        return line.isBlank()
                || line.isBullet()
                || line.hasContactDetails()
                || line.endsLikeProse()
                || line.wordCount() > MAX_HEADING_WORDS
                || line.length() > MAX_HEADING_CHARS;
    }

    /**
     * Scores how strongly a line behaves like a heading.
     *
     * <p>Additive rather than a decision tree, so that no single weak signal
     * decides the outcome and so the weights can be tuned against the
     * adversarial corpus without restructuring the logic. The score becomes the
     * section's confidence, which is what a low-confidence LLM repair pass will
     * later select on.
     *
     * @param line  the candidate
     * @param match what the lexicon resolved it to
     * @param model the document, for context around the line
     * @return a score from 0 to 100
     */
    static int scoreHeading(DocumentLine line, HeadingLexicon.Match match, LineModel model) {
        int score = match.exact() ? SCORE_EXACT_MATCH : SCORE_KEYWORD_MATCH;

        if (line.isAllCaps()) {
            score += SCORE_ALL_CAPS;
        } else if (line.isTitleCase()) {
            score += SCORE_TITLE_CASE;
        }

        if (line.wordCount() <= SHORT_LINE_WORDS) {
            score += SCORE_SHORT_LINE;
        }

        if (line.length() <= SHORT_LINE_CHARS) {
            score += SCORE_SHORT_CHARS;
        }

        if (line.stripped().endsWith(":")) {
            score += SCORE_TRAILING_COLON;
        }

        // A heading is separated from what precedes it. The first line of a
        // document counts: nothing precedes it at all.
        if (line.index() == 0 || model.line(line.index() - 1).isBlank()) {
            score += SCORE_BLANK_LINE_BEFORE;
        }

        // A heading introduces something. One with nothing after it is more
        // likely a stray line, or a footer.
        if (model.nextNonBlank(line.index() + 1) >= 0) {
            score += SCORE_CONTENT_FOLLOWS;
        }

        return Math.min(100, score);
    }

    /**
     * How much to trust that the pre-heading block really is contact detail.
     *
     * <p>It nearly always is — resumes open with a name and contact details. The
     * confidence reflects whether the block actually contains any, because a
     * preamble that holds none may be something else entirely, such as a summary
     * whose heading was missed.
     */
    private static int contactConfidence(LineModel model, int firstHeadingLine) {
        boolean hasDetails = model.range(0, firstHeadingLine - 1).stream()
                .anyMatch(DocumentLine::hasContactDetails);
        return hasDetails ? 90 : 45;
    }

    /** A located heading, before it becomes a section with bounds. */
    private record Heading(int line, SectionType type, int score) {
    }
}
