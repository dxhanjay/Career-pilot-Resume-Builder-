package com.careerpilot.config;

import com.careerpilot.config.properties.AsyncProperties;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configures the executor behind the async job engine described in
 * Architecture §6, and enables scheduled tasks.
 *
 * <p>{@code @EnableScheduling} is required by the {@code JobPoller} and the
 * stale-lock reaper introduced in Phase 5. {@code @EnableAsync} makes
 * {@code @Async} methods actually run asynchronously — without it they execute
 * on the calling thread, which produces an application that behaves correctly
 * in tests and blocks HTTP requests for forty seconds in production.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /** Bean name referenced by {@code @Async("jobExecutor")}. */
    public static final String JOB_EXECUTOR = "jobExecutor";

    private final AsyncProperties asyncProperties;

    public AsyncConfig(AsyncProperties asyncProperties) {
        this.asyncProperties = asyncProperties;
    }

    /**
     * The executor that runs background jobs — parsing, ATS analysis, JD
     * matching, interview evaluation.
     *
     * <p>A dedicated, explicitly named executor rather than Spring's default.
     * The default {@code SimpleAsyncTaskExecutor} creates an unbounded number of
     * threads: one per task, forever, with no queue and no ceiling. Under any
     * burst that is a memory exhaustion path, and it makes concurrency
     * unmeasurable because nothing is bounded.
     *
     * <p>{@code CallerRunsPolicy} is the rejection strategy. When the pool and
     * the queue are both full, the submitting thread runs the task itself. This
     * applies natural backpressure — the caller slows down instead of the task
     * being silently discarded. Losing a user's analysis request because a queue
     * was full, with no error anywhere, is a far worse outcome than a slow
     * request.
     *
     * <p>{@code setWaitForTasksToCompleteOnShutdown} pairs with the graceful
     * shutdown configured in {@code application.yml}: on a Railway redeploy,
     * in-flight jobs are given time to finish rather than being killed
     * mid-write. Jobs interrupted anyway are recovered by the stale-lock reaper,
     * so this is an optimisation rather than the correctness mechanism — but it
     * meaningfully reduces how often that recovery is needed.
     *
     * @return the configured job executor
     */
    @Bean(name = JOB_EXECUTOR)
    public Executor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncProperties.corePoolSize());
        executor.setMaxPoolSize(asyncProperties.maxPoolSize());
        executor.setQueueCapacity(asyncProperties.queueCapacity());
        executor.setThreadNamePrefix("job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(mdcPropagatingDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Copies the submitting thread's logging context onto the worker thread.
     *
     * <p>Without this, NFR-OBS-01 stops at the thread boundary. {@link MDC} is
     * thread-local, so a job dispatched from an HTTP request starts on a worker
     * thread with an empty context — and every log line it writes, including the
     * stack trace when it fails, has no correlation ID. Precisely the work most
     * likely to fail (AI calls, parsing, external I/O) becomes the work hardest
     * to trace back to the user who triggered it.
     *
     * <p>The {@code finally} block clears the context afterwards for the same
     * reason {@code CorrelationIdFilter} does: pool threads are reused, and a
     * stale context would attribute one job's log lines to a previous request.
     *
     * @return a decorator that propagates and then clears the MDC
     */
    private TaskDecorator mdcPropagatingDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
