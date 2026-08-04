package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.application.TextExtractor;
import com.careerpilot.parsing.domain.ExtractedText;
import com.careerpilot.parsing.domain.ParseWarning;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Text extraction using Apache Tika.
 *
 * <p>Serves two roles: the <strong>only</strong> path for {@code .docx}, and the
 * <strong>fallback</strong> when PDFBox fails on a PDF. The second matters more
 * than it sounds — PDFBox and Tika's PDF handling fail on different documents,
 * so trying both converts a class of hard failures into successes at no cost
 * beyond one extra attempt on an already-failing file.
 *
 * <p>Ordered after {@link PdfBoxTextExtractor} so PDFBox is tried first for PDFs.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
@Order(2)
public class TikaTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaTextExtractor.class);

    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * Upper bound on extracted characters.
     *
     * <p>Tika's default is 100,000, and passing -1 for "unlimited" is how a
     * decompression bomb — a small archive that expands to gigabytes — takes the
     * container down. One million characters is roughly 200 pages of prose, far
     * beyond any resume, and bounded.
     */
    private static final int MAX_CHARACTERS = 1_000_000;

    @Override
    public String name() {
        return "TIKA";
    }

    @Override
    public String version() {
        Package pkg = AutoDetectParser.class.getPackage();
        String implVersion = pkg == null ? null : pkg.getImplementationVersion();
        return implVersion == null ? "unknown" : implVersion;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Claims both accepted formats. For PDFs it is only reached after PDFBox
     * has already failed — the parsing service tries extractors in order.
     */
    @Override
    public boolean supports(String mimeType) {
        return DOCX_MIME_TYPE.equalsIgnoreCase(mimeType)
                || "application/pdf".equalsIgnoreCase(mimeType);
    }

    @Override
    public ExtractedText extract(byte[] content, String mimeType) {
        List<ParseWarning> warnings = new ArrayList<>();

        try (InputStream stream = new ByteArrayInputStream(content)) {

            BodyContentHandler handler = new BodyContentHandler(MAX_CHARACTERS);
            Metadata metadata = new Metadata();

            // A fresh parser per call. AutoDetectParser is not documented as
            // thread-safe, and the poller may run extractions concurrently as
            // the executor grows. Constructing one is cheap; a shared mutable
            // parser producing interleaved output would not be.
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());

            String text = normalise(handler.toString());
            int pageCount = readPageCount(metadata);

            return new ExtractedText(text, pageCount, name(), List.copyOf(warnings));

        } catch (IOException | TikaException e) {
            throw new TextExtractionException("Tika could not read this document", e);

        } catch (SAXException e) {
            // Thrown when the character limit is hit. The text collected so far
            // is discarded by the handler, so this is a genuine failure rather
            // than a truncation we can work with.
            log.warn("Tika extraction exceeded the character limit");
            throw new TextExtractionException("Document is too large to process", e);

        } catch (RuntimeException e) {
            // Tika delegates to format-specific parsers that raise unchecked
            // exceptions on malformed input. Untrusted input must not produce a
            // 500.
            throw new TextExtractionException("Tika failed on a malformed document", e);
        }
    }

    /**
     * Reads the page count from document metadata.
     *
     * <p>{@code .docx} does not reliably carry one — the value is written by the
     * authoring application and is often absent or stale, because page count
     * depends on rendering. Zero means "unknown", which
     * {@code ExtractedText.qualityWarnings()} treats differently from a real
     * page count: it will not claim a document has no text layer when it has no
     * pages to speak of.
     *
     * @param metadata Tika's extracted metadata
     * @return the page count, or 0 if unknown
     */
    private int readPageCount(Metadata metadata) {
        for (String key : new String[] { "xmpTPg:NPages", "Page-Count", "meta:page-count" }) {
            String value = metadata.get(key);
            if (value != null) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    // Metadata is written by other software and is not trusted.
                }
            }
        }
        return 0;
    }

    private String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
