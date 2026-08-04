package com.careerpilot.parsing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExtractedText}.
 *
 * <p>These cover the heuristics behind the warnings a user actually sees. They
 * matter because the warnings are the product's differentiator: a score with no
 * explanation is what every competitor already gives, and "your two-column
 * layout scrambled your work history" is the part that makes the score
 * believable.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("ExtractedText")
class ExtractedTextTest {

    private static ExtractedText of(String text, int pages) {
        return new ExtractedText(text, pages, "PDFBOX", List.of());
    }

    /** Generates realistic single-column prose of roughly the requested length. */
    private static String prose(int words) {
        return IntStream.range(0, words)
                .mapToObj(i -> "word" + i)
                .collect(Collectors.joining(" "));
    }

    @Nested
    @DisplayName("counting")
    class Counting {

        @Test
        @DisplayName("counts whitespace-separated tokens")
        void countsWords() {
            assertThat(of("Software engineer with five years", 1).wordCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("treats runs of whitespace as one separator")
        void collapsesWhitespace() {
            assertThat(of("a    b\n\nc\t\td", 1).wordCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("empty and blank text count as zero")
        void handlesEmpty() {
            assertThat(of("", 1).wordCount()).isZero();
            assertThat(of("   \n  ", 1).wordCount()).isZero();
        }

        @Test
        @DisplayName("null text does not throw")
        void handlesNull() {
            ExtractedText extracted = of(null, 1);
            assertThat(extracted.wordCount()).isZero();
            assertThat(extracted.charCount()).isZero();
        }
    }

    @Nested
    @DisplayName("usability")
    class Usability {

        @Test
        @DisplayName("prose is usable")
        void proseIsUsable() {
            assertThat(of(prose(300), 2).isUsable()).isTrue();
        }

        @Test
        @DisplayName("a scan yielding almost nothing is not usable")
        void scanIsNotUsable() {
            // The single most common real-world parse failure: a photo or scan
            // saved as a PDF. Pages exist; text does not.
            assertThat(of("Page 1", 2).isUsable()).isFalse();
        }

        @Test
        @DisplayName("empty text is not usable")
        void emptyIsNotUsable() {
            assertThat(of("", 1).isUsable()).isFalse();
        }
    }

    @Nested
    @DisplayName("quality warnings")
    class Warnings {

        private List<String> codesFor(ExtractedText extracted) {
            return extracted.qualityWarnings().stream().map(ParseWarning::code).toList();
        }

        @Test
        @DisplayName("a page with no text produces NO_TEXT_LAYER")
        void detectsScannedDocument() {
            assertThat(codesFor(of("Resume", 2))).contains(ParseWarning.NO_TEXT_LAYER);
        }

        @Test
        @DisplayName("thin but non-empty text produces SPARSE_TEXT")
        void detectsSparseText() {
            assertThat(codesFor(of(prose(60), 1))).contains(ParseWarning.SPARSE_TEXT);
        }

        @Test
        @DisplayName("NO_TEXT_LAYER and SPARSE_TEXT are mutually exclusive")
        void doesNotDoublyWarn() {
            // Both would be technically true for a near-empty document, and
            // showing two warnings for one problem reads as noise.
            List<String> codes = codesFor(of("Resume", 2));
            assertThat(codes).contains(ParseWarning.NO_TEXT_LAYER)
                    .doesNotContain(ParseWarning.SPARSE_TEXT);
        }

        @Test
        @DisplayName("a long document produces UNUSUALLY_LONG")
        void detectsLongDocument() {
            assertThat(codesFor(of(prose(3000), 6))).contains(ParseWarning.UNUSUALLY_LONG);
        }

        @Test
        @DisplayName("a normal two-page resume produces no warnings")
        void cleanDocumentIsQuiet() {
            // The false-positive guard. Warning on a perfectly good resume
            // trains users to ignore warnings, which costs more than missing one.
            assertThat(of(prose(500), 2).qualityWarnings()).isEmpty();
        }

        @Test
        @DisplayName("extractor warnings are preserved alongside quality ones")
        void preservesExtractorWarnings() {
            ExtractedText extracted = new ExtractedText(
                    prose(500), 2, "TIKA", List.of(ParseWarning.encryptedDocument()));

            assertThat(extracted.qualityWarnings())
                    .extracting(ParseWarning::code)
                    .contains(ParseWarning.ENCRYPTED_DOCUMENT);
        }
    }

    @Nested
    @DisplayName("multi-column detection")
    class MultiColumn {

        @Test
        @DisplayName("detects a gutter between two columns")
        void detectsColumns() {
            // What a two-column extraction actually looks like: content, a run
            // of spaces where the gutter was, then more content.
            String columnar = IntStream.range(0, 20)
                    .mapToObj(i -> "Skills and tools line " + i + "        Experience entry number " + i)
                    .collect(Collectors.joining("\n"));

            assertThat(of(columnar, 1).looksMultiColumn()).isTrue();
        }

        @Test
        @DisplayName("does not flag ordinary single-column prose")
        void ignoresProse() {
            String normal = IntStream.range(0, 20)
                    .mapToObj(i -> "Built and shipped a feature that reduced latency in service " + i)
                    .collect(Collectors.joining("\n"));

            assertThat(of(normal, 1).looksMultiColumn()).isFalse();
        }

        @Test
        @DisplayName("does not flag a document with only a few aligned lines")
        void ignoresOccasionalAlignment() {
            // Dates right-aligned against job titles produce this pattern on a
            // handful of lines. A genuine two-column layout produces it on most.
            String occasional = IntStream.range(0, 20)
                    .mapToObj(i -> i < 3
                            ? "Senior Engineer        2021 - 2024"
                            : "Delivered measurable improvements to the platform in area " + i)
                    .collect(Collectors.joining("\n"));

            assertThat(of(occasional, 1).looksMultiColumn()).isFalse();
        }

        @Test
        @DisplayName("does not flag a very short document")
        void ignoresShortDocuments() {
            // Too little evidence to conclude anything, and a wrong warning on a
            // short document is more visible than on a long one.
            assertThat(of("Name        Title\nEmail        Phone", 1).looksMultiColumn()).isFalse();
        }

        @Test
        @DisplayName("handles null and empty text")
        void handlesEmpty() {
            assertThat(of(null, 1).looksMultiColumn()).isFalse();
            assertThat(of("", 1).looksMultiColumn()).isFalse();
        }
    }
}
