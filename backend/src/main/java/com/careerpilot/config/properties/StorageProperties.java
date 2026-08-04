package com.careerpilot.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for {@code app.storage.*}.
 *
 * @param provider           {@code cloudinary} or {@code local}
 * @param localDirectory     where {@code LocalFileStorage} writes; ignored otherwise
 * @param signedUrlTtlSeconds lifetime of a generated download URL
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(

        @NotBlank
        String provider,

        String localDirectory,

        /*
         * Short by design. A signed URL is a bearer credential for one file:
         * anyone holding it can read the document, with no further check. URLs
         * leak through browser history, referrer headers, chat messages, and
         * screenshots, so the window in which a leaked one is useful should be
         * measured in minutes.
         *
         * Long enough for a browser to start the download and for a slow
         * connection to finish it; not long enough to be worth sharing.
         */
        long signedUrlTtlSeconds
) {
}
