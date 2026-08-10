package com.careerpilot.parsing.domain.section;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resume text split into stable, addressable lines.
 *
 * <p><strong>This class is the anchor for every piece of evidence the product
 * ever shows.</strong> Parsed entities store line ranges, not copies of text.
 * The ATS analyser quotes those ranges back to the user, and the "here's what
 * the machine saw" screen highlights them. All of that holds only while line
 * numbering is reproducible.
 *
 * <p>Reproducibility is why normalisation lives here and nowhere else, and why
 * it carries {@link #NORMALISATION_VERSION}. If the rules below ever change,
 * every line pointer already stored in the database refers to a different line
 * than it did when it was written — silently, with no error. Changing the rules
 * therefore requires bumping the version and re-parsing affected resumes, not
 * editing a regex.
 *
 * <p>Clients must render {@link #text()} rather than the stored {@code raw_text}
 * for the same reason: highlighting an index into one string while displaying
 * another produces a highlight in the wrong place.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class LineModel {

    /**
     * The normalisation contract version.
     *
     * <p>Stored alongside parsed entities so a later reader can tell whether the
     * line pointers it is holding were produced by today's rules.
     */
    public static final int NORMALISATION_VERSION = 1;

    /** Every line-separator convention a PDF or DOCX extractor can emit. */
    private static final Pattern LINE_SEPARATORS = Pattern.compile("\\r\\n|\\r|\\u2028|\\u2029");

    /** Space-like characters that are not U+0020 and break naive tokenising. */
    private static final Pattern EXOTIC_SPACES =
            Pattern.compile("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]");

    /** Zero-width characters PDF extractors leave behind; invisible but tokenised. */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");

    private final String text;
    private final List<DocumentLine> lines;

    private LineModel(String text, List<DocumentLine> lines) {
        this.text = text;
        this.lines = lines;
    }

    /**
     * Normalises raw extracted text and splits it into indexed lines.
     *
     * <p>The normalisation is deliberately conservative — it repairs how
     * characters are encoded, never what they say:
     *
     * <ol>
     *   <li>Unicode NFC, so an accented name extracted as a decomposed pair
     *       becomes one character and matches what the user typed</li>
     *   <li>Exotic spaces and zero-width characters become ordinary spaces or
     *       disappear</li>
     *   <li>All line separators become {@code \n}</li>
     *   <li>Trailing whitespace is stripped per line</li>
     *   <li>Leading and trailing blank lines are dropped</li>
     * </ol>
     *
     * <p>Blank lines <em>inside</em> the document are kept. A blank line before
     * a heading is one of the strongest signals that it is a heading, and
     * collapsing them would discard that evidence to save nothing.
     *
     * @param rawText extracted text; {@code null} and blank are tolerated
     * @return an addressable line model, possibly empty
     */
    public static LineModel of(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new LineModel("", List.of());
        }

        String normalised = Normalizer.normalize(rawText, Normalizer.Form.NFC);
        normalised = ZERO_WIDTH.matcher(normalised).replaceAll("");
        normalised = EXOTIC_SPACES.matcher(normalised).replaceAll(" ");
        normalised = LINE_SEPARATORS.matcher(normalised).replaceAll("\n");

        String[] split = normalised.split("\n", -1);

        int first = 0;
        int last = split.length - 1;
        while (first <= last && split[first].isBlank()) {
            first++;
        }
        while (last >= first && split[last].isBlank()) {
            last--;
        }
        if (first > last) {
            return new LineModel("", List.of());
        }

        List<DocumentLine> built = new ArrayList<>(last - first + 1);
        StringBuilder rebuilt = new StringBuilder();

        for (int i = first; i <= last; i++) {
            String line = stripTrailing(split[i]);
            built.add(new DocumentLine(built.size(), line));
            if (i > first) {
                rebuilt.append('\n');
            }
            rebuilt.append(line);
        }

        return new LineModel(rebuilt.toString(), List.copyOf(built));
    }

    /**
     * The normalised document.
     *
     * <p>Render this, not the stored raw text — line indices address this string.
     *
     * @return the text every line index refers to
     */
    public String text() {
        return text;
    }

    /**
     * @return every line, in order
     */
    public List<DocumentLine> lines() {
        return lines;
    }

    /**
     * @return the number of lines
     */
    public int size() {
        return lines.size();
    }

    /**
     * @return {@code true} if there are no lines
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * @param index zero-based line index
     * @return the line at that index
     * @throws IndexOutOfBoundsException if the index is outside the document
     */
    public DocumentLine line(int index) {
        return lines.get(index);
    }

    /**
     * Returns a line range, clamped to the document.
     *
     * <p>Clamping rather than throwing: ranges come from stored entities, and a
     * range that has drifted past the end of a re-parsed document should degrade
     * to less evidence, not to a failed page render.
     *
     * @param startInclusive first line
     * @param endInclusive   last line
     * @return the lines in that range, empty if the range is inverted
     */
    public List<DocumentLine> range(int startInclusive, int endInclusive) {
        int from = Math.max(0, startInclusive);
        int to = Math.min(lines.size() - 1, endInclusive);
        if (from > to || lines.isEmpty()) {
            return List.of();
        }
        return List.copyOf(lines.subList(from, to + 1));
    }

    /**
     * Reassembles a line range into text.
     *
     * <p>This is the quote mechanism behind {@code FR-ATS-03}: a finding cites
     * a range, and this turns the range back into the words the user wrote.
     *
     * @param startInclusive first line
     * @param endInclusive   last line
     * @return the text of that range, newline-joined
     */
    public String textOf(int startInclusive, int endInclusive) {
        return String.join("\n", range(startInclusive, endInclusive).stream()
                .map(DocumentLine::text)
                .toList());
    }

    /**
     * Finds the next line at or after an index that has content.
     *
     * @param fromIndex where to start looking, inclusive
     * @return the index of the next non-blank line, or {@code -1} if there is none
     */
    public int nextNonBlank(int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static String stripTrailing(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
