package com.careerpilot.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for {@code app.resume.*}.
 *
 * @param maxFileSizeBytes   largest accepted upload (FR-RES-01)
 * @param maxResumesPerUser  how many live resumes one account may hold
 * @param purgeAfterDays     how long a soft-deleted resume survives
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.resume")
public record ResumeProperties(

        /*
         * Also enforced by spring.servlet.multipart.max-file-size, which the
         * container applies before any application code runs, and by a CHECK
         * constraint on the column. Three layers is not redundancy for its own
         * sake: the container limit cannot produce a useful error message, the
         * application limit can be bypassed by a future code path that does not
         * go through this service, and the column constraint catches both.
         */
        @Min(1024)
        long maxFileSizeBytes,

        /*
         * A cost control as much as a product decision. Every resume is a
         * candidate for parsing and AI analysis, both of which cost money, and
         * an account with no ceiling is an unbounded bill.
         */
        @Min(1)
        int maxResumesPerUser,

        /*
         * NFR-PRIV-01 requires deletion within 30 days. The window exists so
         * that an accidental delete can be reversed by support; making it much
         * shorter removes that safety net, and making it longer breaches the
         * commitment.
         */
        @Min(1)
        int purgeAfterDays
) {
}
