package com.careerpilot.parsing.domain;

import java.util.List;

/**
 * The result of extracting text from a document.
 *
 * <p>A plain record in the domain layer: no Spring, no I/O, no framework. The
 * quality heuristics below are therefore unit-testable against a string in
 * microseconds, which is what makes it practical to have many of them.
 *
 * @param rawText   the extracted text, normalised
 * @param pageCount pages processed; 0 where the format has no pages
 * @param parser    which extractor produced this
 * @param warnings  structural problems detected
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record ExtractedText(
        String rawText,
        int pageCount,
        String parser,
        List<ParseWarning> warnings
) {

    /** Below this, a document with pages almost certainly has no text layer. */
    private static final int NO_TEXT_LAYER_THRESHOLD = 20;

    /** Below this, a resume is suspiciously thin — usually image-based. */
    private static final int SPARSE_TEXT_THRESHOLD = 120;

    /** Above this, a resume is unusually long for an early-career candidate. */
    private static final int LONG_DOCUMENT_PAGES = 4;

    /**
     * @return the number of whitespace-separated tokens
     */
    public int wordCount() {
        if (rawText == null || rawText.isBlank()) {
            return 0;
        }
        return rawText.trim().split("\\s+").length;
    }

    /**
     * @return the number of characters extracted
     */
    public int charCount() {
        return rawText == null ? 0 : rawText.length();
    }

    /**
     * Whether enough text was recovered for downstream analysis to mean anything.
     *
     * <p>The distinction between "extraction failed" and "extraction succeeded
     * and found nothing" matters to the user: the first is our problem, the
     * second is a property of their file and is fixable by them. Both are
     * reported as a parse failure, but with different messages.
     *
     * @return {@code true} if there is enough text to analyse
     */
    public boolean isUsable() {
        return wordCount() >= NO_TEXT_LAYER_THRESHOLD;
    }

    /**
     * Detects quality problems that do not prevent extraction.
     *
     * <p>Appended to whatever the extractor itself reported, so a caller sees
     * format-specific warnings and content-quality warnings together.
     *
     * @return warnings implied by the extracted content
     */
    public List<ParseWarning> qualityWarnings() {
        List<ParseWarning> detected = new java.util.ArrayList<>(warnings);

        int words = wordCount();

        if (pageCount > 0 && words < NO_TEXT_LAYER_THRESHOLD) {
            detected.add(ParseWarning.noTextLayer());
        } else if (words < SPARSE_TEXT_THRESHOLD) {
            detected.add(ParseWarning.sparseText(words));
        }

        if (pageCount > LONG_DOCUMENT_PAGES) {
            detected.add(ParseWarning.unusuallyLong(pageCount));
        }

        if (looksMultiColumn()) {
            detected.add(ParseWarning.multiColumnLayout());
        }

        return List.copyOf(detected);
    }

    /**
     * Heuristic detection of a multi-column layout.
     *
     * <p>Text extractors read a two-column page in one of two ways: they either
     * interleave the columns line by line, or they emit lines containing a run
     * of whitespace where the gutter was. The second is detectable — a line with
     * text, then several consecutive spaces, then more text, is very unlikely in
     * normal prose and very likely across a column gutter.
     *
     * <p>Deliberately a heuristic, and deliberately conservative. A false
     * positive costs the user an unnecessary suggestion; a false negative costs
     * them the explanation for why their resume scored badly. The threshold is
     * set so that a handful of aligned lines is not enough — a genuine
     * two-column CV produces this pattern on most of its lines.
     *
     * <p>A layout-aware extraction pass would be more accurate and is a
     * candidate for later. This costs nothing and catches the common case.
     *
     * @return {@code true} if the text looks like it came from columns
     */
    public boolean looksMultiColumn() {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }

        String[] lines = rawText.split("\\R");
        if (lines.length < 10) {
            return false;
        }

        int gutterLines = 0;
        int contentLines = 0;

        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.length() < 20) {
                continue;
            }
            contentLines++;
            // Text, a run of 4+ spaces, then more text.
            if (trimmed.matches(".*\\S {4,}\\S.*")) {
                gutterLines++;
            }
        }

        return contentLines >= 10 && (double) gutterLines / contentLines > 0.35;
    }
}
