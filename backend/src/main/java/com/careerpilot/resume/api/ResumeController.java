package com.careerpilot.resume.api;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.resume.application.ResumeService;
import com.careerpilot.resume.application.dto.DownloadUrlResponse;
import com.careerpilot.resume.application.dto.ResumeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * Resume upload and management.
 *
 * <p>Thin, like {@code AuthController}: bind, read the principal, call one use
 * case, wrap the result. No entity enters this file, and no validation decision
 * is made here — file-type detection and size limits live in the service and the
 * domain, so a future scheduled import or admin tool gets the same rules for
 * free.
 *
 * <p>Every method takes {@code @AuthenticationPrincipal} and passes the user id
 * down. The service then scopes every query by it, which is what makes IDOR
 * structurally difficult rather than merely discouraged.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/resumes")
@Tag(name = "Resumes", description = "Upload, list, download, and delete resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * Uploads a resume.
     *
     * <p>The whole file is read into memory. That is a deliberate consequence of
     * needing the bytes twice — once to detect the type from its header, once to
     * compute the checksum — before deciding whether to store it at all.
     * Streaming would mean either two passes over a temp file or storing first
     * and validating after, and the second is worse: it puts unvalidated content
     * in the storage bucket. Bounded at 5 MB by
     * {@code spring.servlet.multipart.max-file-size}, so the memory cost is
     * predictable.
     *
     * @param file        the uploaded file
     * @param makePrimary whether to mark it primary
     * @param principal   the authenticated caller
     * @param uriBuilder  injected builder for the {@code Location} header
     * @return 201 with the stored resume
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a resume",
            description = """
                    Accepts PDF and .docx up to 5 MB. The file type is determined from the \
                    file's own bytes, not from its extension or Content-Type header. \
                    Re-uploading identical content returns 409.""")
    public ResponseEntity<ApiResponse<ResumeResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "makePrimary", defaultValue = "false") boolean makePrimary,
            @AuthenticationPrincipal AuthenticatedUser principal,
            UriComponentsBuilder uriBuilder) {

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("No file was provided");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            // The multipart stream failed mid-read: a truncated upload or a
            // dropped connection. Not a server defect, so not a 500.
            throw new BusinessRuleViolationException("The upload was interrupted. Please try again.");
        }

        ResumeResponse resume = resumeService.upload(
                principal.getId(), content, file.getOriginalFilename(), makePrimary);

        URI location = uriBuilder.path("/api/v1/resumes/{id}").buildAndExpand(resume.id()).toUri();

        return ResponseEntity.created(location)
                .body(ApiResponse.ok(resume, "Resume uploaded"));
    }

    /**
     * Lists the caller's resumes, newest first.
     *
     * @param pageable  page request; defaults to 20 per page
     * @param principal the authenticated caller
     * @return 200 with one page of resumes
     */
    @GetMapping
    @Operation(summary = "List your resumes")
    public ResponseEntity<ApiResponse<PageResponse<ResumeResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(resumeService.list(principal.getId(), pageable)));
    }

    /**
     * Fetches one resume.
     *
     * @param id        the resume
     * @param principal the authenticated caller
     * @return 200 with the resume
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one resume",
            description = "Returns 404 for a resume owned by another user, not 403.")
    public ResponseEntity<ApiResponse<ResumeResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(resumeService.get(id, principal.getId())));
    }

    /**
     * Returns a short-lived signed download URL.
     *
     * <p>A URL rather than the file. Streaming resume bytes through this
     * container would cost CPU time we are billed for and add a hop, with no
     * security gain over a URL that expires in minutes.
     *
     * @param id        the resume
     * @param principal the authenticated caller
     * @return 200 with a signed URL and its expiry
     */
    @GetMapping("/{id}/download")
    @Operation(summary = "Get a download link",
            description = """
                    Returns a signed URL that expires in minutes. Navigate to it immediately \
                    rather than storing it: it is a bearer credential for that one file.""")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(resumeService.getDownloadUrl(id, principal.getId())));
    }

    /**
     * Marks a resume as the caller's primary one.
     *
     * @param id        the resume
     * @param principal the authenticated caller
     * @return 200 with the updated resume
     */
    @PatchMapping("/{id}/primary")
    @Operation(summary = "Set your primary resume")
    public ResponseEntity<ApiResponse<ResumeResponse>> makePrimary(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                resumeService.makePrimary(id, principal.getId()), "Primary resume updated"));
    }

    /**
     * Deletes a resume.
     *
     * @param id        the resume
     * @param principal the authenticated caller
     * @return 204
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a resume",
            description = """
                    Soft delete. The file and row are permanently removed after 30 days, \
                    so an accidental deletion can be reversed within that window.""")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        resumeService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
