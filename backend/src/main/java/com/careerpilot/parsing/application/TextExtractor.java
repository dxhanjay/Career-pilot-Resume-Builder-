package com.careerpilot.parsing.application;

import com.careerpilot.parsing.domain.ExtractedText;

/**
 * Outbound port for turning document bytes into text.
 *
 * <p>Two implementations, tried in order: PDFBox for PDFs, Tika for everything
 * else and as a fallback when PDFBox fails. Keeping this an interface is what
 * lets the parsing service express "try each extractor until one produces
 * usable text" without knowing anything about either library.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface TextExtractor {

    /**
     * @return the name recorded in {@code resume_parses.parser_name}
     */
    String name();

    /**
     * @return the library version, recorded so that a change in extraction
     *         quality can be attributed to a dependency upgrade rather than
     *         guessed at
     */
    String version();

    /**
     * @param mimeType the type determined from the file's bytes
     * @return whether this extractor handles that format
     */
    boolean supports(String mimeType);

    /**
     * Extracts text.
     *
     * @param content  the raw file bytes
     * @param mimeType the type determined from the bytes
     * @return the extracted text and any structural warnings
     * @throws TextExtractionException if the document cannot be read at all
     */
    ExtractedText extract(byte[] content, String mimeType);

    /**
     * Raised when a document cannot be read.
     *
     * <p>Distinct from "read successfully but contained no text", which is not
     * an exception — it is a valid result that produces a
     * {@link com.careerpilot.parsing.domain.ParseWarning#NO_TEXT_LAYER} warning
     * and a specific, actionable message. Conflating the two would tell a user
     * with a scanned CV that our software broke, rather than that their file
     * cannot be read by screening systems either.
     */
    class TextExtractionException extends RuntimeException {

        /**
         * @param message description of the failure
         * @param cause   underlying library failure
         */
        public TextExtractionException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * @param message description of the failure
         */
        public TextExtractionException(String message) {
            super(message);
        }
    }
}
