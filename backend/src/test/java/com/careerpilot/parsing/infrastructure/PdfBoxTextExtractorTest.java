package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.application.TextExtractor;
import com.careerpilot.parsing.domain.ExtractedText;
import com.careerpilot.parsing.domain.ParseWarning;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests for {@link PdfBoxTextExtractor}.
 *
 * <p>Builds real PDFs with PDFBox and reads them back with the extractor. That
 * matters more than it might appear: the alternative is checking in binary
 * fixtures, which nobody can review in a diff and which quietly stop
 * representing anything when the library is upgraded. Generating them means the
 * test exercises the actual library on actual PDF bytes, with no Docker and no
 * fixture files.
 *
 * <p>What this cannot cover is the messy real world — PDFs produced by Word,
 * LaTeX, Canva, and a dozen resume builders, each with its own quirks. Those
 * need a corpus, which is Phase 6b's problem.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("PdfBoxTextExtractor")
class PdfBoxTextExtractorTest {

    private final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

    /**
     * Builds a PDF containing the given lines, one page per element.
     *
     * @param pages each element is one page; lines within a page are separated by {@code \n}
     * @return the PDF as bytes
     */
    private static byte[] pdfWith(String... pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String pageText : pages) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.setLeading(14);
                    content.newLineAtOffset(50, 750);
                    for (String line : pageText.split("\n")) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    @Nested
    @DisplayName("extraction")
    class Extraction {

        @Test
        @DisplayName("recovers the text that was written")
        void roundTrip() throws IOException {
            byte[] pdf = pdfWith("Aditi Sharma\nSoftware Engineer\nSpring Boot, PostgreSQL");

            ExtractedText result = extractor.extract(pdf, "application/pdf");

            assertThat(result.rawText())
                    .contains("Aditi Sharma")
                    .contains("Software Engineer")
                    .contains("Spring Boot, PostgreSQL");
            assertThat(result.parser()).isEqualTo("PDFBOX");
            assertThat(result.pageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("counts pages")
        void countsPages() throws IOException {
            byte[] pdf = pdfWith("Page one content", "Page two content", "Page three content");

            ExtractedText result = extractor.extract(pdf, "application/pdf");

            assertThat(result.pageCount()).isEqualTo(3);
            assertThat(result.rawText()).contains("Page one").contains("Page three");
        }

        @Test
        @DisplayName("normalises line endings")
        void normalisesLineEndings() throws IOException {
            byte[] pdf = pdfWith("Line one\nLine two\nLine three");

            ExtractedText result = extractor.extract(pdf, "application/pdf");

            // Downstream section detection matches on line boundaries, so mixed
            // endings from an unknown source platform would break it.
            assertThat(result.rawText()).doesNotContain("\r");
        }

        @Test
        @DisplayName("reports a real library version, not a placeholder")
        void reportsVersion() {
            // Recorded on every parse row so a change in extraction quality can
            // be attributed to a dependency upgrade rather than guessed at. A
            // hardcoded string here would silently become a lie.
            assertThat(extractor.version())
                    .isNotEqualTo("unknown")
                    .startsWith("3.");
        }
    }

    @Nested
    @DisplayName("format support")
    class Support {

        @Test
        @DisplayName("handles PDF and nothing else")
        void supportsOnlyPdf() {
            assertThat(extractor.supports("application/pdf")).isTrue();
            assertThat(extractor.supports("APPLICATION/PDF")).isTrue();
            assertThat(extractor.supports(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("malformed input")
    class Malformed {

        @Test
        @DisplayName("a corrupt PDF raises TextExtractionException, not an unhandled error")
        void corruptPdfFails() {
            byte[] corrupt = "%PDF-1.7 this is not actually a pdf".getBytes(StandardCharsets.ISO_8859_1);

            // Uploads are untrusted by definition. A malformed one must become a
            // clean parse failure the user can act on, never a 500.
            assertThatThrownBy(() -> extractor.extract(corrupt, "application/pdf"))
                    .isInstanceOf(TextExtractor.TextExtractionException.class);
        }

        @Test
        @DisplayName("empty content fails cleanly")
        void emptyFails() {
            assertThatThrownBy(() -> extractor.extract(new byte[0], "application/pdf"))
                    .isInstanceOf(TextExtractor.TextExtractionException.class);
        }
    }

    @Nested
    @DisplayName("integration with quality heuristics")
    class QualityHeuristics {

        @Test
        @DisplayName("a PDF with no text is flagged as having no text layer")
        void emptyPdfIsFlagged() throws IOException {
            // A page with nothing written on it is the closest analogue to a
            // scanned image that can be produced without a real scan: pages
            // exist, text does not.
            try (PDDocument document = new PDDocument();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                document.addPage(new PDPage());
                document.addPage(new PDPage());
                document.save(out);

                ExtractedText result = extractor.extract(out.toByteArray(), "application/pdf");

                assertThat(result.isUsable()).isFalse();

                List<String> codes = result.qualityWarnings().stream()
                        .map(ParseWarning::code).toList();
                assertThat(codes).contains(ParseWarning.NO_TEXT_LAYER);
            }
        }

        @Test
        @DisplayName("a normal resume produces usable text and no warnings")
        void normalResumeIsClean() throws IOException {
            StringBuilder page = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                page.append("Delivered measurable improvements to the platform in area ")
                        .append(i).append('\n');
            }

            ExtractedText result = extractor.extract(pdfWith(page.toString()), "application/pdf");

            assertThat(result.isUsable()).isTrue();
            // The false-positive guard: warning on a good resume trains users to
            // ignore warnings, which costs more than missing one.
            assertThat(result.qualityWarnings()).isEmpty();
        }
    }
}
