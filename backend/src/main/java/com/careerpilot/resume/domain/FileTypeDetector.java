package com.careerpilot.resume.domain;

import java.util.Optional;

/**
 * Identifies an uploaded file by its leading bytes.
 *
 * <p><strong>This is a security control, and the reason it exists is that the
 * two obvious alternatives are both attacker-controlled.</strong>
 *
 * <ul>
 *   <li>The <em>filename extension</em> is whatever the client says it is.
 *       {@code payload.pdf} can contain anything at all.</li>
 *   <li>The <em>{@code Content-Type} header</em> is likewise supplied by the
 *       client. A browser sets it honestly; {@code curl} sets it to whatever
 *       you ask for.</li>
 * </ul>
 *
 * <p>The file's own bytes are the only part of an upload the sender cannot lie
 * about while still producing a file the parser will accept. Checking them is
 * what stops an HTML document with a script payload, or a Windows executable,
 * being stored under a {@code .pdf} name and later served back to a browser.
 *
 * <p>Deliberately in the domain layer with no Spring or servlet imports: it
 * takes a byte array, returns an enum, and is exhaustively unit-testable in
 * microseconds. Security logic that is awkward to test is security logic that
 * goes untested.
 *
 * <p>Note this establishes the <em>container</em> format only. A well-formed PDF
 * can still contain malicious embedded content, which is why files are stored
 * with authenticated delivery and never served inline from our own origin.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class FileTypeDetector {

    /** Bytes needed to identify every supported type. */
    public static final int SIGNATURE_LENGTH = 8;

    /** {@code %PDF} — every PDF begins with this. */
    private static final byte[] PDF_SIGNATURE = { 0x25, 0x50, 0x44, 0x46 };

    /**
     * {@code PK\x03\x04} — the ZIP local file header.
     *
     * <p>A .docx is a ZIP archive, so this signature alone cannot distinguish it
     * from a .zip, a .jar, or a .xlsx. See {@link #detect} for how that
     * ambiguity is handled.
     */
    private static final byte[] ZIP_SIGNATURE = { 0x50, 0x4B, 0x03, 0x04 };

    /** Legacy .doc (OLE2 compound document). Detected in order to reject it clearly. */
    private static final byte[] OLE2_SIGNATURE =
            { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

    private FileTypeDetector() {
    }

    /**
     * A file format the platform accepts.
     */
    public enum FileType {

        /** Portable Document Format. */
        PDF("application/pdf", ".pdf"),

        /** Office Open XML word processing document. */
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");

        private final String mimeType;
        private final String extension;

        FileType(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        /**
         * @return the canonical MIME type, as determined from the bytes rather
         *         than from anything the client claimed
         */
        public String getMimeType() {
            return mimeType;
        }

        /**
         * @return the canonical file extension, including the dot
         */
        public String getExtension() {
            return extension;
        }
    }

    /**
     * Identifies a file from its leading bytes.
     *
     * <p>A {@code .docx} and a plain {@code .zip} share the same signature,
     * because a {@code .docx} <em>is</em> a ZIP archive. Distinguishing them
     * properly means opening the archive and looking for
     * {@code word/document.xml}, which is Apache Tika's job in Phase 6.
     *
     * <p>Here, a ZIP signature is accepted as {@code DOCX} on the strength of
     * the declared filename extension. That is a deliberate, narrow use of
     * client-supplied information: the byte check has already established the
     * file is a real ZIP container, so the worst outcome is that a genuine
     * {@code .zip} renamed to {@code .docx} is stored and then fails to parse
     * in Phase 6 — a clean failure, not a security hole. The dangerous case,
     * an executable or an HTML document, is rejected here regardless of name.
     *
     * @param header       the first bytes of the file; at least
     *                     {@link #SIGNATURE_LENGTH} where available
     * @param filename     the client-supplied filename, used only to
     *                     disambiguate ZIP containers
     * @return the detected type, or empty if the file is not an accepted format
     */
    public static Optional<FileType> detect(byte[] header, String filename) {
        if (header == null || header.length < 4) {
            return Optional.empty();
        }

        if (startsWith(header, PDF_SIGNATURE)) {
            return Optional.of(FileType.PDF);
        }

        if (startsWith(header, ZIP_SIGNATURE)) {
            boolean claimsDocx = filename != null
                    && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".docx");
            return claimsDocx ? Optional.of(FileType.DOCX) : Optional.empty();
        }

        // Recognised, and deliberately rejected. Detecting legacy .doc lets the
        // API say "convert this to PDF or .docx" instead of the unhelpful
        // "unsupported file type" a user would otherwise get for a file their
        // word processor considers perfectly normal.
        if (startsWith(header, OLE2_SIGNATURE)) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    /**
     * Whether the bytes look like a legacy {@code .doc} document.
     *
     * <p>Used only to produce a more useful error message.
     *
     * @param header the first bytes of the file
     * @return {@code true} if this is an OLE2 compound document
     */
    public static boolean isLegacyWordDocument(byte[] header) {
        return startsWith(header, OLE2_SIGNATURE);
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
