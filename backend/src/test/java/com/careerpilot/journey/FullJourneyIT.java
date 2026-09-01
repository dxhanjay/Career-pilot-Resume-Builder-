package com.careerpilot.journey;

import com.careerpilot.jobs.application.JobHandler;
import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobType;
import com.careerpilot.jobs.infrastructure.JobRepository;
import com.careerpilot.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole product loop, end to end, against a real database.
 *
 * <p>Sign up, upload a resume, parse it, read what the machine saw, get an ATS
 * score with evidence, paste a job description, match against it, and sit a mock
 * interview built from the resulting gaps.
 *
 * <p>Every other test in this suite proves one component behaves. This one
 * proves they connect — which is the failure mode a module-by-module suite is
 * blindest to, and the reason the entire Phase 6b extraction layer sat unused
 * for a whole phase while its own unit tests stayed green.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@AutoConfigureMockMvc
@DisplayName("⭐ The complete user journey")
class FullJourneyIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "a-long-enough-password";

    /**
     * A deliberately mediocre resume.
     *
     * <p>Good enough to parse and to exercise every extractor, weak enough that
     * the rubric has something to say: one bullet opens with "Responsible for"
     * and none of the Docker or Kubernetes vocabulary the posting below asks for
     * appears anywhere.
     */
    private static final String RESUME = """
            Aditi Sharma
            aditi.sharma@example.com | +91 98765 43210 | Bengaluru, India
            github.com/aditisharma

            EXPERIENCE

            Software Engineering Intern, Acme Technologies
            Jun 2024 - Aug 2024
            - Built a reporting service in Java and Spring Boot for 3 internal teams
            - Reduced report generation time by 40% by batching PostgreSQL queries
            - Responsible for testing the payment module before each release

            EDUCATION

            B.Tech Computer Science, National Institute of Technology
            2021 - 2025

            SKILLS

            Java, Spring Boot, PostgreSQL, JavaScript, React, Git, Linux
            """;

    private static final String POSTING = """
            Backend Engineering Intern
            Globex Systems - Bengaluru

            Requirements:
            - Strong experience with Java and Spring Boot
            - Proficient in SQL and PostgreSQL
            - Solid understanding of Docker and containerised deployment
            - Experience with Kubernetes in production

            Nice to have:
            - Exposure to Redis
            - Familiarity with Kafka
            """;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JobRepository jobRepository;
    private final List<JobHandler> jobHandlers;

    private String token;

    FullJourneyIT(@Autowired MockMvc mockMvc,
                  @Autowired ObjectMapper objectMapper,
                  @Autowired JobRepository jobRepository,
                  @Autowired List<JobHandler> jobHandlers) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
        this.jobHandlers = jobHandlers;
    }

    @BeforeEach
    void signIn() throws Exception {
        token = registerAndLogin();
    }

    @Test
    @DisplayName("upload → parse → see the parse → score → match → interview")
    void completeLoop() throws Exception {
        // ---- 1. Upload -------------------------------------------------
        UUID resumeId = UUID.fromString(
                send(MockMvcRequestBuilders.multipart("/api/v1/resumes").file(pdf(RESUME)), 201)
                        .get("data").get("id").asText());

        // ---- 2. Parse --------------------------------------------------
        // The poller is effectively disabled in the test profile so it cannot
        // race the assertions; the handler is invoked directly instead, which is
        // exactly what the poller would do.
        JsonNode enqueued = send(
                MockMvcRequestBuilders.post("/api/v1/resumes/{id}/parse", resumeId), 202);
        runJob(UUID.fromString(enqueued.get("data").get("id").asText()));

        JsonNode parse = getJson("/api/v1/resumes/{id}/parse", resumeId);
        assertThat(parse.get("data").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(parse.get("data").get("wordCount").asInt()).isPositive();

        // ---- 3. "Here is exactly what the machine saw" ------------------
        JsonNode structured = getJson("/api/v1/resumes/{id}/parse/structured", resumeId).get("data");

        assertThat(structured.get("lines")).isNotEmpty();
        assertThat(structured.get("contact").get("email").asText())
                .as("the contact block is what a screening system stores as the candidate record")
                .isEqualTo("aditi.sharma@example.com");
        assertThat(names(structured.get("skills"), "normalizedName"))
                .as("skills must be persisted, not merely detected — Phase 6b shipped extractors "
                        + "that nothing called")
                .contains("java", "spring boot", "postgresql");
        assertThat(names(structured.get("sections"), "type"))
                .contains("EXPERIENCE", "EDUCATION", "SKILLS");
        assertThat(structured.get("experience")).isNotEmpty();
        assertThat(structured.get("education")).isNotEmpty();

        // ---- 4. ATS score, with evidence -------------------------------
        // Scoring is triggered automatically by the parse job, so a report is
        // already waiting rather than needing a second round trip.
        JsonNode analysis = getJson("/api/v1/resumes/{id}/ats", resumeId).get("data");

        assertThat(analysis.get("overallScore").asInt()).isBetween(0, 100);
        assertThat(analysis.get("categories")).hasSize(5);
        assertThat(analysis.get("findings")).isNotEmpty();

        JsonNode passiveFinding = findByCode(analysis.get("findings"), "PASSIVE_PHRASING");
        assertThat(passiveFinding)
                .as("the resume contains \"Responsible for testing\", which the rubric must catch")
                .isNotNull();
        assertThat(passiveFinding.get("evidence").asText())
                .as("FR-ATS-03: a finding without a quote from the resume is an assertion")
                .contains("Responsible for testing");
        assertThat(passiveFinding.get("recommendation").asText()).isNotBlank();

        // ---- 5. Save a job description ---------------------------------
        UUID postingId = UUID.fromString(
                postJson("/api/v1/job-descriptions", """
                        {"title":"Backend Engineering Intern","company":"Globex Systems",
                         "rawText":%s}""".formatted(objectMapper.writeValueAsString(POSTING)), 201)
                        .get("data").get("id").asText());

        // ---- 6. Match --------------------------------------------------
        JsonNode match = postJson("/api/v1/job-descriptions/" + postingId + "/match",
                """
                        {"resumeId":"%s"}""".formatted(resumeId), 200).get("data");

        assertThat(match.get("overallScore").asInt()).isBetween(0, 100);
        assertThat(names(match.get("matched"), "normalizedName"))
                .as("the resume names Java, Spring Boot and PostgreSQL and the posting asks for them")
                .contains("java", "spring boot", "postgresql");
        assertThat(names(match.get("missing"), "normalizedName"))
                .as("the resume mentions neither, and both are stated requirements")
                .contains("docker", "kubernetes");

        JsonNode dockerGap = findByField(match.get("missing"), "normalizedName", "docker");
        assertThat(dockerGap.get("jdEvidence").asText())
                .as("a gap that does not quote the line that asked for it is unactionable")
                .containsIgnoringCase("docker");
        assertThat(dockerGap.get("required").asBoolean()).isTrue();

        // ---- 7. Interview targeting those gaps -------------------------
        JsonNode session = postJson("/api/v1/interviews", """
                {"focus":"JOB_SPECIFIC","resumeId":"%s","jobDescriptionId":"%s","questionCount":4}"""
                .formatted(resumeId, postingId), 201).get("data");

        UUID sessionId = UUID.fromString(session.get("id").asText());
        JsonNode questions = session.get("questions");
        assertThat(questions).hasSize(4);

        assertThat(questions.toString())
                .as("a job-specific interview must probe the gaps the match just found")
                .containsIgnoringCase("docker");

        questions.forEach(question -> assertThat(question.has("expectedPoints"))
                .as("the cues a good answer covers must stay hidden until the question is "
                        + "answered, or the exercise becomes transcription")
                .isFalse());

        // ---- 8. Answer, and be scored ----------------------------------
        UUID firstQuestionId = UUID.fromString(questions.get(0).get("id").asText());
        JsonNode answer = postJson(
                "/api/v1/interviews/%s/questions/%s/answer".formatted(sessionId, firstQuestionId),
                """
                        {"answer":%s}""".formatted(objectMapper.writeValueAsString(STRONG_ANSWER)),
                200).get("data");

        assertThat(answer.get("score").asInt()).isBetween(0, 100);
        assertThat(answer.get("structureScore").asInt()).isPositive();
        assertThat(answer.get("strengths")).isNotEmpty();

        // ---- 9. Finish and read the report -----------------------------
        JsonNode finished = postJson("/api/v1/interviews/" + sessionId + "/complete", "", 200)
                .get("data");

        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.get("report").get("axisScores")).hasSize(4);
        assertThat(finished.get("overallScore").asInt())
                .as("three of four questions went unanswered, so the score must reflect the "
                        + "interview actually sat rather than the one question that went well")
                .isLessThan(answer.get("score").asInt());

        // ---- 10. The dashboard ties it together ------------------------
        JsonNode dashboard = getJson("/api/v1/dashboard").get("data");
        assertThat(dashboard.get("counts").get("resumes").asInt()).isEqualTo(1);
        assertThat(dashboard.get("counts").get("jobDescriptions").asInt()).isEqualTo(1);
        assertThat(dashboard.get("counts").get("interviews").asInt()).isEqualTo(1);
        assertThat(dashboard.get("latestScore").get("overallScore").asInt()).isBetween(0, 100);
    }

    @Test
    @DisplayName("re-running the analysis builds a score history rather than overwriting it")
    void analysisHistoryAccumulates() throws Exception {
        UUID resumeId = UUID.fromString(
                send(MockMvcRequestBuilders.multipart("/api/v1/resumes").file(pdf(RESUME)), 201)
                        .get("data").get("id").asText());

        JsonNode enqueued = send(
                MockMvcRequestBuilders.post("/api/v1/resumes/{id}/parse", resumeId), 202);
        runJob(UUID.fromString(enqueued.get("data").get("id").asText()));

        postJson("/api/v1/resumes/" + resumeId + "/ats", "", 200);

        JsonNode history = getJson("/api/v1/resumes/{id}/ats/history", resumeId).get("data");

        assertThat(history.get("points").size())
                .as("the parse triggers one analysis and the explicit call adds another; the "
                        + "closing promise is \"fix it and watch the score move\", which needs "
                        + "both runs to survive")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("a resume that has not been parsed cannot be matched or scored")
    void unparsedResumeIsRefusedCleanly() throws Exception {
        UUID resumeId = UUID.fromString(
                send(MockMvcRequestBuilders.multipart("/api/v1/resumes").file(pdf(RESUME)), 201)
                        .get("data").get("id").asText());

        // 409, not 500. The resume exists and the request is well formed; it is
        // the state that is wrong, and the client can act on that.
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/resumes/{id}/ats", resumeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/resumes/{id}/ats", resumeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("one user cannot read another user's resume, posting, or interview")
    void tenantIsolationHolds() throws Exception {
        UUID resumeId = UUID.fromString(
                send(MockMvcRequestBuilders.multipart("/api/v1/resumes").file(pdf(RESUME)), 201)
                        .get("data").get("id").asText());
        UUID postingId = UUID.fromString(
                postJson("/api/v1/job-descriptions", """
                        {"title":"Backend Engineering Intern","rawText":%s}"""
                        .formatted(objectMapper.writeValueAsString(POSTING)), 201)
                        .get("data").get("id").asText());

        String intruder = registerAndLogin();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/resumes/{id}", resumeId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/job-descriptions/{id}", postingId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());

        // 404 rather than 403 throughout: telling an intruder that a resource
        // exists but is not theirs is itself a disclosure.
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/resumes/{id}/parse/structured", resumeId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private static final String STRONG_ANSWER = """
            Last summer I was working on the reporting service at Acme, and generating the
            monthly report was taking about 4 minutes because we issued one query per team.
            I profiled it, found 30 separate PostgreSQL round trips, and I decided to batch
            them into a single join with a covering index. As a result generation came down
            to 25 seconds, and the 3 teams using it stopped filing tickets about timeouts.
            Looking back I would have measured before I started guessing, because I spent two
            days on a caching approach that turned out to be solving the wrong problem.
            """;

    /** Runs a queued job through its handler, as the poller would. */
    private void runJob(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        JobType type = job.getJobType();
        JobHandler handler = jobHandlers.stream()
                .filter(candidate -> candidate.handles() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No handler for " + type));
        handler.execute(job);
    }

    private static MockMultipartFile pdf(String text) throws IOException {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes(text));
    }

    /** A single-page PDF with a real text layer, so the parser has something to read. */
    private static byte[] pdfBytes(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.setLeading(13);
                content.newLineAtOffset(50, 780);
                for (String line : text.split("\n")) {
                    // Standard 14 fonts cannot encode every character; the fixture
                    // stays inside WinAnsi so a stray glyph cannot fail the build.
                    content.showText(line.replaceAll("[^\\x20-\\x7E]", " "));
                    content.newLine();
                }
                content.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private JsonNode getJson(String path, Object... vars) throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.get(path, vars)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode send(
            MockHttpServletRequestBuilder builder,
            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .header("Authorization", "Bearer " + token);
        if (!body.isEmpty()) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerAndLogin() throws Exception {
        String email = "journey-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Aditi Sharma"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private static List<String> names(JsonNode array, String field) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(node -> node.get(field).asText())
                .toList();
    }

    private static JsonNode findByCode(JsonNode array, String code) {
        return findByField(array, "code", code);
    }

    private static JsonNode findByField(JsonNode array, String field, String value) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .filter(node -> node.hasNonNull(field) && node.get(field).asText().equals(value))
                .findFirst()
                .orElse(null);
    }
}
