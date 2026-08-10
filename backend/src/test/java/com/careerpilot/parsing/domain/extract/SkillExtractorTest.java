package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionSegmenter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SkillExtractor} and {@link SkillLexicon}.
 *
 * <p>The false-positive tests carry the most weight. Under {@code FR-JD-03} a
 * wrongly-detected skill does not merely add noise — it hides a real gap, so a
 * candidate told they already have Rust never learns they need it. That is the
 * more damaging of the two error directions, and the ambiguous-entry rules
 * exist to bias against it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("SkillExtractor")
class SkillExtractorTest {

    private static List<DetectedSkill> extract(String text) {
        LineModel model = LineModel.of(text);
        List<ResumeSection> sections = SectionSegmenter.segment(model);
        return SkillExtractor.extract(model, sections);
    }

    private static List<String> namesFrom(String text) {
        return extract(text).stream().map(DetectedSkill::normalizedName).toList();
    }

    @Nested
    @DisplayName("detection")
    class Detection {

        @Test
        @DisplayName("finds skills listed under a skills heading")
        void findsListedSkills() {
            List<String> skills = namesFrom("""
                    TECHNICAL SKILLS
                    Java, Spring Boot, PostgreSQL, Docker, Git""");

            assertThat(skills).contains("java", "spring boot", "postgresql", "docker", "git");
        }

        @Test
        @DisplayName("finds skills mentioned in experience bullets")
        void findsSkillsInProse() {
            List<String> skills = namesFrom("""
                    WORK EXPERIENCE
                    Backend Intern
                    - Built a payment service in Java using Spring Boot and Redis""");

            assertThat(skills).contains("java", "spring boot", "redis");
        }

        @Test
        @DisplayName("resolves aliases to a canonical name")
        void resolvesAliases() {
            assertThat(namesFrom("TECHNICAL SKILLS\nReactJS, JS, Postgres, K8s"))
                    .contains("react", "javascript", "postgresql", "kubernetes");
        }

        @Test
        @DisplayName("keeps the candidate's own spelling alongside the canonical name")
        void keepsVerbatimSpelling() {
            DetectedSkill react = extract("TECHNICAL SKILLS\nReactJS and Postgres").stream()
                    .filter(s -> s.normalizedName().equals("react"))
                    .findFirst()
                    .orElseThrow();

            assertThat(react.name()).isEqualTo("ReactJS");
            assertThat(react.normalizedName()).isEqualTo("react");
        }

        @Test
        @DisplayName("handles punctuation in skill names")
        void handlesPunctuatedNames() {
            assertThat(namesFrom("TECHNICAL SKILLS\nC++, C#, Node.js, .NET, CI/CD"))
                    .contains("c++", "c#", "node.js", ".net", "ci/cd");
        }

        @Test
        @DisplayName("assigns a category")
        void assignsCategory() {
            List<DetectedSkill> skills = extract("TECHNICAL SKILLS\nJava, PostgreSQL, Docker");

            assertThat(skills).anySatisfy(s -> {
                assertThat(s.normalizedName()).isEqualTo("java");
                assertThat(s.category()).isEqualTo(SkillCategory.LANGUAGE);
            });
            assertThat(skills).anySatisfy(s -> {
                assertThat(s.normalizedName()).isEqualTo("postgresql");
                assertThat(s.category()).isEqualTo(SkillCategory.DATABASE);
            });
            assertThat(skills).anySatisfy(s -> {
                assertThat(s.normalizedName()).isEqualTo("docker");
                assertThat(s.category()).isEqualTo(SkillCategory.CLOUD_DEVOPS);
            });
        }

