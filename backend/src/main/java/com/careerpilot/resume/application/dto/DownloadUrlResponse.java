package com.careerpilot.resume.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A time-limited link to a stored resume.
 *
 * <p>{@code expiresAt} is returned so the client can behave correctly rather
 * than guess. A URL held in component state and reused twenty minutes later
 * fails with a provider error the frontend cannot interpret; knowing the expiry
 * lets it request a fresh one instead.
 *
 * <p>The URL is a bearer credential for one file — anyone holding it can read
 * the document, with no further check. That is why it expires in minutes and why
 * the frontend should navigate to it immediately rather than storing it.
 *
 * @param downloadUrl signed, expiring URL
 * @param expiresAt   when it stops working
 * @param filename    suggested filename for the download
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Schema(name = "DownloadUrlResponse", description = "A short-lived signed download link")
public record DownloadUrlResponse(
        String downloadUrl,
        Instant expiresAt,
        String filename
) {
}
