package com.careerpilot.parsing.domain;

/**
 * Truncates extracted text to its column width.
 *
 * <p>Everything the parser writes is derived from an uploaded file, which is
 * untrusted input. A resume containing a 900-character "institution" is
 * malformed rather than malicious, but the effect is the same either way: an
 * over-length insert aborts the transaction and loses the entire parse,
 * including the fields that were extracted correctly.
 *
 * <p>Truncating turns that into one short value and a complete parse. The
 * alternative — validating and rejecting — would discard good data because one
 * field was odd.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
final class ColumnWidths {

    private ColumnWidths() {
    }

    /**
     * @param value the extracted text, may be {@code null}
     * @param max   the column width
     * @return the value, truncated to fit
     */
    static String fit(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
