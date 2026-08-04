package com.careerpilot.resume.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FileTypeDetector}.
 *
 * <p>This class guards the boundary where the least-trusted input in the whole
 * application arrives: an arbitrary binary file. The rejection tests matter more
 * than the acceptance tests — accepting a valid PDF failing would be noticed
 * within minutes of a user trying it, whereas accepting a disguised HTML
 * document would be noticed only after it caused harm.
 *
 * <p>No Spring, no I/O: bytes in, enum out, microseconds per case.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("FileTypeDetector")
class FileTypeDetectorTest {

    @Nested
    @DisplayName("accepts genuine files")
    class Accepts {

        @Test
        @DisplayName("a PDF, identified by its %PDF header")
        void detectsPdf() {
            byte[] pdf = "%PDF-1.7\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1);

            assertThat(FileTypeDetector.detect(pdf, "resume.pdf"))
                    .contains(FileTypeDetector.FileType.PDF);
        }

        @Test
        @DisplayName("a PDF regardless of what the filename claims")
        void pdfWinsOverFilename() {
            byte[] pdf = "%PDF-1.4 content".getBytes(StandardCharsets.ISO_8859_1);

            // The bytes are authoritative. A real PDF named .docx is still a PDF,
            // and treating it as one is correct.
            assertThat(FileTypeDetector.detect(pdf, "resume.docx"))
                    .contains(FileTypeDetector.FileType.PDF);
        }

        @Test
        @DisplayName("a .docx, identified by ZIP header plus extension")
        void detectsDocx() {
            byte[] zip = { 0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00 };

            assertThat(FileTypeDetector.detect(zip, "resume.docx"))
                    .contains(FileTypeDetector.FileType.DOCX);
        }

        @Test
        @DisplayName("a .docx whose extension is upper-case")
        void docxExtensionIsCaseInsensitive() {
            byte[] zip = { 0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00 };

            assertThat(FileTypeDetector.detect(zip, "RESUME.DOCX"))
                    .contains(FileTypeDetector.FileType.DOCX);
        }
    }

    @Nested
    @DisplayName("rejects disguised content")
    class RejectsDisguised {

        @Test
        @DisplayName("an HTML document named .pdf")
        void rejectsHtmlNamedPdf() {
            byte[] html = "<!DOCTYPE html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

            // The case the whole class exists for. Trusting the extension here
            // would store an HTML document that a browser may later render,
            // executing its script in our origin's context.
            assertThat(FileTypeDetector.detect(html, "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("a Windows executable named .pdf")
        void rejectsExecutableNamedPdf() {
            byte[] exe = { 0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00 };  // MZ

            assertThat(FileTypeDetector.detect(exe, "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("an ELF binary named .pdf")
        void rejectsElfNamedPdf() {
            byte[] elf = { 0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00 };  // .ELF

            assertThat(FileTypeDetector.detect(elf, "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("a plain ZIP archive named .zip")
        void rejectsPlainZip() {
            byte[] zip = { 0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00 };

            // Same signature as .docx, because a .docx IS a ZIP. Without a .docx
            // extension there is nothing to suggest it is a document, so it is
            // refused rather than stored and failed on later.
            assertThat(FileTypeDetector.detect(zip, "archive.zip")).isEmpty();
        }

        @Test
        @DisplayName("a JPEG named .pdf")
        void rejectsImage() {
            byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46 };

            assertThat(FileTypeDetector.detect(jpeg, "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("plain text named .pdf")
        void rejectsPlainText() {
            byte[] text = "Dear hiring manager,".getBytes(StandardCharsets.UTF_8);

            assertThat(FileTypeDetector.detect(text, "resume.pdf")).isEmpty();
        }
    }

    @Nested
    @DisplayName("rejects legacy .doc with a usable signal")
    class LegacyDoc {

        private final byte[] ole2 =
                { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

        @Test
        @DisplayName("is not an accepted type")
        void rejected() {
            assertThat(FileTypeDetector.detect(ole2, "resume.doc")).isEmpty();
        }

        @Test
        @DisplayName("is identifiable, so the API can say 'convert to PDF' instead of 'unsupported'")
        void identifiable() {
            // A user whose word processor considers this file perfectly normal
            // deserves better than a generic rejection.
            assertThat(FileTypeDetector.isLegacyWordDocument(ole2)).isTrue();
        }

        @Test
        @DisplayName("a PDF is not mistaken for legacy .doc")
        void pdfIsNotOle2() {
            byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1);
            assertThat(FileTypeDetector.isLegacyWordDocument(pdf)).isFalse();
        }
    }

    @Nested
    @DisplayName("handles malformed input without throwing")
    class Malformed {

        @Test
        @DisplayName("null content")
        void nullContent() {
            assertThat(FileTypeDetector.detect(null, "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("empty content")
        void emptyContent() {
            assertThat(FileTypeDetector.detect(new byte[0], "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("content shorter than any signature")
        void tooShort() {
            // A 3-byte upload must not cause an ArrayIndexOutOfBounds. It is the
            // simplest possible malicious input.
            assertThat(FileTypeDetector.detect(new byte[] { 0x25, 0x50, 0x44 }, "resume.pdf")).isEmpty();
        }

        @Test
        @DisplayName("null filename alongside PDF content")
        void nullFilenameWithPdf() {
            byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1);

            // A PDF needs no filename hint.
            assertThat(FileTypeDetector.detect(pdf, null))
                    .contains(FileTypeDetector.FileType.PDF);
        }

        @Test
        @DisplayName("null filename alongside ZIP content")
        void nullFilenameWithZip() {
            byte[] zip = { 0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00 };

            // A ZIP does need one, and must not NPE without it.
            assertThat(FileTypeDetector.detect(zip, null)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "   ", "no-extension", ".pdf", "resume.pdf.exe" })
        @DisplayName("odd filenames alongside non-matching content")
        void oddFilenames(String filename) {
            byte[] garbage = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 };

            assertThat(FileTypeDetector.detect(garbage, filename)).isEmpty();
        }
    }

    @Nested
    @DisplayName("type metadata")
    class Metadata {

        @Test
        @DisplayName("carries canonical MIME types, not client-supplied ones")
        void mimeTypes() {
            assertThat(FileTypeDetector.FileType.PDF.getMimeType()).isEqualTo("application/pdf");
            assertThat(FileTypeDetector.FileType.DOCX.getMimeType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }

        @Test
        @DisplayName("signature length covers every supported signature")
        void signatureLength() {
            // OLE2 is the longest at 8 bytes. If a longer signature is added and
            // this constant is not raised, detection silently stops working for
            // it - the header slice would be too short to match.
            assertThat(FileTypeDetector.SIGNATURE_LENGTH).isGreaterThanOrEqualTo(8);
        }

        @Test
        @DisplayName("detect returns Optional rather than null")
        void returnsOptional() {
            Optional<FileTypeDetector.FileType> result =
                    FileTypeDetector.detect(new byte[] { 0, 0, 0, 0 }, "x.pdf");

            assertThat(result).isNotNull().isEmpty();
        }
    }
}