        @Test
        @DisplayName("records the line a skill was found on")
        void recordsSourceLine() {
            DetectedSkill docker = extract("""
                    TECHNICAL SKILLS
                    Java
                    Docker""").stream()
                    .filter(s -> s.normalizedName().equals("docker"))
                    .findFirst()
                    .orElseThrow();

            assertThat(docker.lineStart()).isEqualTo(2);
            assertThat(docker.lineEnd()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("word boundaries")
    class WordBoundaries {

        @Test
        @DisplayName("\"java\" does not match inside \"javascript\"")
        void javaDoesNotMatchJavascript() {
            List<String> skills = namesFrom("TECHNICAL SKILLS\nJavaScript");

            assertThat(skills).contains("javascript").doesNotContain("java");
        }

        @Test
        @DisplayName("\"go\" does not match inside \"golang\" twice or inside ordinary words")
        void goDoesNotMatchSubstrings() {
            assertThat(namesFrom("TECHNICAL SKILLS\nGolang")).contains("go");
            assertThat(namesFrom("TECHNICAL SKILLS\nOngoing coursework")).doesNotContain("go");
        }

        @Test
        @DisplayName("\"c\" does not match inside ordinary words")
        void cDoesNotMatchSubstrings() {
            assertThat(namesFrom("TECHNICAL SKILLS\nComputer science coursework"))
                    .doesNotContain("c");
            assertThat(namesFrom("TECHNICAL SKILLS\nC, Python")).contains("c", "python");
        }
    }

    @Nested
    @DisplayName("ambiguous names")
    class AmbiguousNames {

        @Test
        @DisplayName("are detected inside a skills section")
        void detectedInSkillsSection() {
            assertThat(namesFrom("TECHNICAL SKILLS\nC, R, Go, Rust, Swift"))
                    .contains("c", "r", "go", "rust", "swift");
        }

        @Test
        @DisplayName("are ignored outside a skills section")
        void ignoredElsewhere() {
            List<String> skills = namesFrom("""
                    WORK EXPERIENCE
                    Engineer
                    - Made the deployment process rust-free and helped the team go faster
                    - I excel at shipping on time""");

            assertThat(skills).doesNotContain("rust", "go", "excel");
        }

        @Test
        @DisplayName("are ignored in a document with no recognised structure")
        void ignoredInUnstructuredDocuments() {
            // No headings at all, so nothing disambiguates a bare "go".
            List<String> skills = namesFrom("""
                    Aditi Sharma
                    Where I have been
                    I helped the team go faster and excel at delivery""");

            assertThat(skills).doesNotContain("go", "excel");
        }

        @Test
        @DisplayName("score lower than unambiguous ones even when correct")
        void scoreLower() {
            List<DetectedSkill> skills = extract("TECHNICAL SKILLS\nJava, Go");

            int java = confidenceOf(skills, "java");
            int go = confidenceOf(skills, "go");

            assertThat(java).isGreaterThan(go);
        }
    }

    @Nested
    @DisplayName("false positives that must not occur")
    class FalsePositives {

        @Test
        @DisplayName("\"the rest of the team\" is not a REST API")
        void restIsNotRestApi() {
            assertThat(namesFrom("""
                    WORK EXPERIENCE
                    - Coordinated the rest of the team during release week"""))
                    .doesNotContain("rest api");
        }

        @Test
        @DisplayName("a graph \"node\" is not Node.js")
        void nodeIsNotNodeJs() {
            assertThat(namesFrom("""
                    PROJECTS
                    - Implemented a tree where each node stores a checksum"""))
                    .doesNotContain("node.js");
        }

        @Test
        @DisplayName("\"Node.js\" itself is still detected")
        void nodeJsStillDetected() {
            assertThat(namesFrom("TECHNICAL SKILLS\nNode.js, Express"))
                    .contains("node.js", "express");
        }
    }

    @Nested
    @DisplayName("confidence")
    class Confidence {

        @Test
        @DisplayName("a listed skill outranks one mentioned in passing")
        void listedOutranksMentioned() {
            List<DetectedSkill> listed = extract("TECHNICAL SKILLS\nDocker");
            List<DetectedSkill> mentioned = extract("""
                    WORK EXPERIENCE
                    - Deployed with Docker""");

            assertThat(confidenceOf(listed, "docker"))
                    .isGreaterThan(confidenceOf(mentioned, "docker"));
        }

        @Test
        @DisplayName("the strongest occurrence wins when a skill appears twice")
        void strongestOccurrenceWins() {
            List<DetectedSkill> skills = extract("""
                    WORK EXPERIENCE
                    - Deployed with Docker

                    TECHNICAL SKILLS
                    Docker""");

            assertThat(skills).filteredOn(s -> s.normalizedName().equals("docker")).hasSize(1);
            // The skills-section mention is stronger, so its line is the one kept.
            assertThat(confidenceOf(skills, "docker")).isEqualTo(95);
        }
    }

    @Nested
    @DisplayName("degenerate input")
    class DegenerateInput {

        @Test
        @DisplayName("an empty document yields nothing")
        void handlesEmpty() {
            assertThat(SkillExtractor.extract(LineModel.of(""), List.of())).isEmpty();
            assertThat(SkillExtractor.extract(null, null)).isEmpty();
        }

        @Test
        @DisplayName("a resume with no recognised skills yields nothing rather than guessing")
        void handlesNoSkills() {
            assertThat(namesFrom("""
                    EDUCATION
                    B.A. History
                    University of Somewhere""")).isEmpty();
        }
    }

    private static int confidenceOf(List<DetectedSkill> skills, String canonical) {
        return skills.stream()
                .filter(s -> s.normalizedName().equals(canonical))
                .findFirst()
                .orElseThrow(() -> new AssertionError("skill not found: " + canonical))
                .confidence();
    }
}
