package com.careerpilot.parsing.domain.section;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LineModel}.
 *
 * <p>These are stability tests as much as correctness tests. Line indices are
 * stored in the database and later used to highlight evidence in the user's own
 * resume, so a change in normalisation that shifts indices corrupts every
 * pointer already written — without throwing anything. The assertions here are
 * what make that change loud.
 *
 * <p>Invisible characters are built from named code-point constants rather than
 * pasted in as literals. A zero-width-character test containing a literal
 * zero-width character cannot be reviewed, and does not survive a copy-paste or
 * an encoding change that silently drops it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("LineModel")
class LineModelTest {

    /** U+00A0 non-breaking space. */
    private static final String NBSP = ch(0x00A0);

    /** U+2009 thin space. */
    private static final String THIN_SPACE = ch(0x2009);

    /** U+200B zero-width space. */
    private static final String ZERO_WIDTH_SPACE = ch(0x200B);

    /** U+FEFF byte-order mark, which extractors leave mid-document. */
    private static final String BOM = ch(0xFEFF);

    /** U+0301 combining acute accent. */
    private static final String COMBINING_ACUTE = ch(0x0301);

    private static String ch(int codePoint) {
        return String.valueOf((char) codePoint);
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("treats every line-separator convention as one newline")
        void unifiesLineSeparators() {
            // CRLF, bare CR, and LF all split; an ordinary space does not.
            LineModel model = LineModel.of("alpha\r\nbeta\rgamma delta\nepsilon");

            assertThat(model.size()).isEqualTo(4);
            assertThat(model.lines()).extracting(DocumentLine::text)
                    .containsExactly("alpha", "beta", "gamma delta", "epsilon");
        }

        @Test
        @DisplayName("converts non-breaking and exotic spaces to ordinary spaces")
        void normalisesExoticSpaces() {
            LineModel model = LineModel.of("Java" + NBSP + "and" + THIN_SPACE + "Spring");

            assertThat(model.line(0).text()).isEqualTo("Java and Spring");
            assertThat(model.line(0).wordCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("removes zero-width characters left behind by PDF extractors")
        void removesZeroWidth() {
            LineModel model = LineModel.of("Spring" + ZERO_WIDTH_SPACE + "Boot" + BOM);

            assertThat(model.line(0).text()).isEqualTo("SpringBoot");
        }

        @Test
        @DisplayName("composes decomposed accents so names match what the user typed")
        void appliesNfc() {
            LineModel model = LineModel.of("Jose" + COMBINING_ACUTE + " Ramos");

            // NFC folds the "e" and the combining accent into one character, so
            // the name is 10 characters and not 11.
            assertThat(model.line(0).text()).hasSize(10);
            assertThat(model.line(0).text()).startsWith("Jos");
            assertThat(model.line(0).wordCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("strips trailing whitespace but keeps indentation")
        void stripsTrailingWhitespaceOnly() {
            LineModel model = LineModel.of("   indented content   \n");

            assertThat(model.line(0).text()).isEqualTo("   indented content");
        }

        @Test
        @DisplayName("drops leading and trailing blank lines")
        void trimsDocumentEdges() {
            LineModel model = LineModel.of("\n\n  \nEDUCATION\ncontent\n\n  \n");

            assertThat(model.size()).isEqualTo(2);
            assertThat(model.line(0).text()).isEqualTo("EDUCATION");
        }

        @Test
        @DisplayName("keeps interior blank lines, which are heading evidence")
        void keepsInteriorBlanks() {
            LineModel model = LineModel.of("SKILLS\n\nJava\n\n\nEDUCATION");

            assertThat(model.size()).isEqualTo(6);
            assertThat(model.line(1).isBlank()).isTrue();
            assertThat(model.line(3).isBlank()).isTrue();
            assertThat(model.line(4).isBlank()).isTrue();
        }

        @Test
        @DisplayName("null and blank input produce an empty model rather than throwing")
        void handlesEmptyInput() {
            assertThat(LineModel.of(null).isEmpty()).isTrue();
            assertThat(LineModel.of("").isEmpty()).isTrue();
            assertThat(LineModel.of("   \n\n  ").isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("indexing")
    class Indexing {

        @Test
        @DisplayName("indices are contiguous from zero")
        void indicesAreContiguous() {
            LineModel model = LineModel.of("a\nb\nc\nd");

            for (int i = 0; i < model.size(); i++) {
                assertThat(model.line(i).index()).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("text() is the string the indices address")
        void textMatchesIndices() {
            LineModel model = LineModel.of("alpha\r\nbeta\r\ngamma");

            assertThat(model.text()).isEqualTo("alpha\nbeta\ngamma");
            assertThat(model.text().split("\n")).hasSize(model.size());
        }

        @Test
        @DisplayName("the same input always produces the same model")
        void isDeterministic() {
            String input = "SKILLS\r\nJava , Spring\n\nEDUCATION\nB.Tech";

            assertThat(LineModel.of(input).text()).isEqualTo(LineModel.of(input).text());
        }
    }

    @Nested
    @DisplayName("ranges")
    class Ranges {

        private final LineModel model = LineModel.of("zero\none\ntwo\nthree\nfour");

        @Test
        @DisplayName("returns the inclusive range")
        void returnsInclusiveRange() {
            assertThat(model.range(1, 3)).extracting(DocumentLine::text)
                    .containsExactly("one", "two", "three");
        }

        @Test
        @DisplayName("reassembles a range into quotable text")
        void reassemblesText() {
            assertThat(model.textOf(1, 2)).isEqualTo("one\ntwo");
        }

        @Test
        @DisplayName("clamps out-of-bounds ranges instead of throwing")
        void clampsRanges() {
            assertThat(model.range(-5, 99)).hasSize(5);
            assertThat(model.range(3, 99)).extracting(DocumentLine::text)
                    .containsExactly("three", "four");
        }

        @Test
        @DisplayName("an inverted range is empty, not an error")
        void invertedRangeIsEmpty() {
            assertThat(model.range(4, 1)).isEmpty();
            assertThat(model.textOf(4, 1)).isEmpty();
        }
    }

    @Nested
    @DisplayName("nextNonBlank")
    class NextNonBlank {

        @Test
        @DisplayName("skips blank lines")
        void skipsBlanks() {
            LineModel model = LineModel.of("SKILLS\n\n\nJava");

            assertThat(model.nextNonBlank(1)).isEqualTo(3);
        }

        @Test
        @DisplayName("returns the index itself when it already has content")
        void returnsSelf() {
            LineModel model = LineModel.of("SKILLS\nJava");

            assertThat(model.nextNonBlank(0)).isZero();
        }

        @Test
        @DisplayName("returns -1 when nothing follows")
        void returnsMinusOneAtEnd() {
            LineModel model = LineModel.of("SKILLS\nJava");

            assertThat(model.nextNonBlank(2)).isEqualTo(-1);
        }
    }
}
