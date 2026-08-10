package com.careerpilot.parsing.domain.section;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HeadingLexicon}.
 *
 * <p>The false-negative cases matter as much as the positive ones. A keyword
 * that fires too eagerly turns an employer name into a section heading, which
 * splits a resume in the wrong place and puts every entity beneath it into the
 * wrong section — a failure that is invisible in the score and very visible on
 * the "what the machine saw" screen.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("HeadingLexicon")
class HeadingLexiconTest {

    @Nested
    @DisplayName("exact matches")
    class ExactMatches {

        @ParameterizedTest(name = "\"{0}\" is {1}")
        @CsvSource({
                "EDUCATION,               EDUCATION",
                "Work Experience,         EXPERIENCE",
                "Employment History,      EXPERIENCE",
                "PROFESSIONAL SUMMARY,    SUMMARY",
                "Career Objective,        SUMMARY",
                "Technical Skills,        SKILLS",
                "Core Competencies,       SKILLS",
                "Academic Projects,       PROJECTS",
                "Certifications,          CERTIFICATIONS",
                "Awards and Achievements, ACHIEVEMENTS",
                "Languages Known,         LANGUAGES",
                "Positions of Responsibility, INTERESTS",
                "References,              REFERENCES"
        })
        @DisplayName("resolve known headings")
        void resolvesKnownHeadings(String heading, SectionType expected) {
            assertThat(HeadingLexicon.resolve(heading))
                    .hasValueSatisfying(match -> {
                        assertThat(match.type()).isEqualTo(expected);
                        assertThat(match.exact()).isTrue();
                    });
        }

        @Test
        @DisplayName("are case-insensitive")
        void areCaseInsensitive() {
            assertThat(HeadingLexicon.resolve("education")).isPresent();
            assertThat(HeadingLexicon.resolve("EdUcAtIoN")).isPresent();
        }
    }

    @Nested
    @DisplayName("decoration stripping")
    class DecorationStripping {

        @ParameterizedTest
        @ValueSource(strings = {
                "EDUCATION:",
                "-- EDUCATION --",
                "| EDUCATION |",
                "*** Education ***",
                "EDUCATION 2022",
                "  Education  "
        })
        @DisplayName("decorated headings still resolve")
        void stripsDecoration(String heading) {
            assertThat(HeadingLexicon.resolve(heading))
                    .hasValueSatisfying(match ->
                            assertThat(match.type()).isEqualTo(SectionType.EDUCATION));
        }

        @Test
        @DisplayName("canonicalisation collapses whitespace and lowercases")
        void canonicalises() {
            assertThat(HeadingLexicon.canonicalise("--  WORK   EXPERIENCE : "))
                    .isEqualTo("work experience");
        }

        @Test
        @DisplayName("text with no letters canonicalises to empty and matches nothing")
        void handlesDecorationOnly() {
            assertThat(HeadingLexicon.canonicalise("--------")).isEmpty();
            assertThat(HeadingLexicon.resolve("--------")).isEmpty();
            assertThat(HeadingLexicon.resolve(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("keyword matches")
    class KeywordMatches {

        @ParameterizedTest(name = "\"{0}\" is {1}")
        @CsvSource({
                "TECHNICAL SKILLS & TOOLS,     SKILLS",
                "Key Skills and Strengths,     SKILLS",
                "Professional Experience Summary, EXPERIENCE",
                "My Certifications List,       CERTIFICATIONS"
        })
        @DisplayName("resolve decorated variants, but as inexact")
        void resolvesVariants(String heading, SectionType expected) {
            assertThat(HeadingLexicon.resolve(heading))
                    .hasValueSatisfying(match -> {
                        assertThat(match.type()).isEqualTo(expected);
                        assertThat(match.exact()).isFalse();
                    });
        }

        @Test
        @DisplayName("the more specific keyword wins when two are present")
        void prefersMoreSpecificKeyword() {
            // Contains both "project" and "experience"; ordering must pick PROJECTS.
            assertThat(HeadingLexicon.resolve("Selected Project Experience"))
                    .hasValueSatisfying(match ->
                            assertThat(match.type()).isEqualTo(SectionType.PROJECTS));
        }

        @Test
        @DisplayName("match whole words only")
        void matchesWholeWordsOnly() {
            // "referenced" contains "reference"; substring matching would make
            // this a REFERENCES heading and split the resume here.
            assertThat(HeadingLexicon.resolve("Details referenced elsewhere")).isEmpty();
        }
    }

    @Nested
    @DisplayName("lines that must not resolve")
    class NonHeadings {

        @ParameterizedTest
        @ValueSource(strings = {
                "Microsoft Research",
                "GitHub Profile",
                "LinkedIn Profile",
                "Aditi Sharma",
                "Backend Engineering Intern",
                "National Institute of Technology",
                "B.Tech, Computer Science",
                "AWS Certified Cloud Practitioner",
                "Java, Spring Boot, PostgreSQL"
        })
        @DisplayName("employer names, job titles and content lines resolve to nothing")
        void doesNotResolveContent(String line) {
            assertThat(HeadingLexicon.resolve(line)).isEmpty();
        }

        @Test
        @DisplayName("\"Research\" alone is still a heading, unlike \"Microsoft Research\"")
        void researchAloneStillResolves() {
            assertThat(HeadingLexicon.resolve("Research"))
                    .hasValueSatisfying(match -> {
                        assertThat(match.type()).isEqualTo(SectionType.PUBLICATIONS);
                        assertThat(match.exact()).isTrue();
                    });
            assertThat(HeadingLexicon.resolve("Microsoft Research")).isEmpty();
        }

        @Test
        @DisplayName("\"Profile\" alone is still a heading, unlike \"GitHub Profile\"")
        void profileAloneStillResolves() {
            assertThat(HeadingLexicon.resolve("Profile"))
                    .hasValueSatisfying(match ->
                            assertThat(match.type()).isEqualTo(SectionType.SUMMARY));
            assertThat(HeadingLexicon.resolve("GitHub Profile")).isEmpty();
        }
    }
}
