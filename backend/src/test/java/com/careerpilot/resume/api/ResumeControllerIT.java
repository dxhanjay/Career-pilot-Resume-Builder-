package com.careerpilot.resume.api;

import com.careerpilot.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the resume endpoints.
 *
 * <p>Runs the whole stack: real multipart dispatch, the real security chain,
 * real Flyway migrations, real PostgreSQL, and {@code LocalFileStorage} writing
 * to a temporary directory.
 *
 * <p><strong>That last part is the payoff from the {@code FileStorage} port.</strong>
 * Without a local adapter, every test here would need a Cloudinary account and
 * an API key in CI — so in practice they would not exist, and the upload path
 * would be the least-tested code in the application while handling the
 * least-trusted input.
 *
 * <p>The cross-user test is the one worth reading. IDOR is the vulnerability
 * most likely to actually appear in this codebase: every resource is user-owned
 * and addressed by UUID, so one repository call that omits the owner exposes
 * another person's CV.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@AutoConfigureMockMvc
@DisplayName("Resume API")
class ResumeControllerIT extends AbstractIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    private String accessToken;

    ResumeControllerIT(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void signIn() throws Exception {
        accessToken = registerAndLogin();
    }

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("accepts a PDF and returns 201")
        void acceptsPdf() throws Exception {
            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(pdfFile("resume.pdf"))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                    .andExpect(jsonPath("$.data.version").value(1))
                    .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                    // First upload becomes primary automatically.
                    .andExpect(jsonPath("$.data.primary").value(true));
        }

        @Test
        @DisplayName("never exposes the storage handle")
        void hidesStorageHandle() throws Exception {
            MvcResult result = mockMvc.perform(multipart("/api/v1/resumes")
                            .file(pdfFile("resume.pdf"))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Exposing these would let a client address the storage provider
            // directly, bypassing the signed-URL flow that is the access control.
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("storagePublicId")
                    .doesNotContain("storageUrl");
        }

        @Test
        @DisplayName("⭐ rejects HTML disguised as a PDF with 415")
        void rejectsDisguisedContent() throws Exception {
            MockMultipartFile disguised = new MockMultipartFile(
                    "file", "resume.pdf", "application/pdf",
                    "<!DOCTYPE html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

            // Both the filename and the Content-Type claim PDF. Only the bytes
            // tell the truth, and only the bytes are believed.
            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(disguised)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.data.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        @DisplayName("rejects identical content with 409")
        void rejectsDuplicate() throws Exception {
            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(pdfFile("resume.pdf"))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isCreated());

            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(pdfFile("resume-copy.pdf"))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("requires authentication")
        void requiresAuth() throws Exception {
            mockMvc.perform(multipart("/api/v1/resumes").file(pdfFile("resume.pdf")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("⭐ another user's resume returns 404, not 403")
        void cannotReadAnotherUsersResume() throws Exception {
            String resumeId = uploadAndGetId(accessToken);

            String otherUsersToken = registerAndLogin();

            // 403 would confirm the resource exists to anyone enumerating UUIDs.
            // Both "does not exist" and "not yours" must be indistinguishable.
            mockMvc.perform(get("/api/v1/resumes/" + resumeId)
                            .header("Authorization", "Bearer " + otherUsersToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("another user cannot obtain a download link")
        void cannotDownloadAnotherUsersResume() throws Exception {
            String resumeId = uploadAndGetId(accessToken);
            String otherUsersToken = registerAndLogin();

            mockMvc.perform(get("/api/v1/resumes/" + resumeId + "/download")
                            .header("Authorization", "Bearer " + otherUsersToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("another user cannot delete it")
        void cannotDeleteAnotherUsersResume() throws Exception {
            String resumeId = uploadAndGetId(accessToken);
            String otherUsersToken = registerAndLogin();

            mockMvc.perform(delete("/api/v1/resumes/" + resumeId)
                            .header("Authorization", "Bearer " + otherUsersToken))
                    .andExpect(status().isNotFound());

            // And it is still there for its actual owner.
            mockMvc.perform(get("/api/v1/resumes/" + resumeId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("the list shows only your own resumes")
        void listIsScopedToTheCaller() throws Exception {
            uploadAndGetId(accessToken);

            String otherUsersToken = registerAndLogin();

            mockMvc.perform(get("/api/v1/resumes")
                            .header("Authorization", "Bearer " + otherUsersToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("returns a download link with an expiry")
        void downloadLinkHasExpiry() throws Exception {
            String resumeId = uploadAndGetId(accessToken);

            mockMvc.perform(get("/api/v1/resumes/" + resumeId + "/download")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty())
                    // Returned so the client can refresh rather than reuse a
                    // dead URL and surface a provider error it cannot interpret.
                    .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.filename").value("resume.pdf"));
        }

        @Test
        @DisplayName("delete is soft: the resume disappears from the list")
        void deleteRemovesFromList() throws Exception {
            String resumeId = uploadAndGetId(accessToken);

            mockMvc.perform(delete("/api/v1/resumes/" + resumeId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/resumes/" + resumeId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/v1/resumes")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        @DisplayName("a deleted checksum can be uploaded again")
        void deletedContentCanBeReuploaded() throws Exception {
            String resumeId = uploadAndGetId(accessToken);

            mockMvc.perform(delete("/api/v1/resumes/" + resumeId)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // The duplicate index is partial on deleted_at, so a soft-deleted
            // row must not block re-uploading the same file. Without the partial
            // clause a user who deleted a resume could never upload it again.
            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(pdfFile("resume.pdf"))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("versions increment per user")
        void versionsIncrement() throws Exception {
            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(pdfFile("v1.pdf"))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(jsonPath("$.data.version").value(1));

            MockMultipartFile second = new MockMultipartFile(
                    "file", "v2.pdf", "application/pdf",
                    "%PDF-1.7\ndifferent content".getBytes(StandardCharsets.ISO_8859_1));

            mockMvc.perform(multipart("/api/v1/resumes")
                            .file(second)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(jsonPath("$.data.version").value(2));
        }
    }

    // --- helpers -----------------------------------------------------------

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile(
                "file", filename, "application/pdf",
                "%PDF-1.7\nfake resume content".getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Registers a fresh account and returns its access token.
     *
     * <p>A new address per call, because the Testcontainers database is shared
     * across the class. A fixed address would make tests order-dependent, which
     * is the least debuggable kind of flake.
     *
     * @return a bearer token for a brand-new user
     */
    private String registerAndLogin() throws Exception {
        String email = "resume-" + UUID.randomUUID() + "@example.com";
        String credentials = """
                {"email":"%s","password":"a-long-enough-password"}""".formatted(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-long-enough-password","fullName":"Resume Test"}"""
                                .formatted(email)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private String uploadAndGetId(String token) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/resumes")
                        .file(pdfFile("resume.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }
}
