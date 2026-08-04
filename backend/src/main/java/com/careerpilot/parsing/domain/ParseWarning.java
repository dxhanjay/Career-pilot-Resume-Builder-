package com.careerpilot.parsing.domain;

/**
 * A structural problem detected while extracting text.
 *
 * <p>Warnings are not failures. Text was extracted, but something about the
 * document predicts that an ATS will read it differently from how a human does
 * — which is the entire premise of the product.
 *
 * <p>This is the mechanism by which risk R1 becomes <em>visible</em> rather than
 * silently producing garbage. A student whose two-column layout scrambled their
 * work history needs to be told that, not handed a low score with no explanation.
 *
 * @param code    stable machine-readable identifier; clients branch on this
 * @param message human-readable explanation, safe to display
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record ParseWarning(String code, String message) {

    /** Two or more text columns detected; reading order may be interleaved. */
    public static final String MULTI_COLUMN_LAYOUT = "MULTI_COLUMN_LAYOUT";

    /** Pages exist but yielded almost no text — probably a scan or an image. */
    public static final String NO_TEXT_LAYER = "NO_TEXT_LAYER";

    /** Very little text overall; likely an image-heavy or graphical CV. */
    public static final String SPARSE_TEXT = "SPARSE_TEXT";

    /** More pages than a resume normally has. */
    public static final String UNUSUALLY_LONG = "UNUSUALLY_LONG";

    /** The primary parser failed and a fallback produced this text. */
    public static final String FALLBACK_PARSER_USED = "FALLBACK_PARSER_USED";

    /** The document is encrypted; some content may be unreadable. */
    public static final String ENCRYPTED_DOCUMENT = "ENCRYPTED_DOCUMENT";

    /**
     * @return a warning about interleaved column text
     */
    public static ParseWarning multiColumnLayout() {
        return new ParseWarning(MULTI_COLUMN_LAYOUT,
                "This looks like a multi-column layout. Applicant tracking systems often read "
                        + "columns as one interleaved stream, which can scramble your experience. "
                        + "A single-column layout is safer.");
    }

    /**
     * @return a warning that the document has no selectable text
     */
    public static ParseWarning noTextLayer() {
        return new ParseWarning(NO_TEXT_LAYER,
                "We could not find any selectable text. This is usually a scanned image or a "
                        + "photo saved as a PDF. Most screening software cannot read these at all — "
                        + "export a text-based PDF from your word processor instead.");
    }

    /**
     * @param wordCount how many words were found
     * @return a warning that very little text was extracted
     */
    public static ParseWarning sparseText(int wordCount) {
        return new ParseWarning(SPARSE_TEXT,
                "We only found %d words. If your resume relies on images, graphics, or text boxes, "
                        + "screening software may not see that content.".formatted(wordCount));
    }

    /**
     * @param pageCount how many pages the document has
     * @return a warning that the document is unusually long
     */
    public static ParseWarning unusuallyLong(int pageCount) {
        return new ParseWarning(UNUSUALLY_LONG,
                "This resume is %d pages. For most early-career roles, one or two pages is expected."
                        .formatted(pageCount));
    }

    /**
     * @param parser the fallback that succeeded
     * @return a warning that the primary extractor failed
     */
    public static ParseWarning fallbackParserUsed(String parser) {
        return new ParseWarning(FALLBACK_PARSER_USED,
                "Our primary reader could not process this file, so we used a fallback (%s). "
                        + "Extraction may be less accurate than usual.".formatted(parser));
    }

    /**
     * @return a warning that the document is encrypted
     */
    public static ParseWarning encryptedDocument() {
        return new ParseWarning(ENCRYPTED_DOCUMENT,
                "This document is password-protected or restricted. Some content may be unreadable, "
                        + "and many screening systems reject protected files outright.");
    }
}
