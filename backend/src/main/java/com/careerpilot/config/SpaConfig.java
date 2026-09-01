package com.careerpilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React application from the same process as the API.
 *
 * <p>One deployable, one origin. That removes an entire class of production
 * problems at a stroke: no cross-origin preflight, no second service to keep
 * in step on redeploys, no environment variable holding an API base URL that
 * is wrong in exactly one environment, and no window during a deploy where the
 * frontend is newer than the API it is calling.
 *
 * <p>The frontend is built into {@code classpath:/static} by the Docker build.
 * When it has not been built — running the backend alone during development —
 * every one of these lookups misses and the API is unaffected.
 *
 * <h2>Why a fallback resolver rather than a controller</h2>
 *
 * <p>A single-page application owns its own routes. A browser asking for
 * {@code /resumes/9f3a} directly, or reloading on it, must receive
 * {@code index.html} and let the router take over — otherwise every deep link
 * and every refresh is a 404. This resolver returns the real file when one
 * exists and {@code index.html} when it does not.
 *
 * <p>Paths under {@code /api}, {@code /actuator}, {@code /swagger-ui} and
 * {@code /v3/api-docs} are excluded from the fallback. Without that exclusion a
 * misspelled API path would return an HTML page with status 200, and a client
 * would report "unexpected token &lt; in JSON" instead of "404 not found" —
 * which is a genuinely miserable thing to debug.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";
    private static final String INDEX = "static/index.html";

    /** Prefixes that must never fall back to the SPA shell. */
    private static final String[] API_PREFIXES = {
            "api/", "actuator/", "v3/api-docs", "swagger-ui"
    };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {

                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isApiPath(resourcePath)) {
                            return null;
                        }
                        // A request for a hashed asset that is genuinely absent
                        // should 404 rather than return HTML, or a stale cached
                        // page will try to execute index.html as JavaScript.
                        if (looksLikeAsset(resourcePath)) {
                            return null;
                        }
                        ClassPathResource index = new ClassPathResource(INDEX);
                        return index.exists() ? index : null;
                    }
                });
    }

    private static boolean isApiPath(String resourcePath) {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        for (String prefix : API_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the path looks like a file rather than a route.
     *
     * <p>A dot in the last segment is the signal. Application routes do not
     * normally contain one; {@code main-B7f2.js} and {@code favicon.ico} always
     * do.
     */
    private static boolean looksLikeAsset(String resourcePath) {
        int lastSlash = resourcePath.lastIndexOf('/');
        String lastSegment = lastSlash < 0 ? resourcePath : resourcePath.substring(lastSlash + 1);
        return lastSegment.contains(".");
    }
}
