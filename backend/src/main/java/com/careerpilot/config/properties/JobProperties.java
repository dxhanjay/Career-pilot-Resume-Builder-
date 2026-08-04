package com.careerpilot.config.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed binding for {@code app.jobs.*}.
 *
 * @param batchSize        how many jobs one poll claims
 * @param staleLockTimeout how long a claim may be held before it is presumed dead
 * @param retentionDays    how long terminal jobs are kept
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.jobs")
public record JobProperties(

        /*
         * Small deliberately. Claimed jobs run sequentially on the poller
         * thread, so a large batch means the last job in it waits for all the
         * others - and with AI work at tens of seconds each, that turns a queue
         * into a stall. Concurrency comes from the executor and from replicas,
         * not from batch size.
         */
        @Min(1)
        int batchSize,

        /*
         * Must comfortably exceed the slowest plausible job, or the reaper will
         * requeue work that is still running and it will execute twice. Parsing
         * targets under 10s and AI analysis under 45s (NFR-PERF-02, -03), so
         * five minutes leaves generous headroom.
         */
        Duration staleLockTimeout,

        @Min(1)
        int retentionDays
) {
}
