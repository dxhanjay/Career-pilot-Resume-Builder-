package com.careerpilot.config;

import com.careerpilot.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client and the API share one origin, so the rules deciding which of them
 * answers a given path are load-bearing.
 *
 * <p>The failure this guards against is subtle and miserable: if the SPA
 * fallback catches API paths, a misspelled endpoint returns {@code index.html}
 * with status 200, and the client reports "unexpected token &lt; in JSON at
 * position 0" instead of "404 not found". Every developer who meets that error
 * loses an hour to it.
 *
 * <p>Runs against a stub {@code static/index.html} in test resources; the real
 * one is compiled in by the Docker build.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@AutoConfigureMockMvc
@DisplayName("SPA routing")
class SpaRoutingIT extends AbstractIntegrationTest {

    private final MockMvc mockMvc;

    SpaRoutingIT(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Nested
    @DisplayName("serves the client")
    class ServesTheClient {

        @Test
        @DisplayName("the root returns the shell")
        void rootReturnsShell() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-spa-shell")));
        }

        @Test
        @DisplayName("a client-side route returns the shell rather than 404")
        void clientRouteReturnsShell() throws Exception {
            // A browser reloading on /dashboard asks the server for /dashboard.
            // Without the fallback, every deep link and every refresh is a 404.
            for (String route : new String[]{"/dashboard", "/login", "/settings"}) {
                mockMvc.perform(get(route))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("data-spa-shell")));
            }
        }

        @Test
        @DisplayName("a nested client route returns the shell")
        void nestedRouteReturnsShell() throws Exception {
            mockMvc.perform(get("/resumes/9f3a7c1e-0000-0000-0000-000000000000"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-spa-shell")));
        }

        @Test
        @DisplayName("a real asset is served as itself")
        void realAssetIsServed() throws Exception {
            mockMvc.perform(get("/assets/stub.js"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("stub asset")));
        }

        @Test
        @DisplayName("the client shell is reachable without signing in")
        void shellIsPublic() throws Exception {
            // A login page behind authentication is not a login page. This
            // exposes no data: every API route below is still authenticated.
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("never swallows the API")
    class NeverSwallowsTheApi {

        @Test
        @DisplayName("⭐ an unknown API path returns 404, not the HTML shell")
        void unknownApiPathIsNotTheShell() throws Exception {
            mockMvc.perform(get("/api/v1/does-not-exist"))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string(not(containsString("data-spa-shell"))));
        }

        @Test
        @DisplayName("a protected API path still returns 401 rather than the shell")
        void protectedApiPathStaysProtected() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString("data-spa-shell"))));
        }

        @Test
        @DisplayName("actuator is not swallowed either")
        void actuatorIsNotTheShell() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("data-spa-shell"))));
        }

        @Test
        @DisplayName("a missing asset 404s rather than returning HTML")
        void missingAssetIsNotTheShell() throws Exception {
            // A stale cached page requesting a hashed bundle that no longer
            // exists must get a 404. Returning index.html would have the browser
            // try to execute HTML as JavaScript.
            mockMvc.perform(get("/assets/index-DEADBEEF.js"))
                    .andExpect(status().isNotFound());
        }
    }
}
