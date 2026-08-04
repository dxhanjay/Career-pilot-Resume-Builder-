package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.application.TextExtractor;
import com.careerpilot.parsing.domain.ExtractedText;
import com.careerpilot.parsing.domain.ParseWarning;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF text extraction using Apache PDFBox.
 *
 * <p>The primary extractor, and used directly rather than through Tika because
 * PDF is the format that matters most here and PDFBox exposes control that
 * Tika's generic facade hides — notably {@code setSortByPosition}, and the
 * ability to distinguish "a PDF with no text layer" from "a PDF we failed to
 * read". Those look identical through a generic parser and need very different
 * messages.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
@Order(1)
public class PdfBoxTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxTextExtractor.class);

    private static final String PDF_MIME_TYPE = "application/pdf";

    /**
     * Guard against a malicious or pathological document.
     *
     * <p>A PDF can legitimately declare thousands of pages, and a crafted one
     * can declare far more while being a few kilobytes — a decompression bomb.
     * Extracting all of them would exhaust memory on a small container. No real
     * resume approaches this.
     */
    private static final int MAX_PAGES = 30;

    @Override
    public String name() {
        return "PDFBOX";
    }

    @Override
    public String version() {
        // Read from the manifest so an upgrade is reflected automatically. A
        // hardcoded string here would silently become a lie, and this value is
        // what lets a change in extraction quality be attributed to a dependency
        // bump rather than guessed at.
        Package pkg = PDDocument.class.getPackage();
        String implVersion = pkg == null ? null : pkg.getImplementationVersion();
        return implVersion == null ? "unknown" : implVersion;
    }

    @Override
    public boolean supports(String mimeType) {
        return PDF_MIME_TYPE.equalsIgnoreCase(mimeType);
    }

    @Override
    public ExtractedText extract(byte[] content, String mimeType) {
        List<ParseWarning> warnings = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(content)) {

            if (document.isEncrypted()) {
                // Loading succeeded, so the document opened with an empty
                // password. Content may still be restricted, and many screening
                // systems reject protected files outright - worth telling the
                // user even though we got text out.
                warnings.add(ParseWarning.encryptedDocument());
            }

            int pageCount = document.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();

            // Order text by position on the page rather than by the order it
            // happens to appear in the content stream. Those differ constantly:
            // PDF generators emit text in whatever order suits them, so without
            // this a perfectly ordinary single-column CV can come out with the
            // footer in the middle. It costs a sort per page.
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pageCount, MAX_PAGES));

            String text = normalise(stripper.getText(document));

            if (pageCount > MAX_PAGES) {
                log.warn("Truncated extraction at {} of {} pages", MAX_PAGES, pageCount);
            }

            return new ExtractedText(text, pageCount, name(), List.copyOf(warnings));

        } catch (IOException e) {
            // Corrupt, truncated, or genuinely password-protected. A real
            // failure, distinct from a document that simply has no text.
            throw new TextExtractionException("PDFBox could not read this PDF", e);

        } catch (RuntimeException e) {
            // PDFBox raises unchecked exceptions on some malformed documents.
            // Caught so a malformed upload becomes a clean parse failure rather
            // than an unhandled 500 - the input is untrusted by definition.
            throw new TextExtractionException("PDFBox failed on a malformed PDF", e);
        }
    }

    /**
     * Normalises extracted text.
     *
     * <p>Line endings are unified because the source document's platform is
     * unknown and downstream section detection matches on line boundaries. Runs
     * of blank lines collapse because PDF extraction produces them liberally and
     * they would otherwise be counted as structure. Horizontal whitespace is
     * deliberately <em>preserved</em>: the multi-column heuristic in
     * {@code ExtractedText} depends on gutter spacing surviving.
     *
     * @param raw text as the stripper produced it
     * @return normalised text
     */
    private String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                // Non-breaking space and friends confuse tokenisation later.
                .replace(' ', ' ')
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
