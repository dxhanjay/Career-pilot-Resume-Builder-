package com.careerpilot.resume.application;

import com.careerpilot.common.exception.ApiException;
import com.careerpilot.common.exception.ErrorCode;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.config.properties.ResumeProperties;
import com.careerpilot.config.properties.StorageProperties;
import com.careerpilot.resume.application.dto.ResumeResponse;
import com.careerpilot.resume.domain.Resume;
import com.careerpilot.resume.domain.StorageProvider;
import com.careerpilot.resume.infrastructure.ResumeRepository;
import com.careerpilot.storage.FileStorage;
import com.careerpilot.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResumeService}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeService")
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private FileStorage fileStorage;

    private ResumeService resumeService;

    private final UUID userId = UUID.randomUUID();

    private final ResumeProperties resumeProperties =
            new ResumeProperties(5 * 1024 * 1024, 10, 30);

    private final StorageProperties storageProperties =
            new StorageProperties("local", null, 300);

    /** Minimal but genuine PDF content. */
    private static byte[] pdf() {
        return "%PDF-1.7\nfake resume content".getBytes(StandardCharsets.ISO_8859_1);
    }

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(
                resumeRepository, fileStorage, resumeProperties, storageProperties);

        lenient().when(fileStorage.store(any(), any(), anyString(), anyString()))
                .thenReturn(new StoredFile("resumes/x/y", "https://cdn/x", 128, StorageProvider.LOCAL));
        lenient().when(resumeRepository.findMaxVersionByUserId(userId)).thenReturn(Optional.empty());
        lenient().when(resumeRepository.countByUserId(userId)).thenReturn(0L);
        lenient().when(resumeRepository.findByUserIdAndChecksum(eq(userId), anyString()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("upload validation")
    class UploadValidation {

        @Test
        @DisplayName("rejects an empty file")
        void rejectsEmpty() {
            assertThatThrownBy(() -> resumeService.upload(userId, new byte[0], "resume.pdf", false))
                    .isInstanceOf(ApiException.class);

            verify(fileStorage, never()).store(any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("rejects a file over the size limit with 413")
        void rejectsOversized() {
            byte[] tooBig = new byte[6 * 1024 * 1024];
            System.arraycopy(pdf(), 0, tooBig, 0, pdf().length);

            assertThatThrownBy(() -> resumeService.upload(userId, tooBig, "resume.pdf", false))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));

            // Size is checked before hashing and before storage: rejecting early
            // costs nothing, hashing 6 MB we were going to discard costs CPU.
            verify(fileStorage, never()).store(any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("⭐ rejects HTML content disguised with a .pdf name")
        void rejectsDisguisedHtml() {
            byte[] html = "<!DOCTYPE html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> resumeService.upload(userId, html, "resume.pdf", false))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

            // Nothing reaches storage. Storing first and validating after would
            // put unvalidated content in the bucket.
            verify(fileStorage, never()).store(any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("gives legacy .doc a specific, actionable message")
        void legacyDocGetsUsefulMessage() {
            byte[] ole2 = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

            assertThatThrownBy(() -> resumeService.upload(userId, ole2, "resume.doc", false))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining(".doc")
                    .hasMessageContaining("PDF");
        }

        @Test
        @DisplayName("rejects once the per-user quota is reached")
        void rejectsOverQuota() {
            when(resumeRepository.countByUserId(userId)).thenReturn(10L);

            assertThatThrownBy(() -> resumeService.upload(userId, pdf(), "resume.pdf", false))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("limit");

            verify(fileStorage, never()).store(any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("rejects identical content with 409 (FR-RES-05)")
        void rejectsDuplicate() {
            when(resumeRepository.findByUserIdAndChecksum(eq(userId), anyString()))
                    .thenReturn(Optional.of(existingResume()));

            assertThatThrownBy(() -> resumeService.upload(userId, pdf(), "resume.pdf", false))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT));

            // Avoids paying to store, parse, and analyse a file we already hold.
            verify(fileStorage, never()).store(any(), any(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("upload success")
    class UploadSuccess {

        @Test
        @DisplayName("stores the DETECTED mime type, never the claimed one")
        void storesDetectedMimeType() {
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            ResumeResponse result = resumeService.upload(userId, pdf(), "resume.pdf", false);

            assertThat(result.mimeType()).isEqualTo("application/pdf");

            ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
            verify(resumeRepository).save(saved.capture());
            assertThat(saved.getValue().getMimeType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("strips path components from the filename")
        void sanitisesPathTraversal() {
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            resumeService.upload(userId, pdf(), "../../../etc/passwd.pdf", false);

            ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
            verify(resumeRepository).save(saved.capture());

            assertThat(saved.getValue().getOriginalFilename())
                    .doesNotContain("..")
                    .doesNotContain("/")
                    .isEqualTo("passwd.pdf");
        }

        @Test
        @DisplayName("neutralises characters that carry meaning in other contexts")
        void sanitisesDangerousCharacters() {
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            resumeService.upload(userId, pdf(), "<script>alert(1)</script>.pdf", false);

            ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
            verify(resumeRepository).save(saved.capture());

            // The filename is echoed in JSON and rendered in a browser.
            assertThat(saved.getValue().getOriginalFilename())
                    .doesNotContain("<")
                    .doesNotContain(">");
        }

        @Test
        @DisplayName("survives filenames the host filesystem would reject")
        void doesNotDependOnHostPathRules() {
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            // Every one of these is legal on Linux and illegal on Windows.
            // Sanitising untrusted input must not depend on where the code runs,
            // and must never throw: an earlier implementation used Paths.get()
            // and raised InvalidPathException on a developer machine while
            // working fine on the Railway container.
            for (String hostile : new String[] {
                    "report<final>.pdf", "a|b.pdf", "quote\".pdf", "star*.pdf", "what?.pdf" }) {

                assertThat(resumeService.upload(userId, pdf(), hostile, false).originalFilename())
                        .as("filename: %s", hostile)
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("the first upload becomes primary automatically")
        void firstUploadIsPrimary() {
            when(resumeRepository.countByUserId(userId)).thenReturn(0L);
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            ResumeResponse result = resumeService.upload(userId, pdf(), "resume.pdf", false);

            // A user with exactly one resume and no primary is a state every
            // downstream feature would have to special-case for no reason.
            assertThat(result.primary()).isTrue();
        }

        @Test
        @DisplayName("a later upload is not primary unless asked")
        void laterUploadIsNotPrimaryByDefault() {
            when(resumeRepository.countByUserId(userId)).thenReturn(3L);
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            ResumeResponse result = resumeService.upload(userId, pdf(), "resume.pdf", false);

            assertThat(result.primary()).isFalse();
        }

        @Test
        @DisplayName("clears the previous primary before setting a new one")
        void clearsPreviousPrimary() {
            when(resumeRepository.countByUserId(userId)).thenReturn(3L);
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            resumeService.upload(userId, pdf(), "resume.pdf", true);

            // A partial unique index permits one primary per user, so setting
            // without clearing would violate the constraint.
            verify(resumeRepository).clearPrimaryForUser(userId);
        }

        @Test
        @DisplayName("increments the version per user")
        void incrementsVersion() {
            when(resumeRepository.findMaxVersionByUserId(userId)).thenReturn(Optional.of((short) 4));
            when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

            ResumeResponse result = resumeService.upload(userId, pdf(), "resume.pdf", false);

            // Powers the score-trend view that shows a student their resume
            // improving, which is the retention moment.
            assertThat(result.version()).isEqualTo((short) 5);
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("another user's resume is 404, not 403")
        void otherUsersResumeIsNotFound() {
            UUID resumeId = UUID.randomUUID();
            when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

            // Distinguishing "exists but forbidden" from "does not exist" would
            // confirm the existence of other users' data to anyone enumerating.
            assertThatThrownBy(() -> resumeService.get(resumeId, userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("download URL generation verifies ownership first")
        void downloadChecksOwnership() {
            UUID resumeId = UUID.randomUUID();
            when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resumeService.getDownloadUrl(resumeId, userId))
                    .isInstanceOf(ResourceNotFoundException.class);

            // No URL is minted for a resource the caller does not own. Once
            // minted, the URL carries no identity of its own.
            verify(fileStorage, never()).generateSignedUrl(anyString(), any());
        }
    }

    @Nested
    @DisplayName("deletion and purge")
    class Deletion {

        @Test
        @DisplayName("delete is soft, leaving the stored file in place")
        void deleteIsSoft() {
            Resume resume = existingResume();
            when(resumeRepository.findByIdAndUserId(resume.getId(), userId))
                    .thenReturn(Optional.of(resume));

            resumeService.delete(resume.getId(), userId);

            assertThat(resume.isDeleted()).isTrue();
            // The 30-day window is what allows an accidental deletion to be
            // reversed by support.
            verify(fileStorage, never()).delete(anyString());
        }

        @Test
        @DisplayName("⭐ purge removes the stored file BEFORE the row")
        void purgeDeletesFileBeforeRow() {
            Resume resume = existingResume();
            when(resumeRepository.findPurgeable(any())).thenReturn(List.of(resume));

            resumeService.purgeDeleted();

            // Order is the assertion. Deleting the row first would orphan the
            // file permanently: nothing would remain recording that it exists,
            // so it would sit in storage forever - breaching the deletion
            // commitment while appearing to satisfy it.
            var inOrder = org.mockito.Mockito.inOrder(fileStorage, resumeRepository);
            inOrder.verify(fileStorage).delete(resume.getStoragePublicId());
            inOrder.verify(resumeRepository).delete(resume);
        }

        @Test
        @DisplayName("one failing file does not abort the whole purge batch")
        void purgeContinuesAfterFailure() {
            Resume failing = existingResume();
            Resume succeeding = existingResume();

            when(resumeRepository.findPurgeable(any())).thenReturn(List.of(failing, succeeding));
            org.mockito.Mockito.doThrow(new RuntimeException("provider down"))
                    .when(fileStorage).delete(failing.getStoragePublicId());

            int purged = resumeService.purgeDeleted();

            // One unreachable object must not prevent every other user's data
            // from being deleted on time.
            assertThat(purged).isEqualTo(1);
            verify(resumeRepository).delete(succeeding);
            verify(resumeRepository, never()).delete(failing);
        }
    }

    // --- helpers -----------------------------------------------------------

    private Resume existingResume() {
        Resume resume = new Resume(
                userId, "resume.pdf", StorageProvider.LOCAL,
                "resumes/" + UUID.randomUUID(), "https://cdn/x",
                "application/pdf", 128, "checksum", (short) 1);
        org.springframework.test.util.ReflectionTestUtils.setField(resume, "id", UUID.randomUUID());
        return resume;
    }
}
