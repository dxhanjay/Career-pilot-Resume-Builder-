package com.careerpilot.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for {@code app.async.*} — the job executor's thread pool.
 *
 * <p>Defaults are deliberately small. The work this pool runs is I/O-bound
 * (waiting on the AI provider, on Cloudinary, on the database) rather than
 * CPU-bound, so a large pool would not make anything faster — it would simply
 * allow more concurrent AI calls, which is the fastest way to exhaust the
 * per-user credit cap and the provider rate limit simultaneously.
 *
 * <p>These are placeholders sized for a container with no traffic. Phase 7
 * introduces the first real AI workload and is where they get tuned against
 * measured latency rather than guessed.
 *
 * @param corePoolSize  threads kept alive even when idle
 * @param maxPoolSize   ceiling on concurrent job threads
 * @param queueCapacity jobs buffered before the pool grows past core size
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.async")
public record AsyncProperties(

        @Min(1) int corePoolSize,
        @Min(1) int maxPoolSize,
        @Min(0) int queueCapacity
) {
}
