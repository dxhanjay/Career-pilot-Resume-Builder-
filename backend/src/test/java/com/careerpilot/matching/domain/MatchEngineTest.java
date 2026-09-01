package com.careerpilot.matching.domain;

import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("Job matching")
class MatchEngineTest {

    private static final String POSTING = """
            Software Engineering Intern
            Acme Technologies — Bengaluru

            Requirements:
            - Strong experience with Java and Spring Boot
            - Proficient in SQL and PostgreSQL
            - Familiar with Git

            Nice to have:
            - Exposure to Docker
            - Knowledge of Kubernetes
            """;

    @Nested
    @DisplayName("Reading a posting")
    class Reading {

        @Test
        @DisplayName("splits must-haves from nice-to-haves by heading")
        void splits_required_from_optional() {
            JobPosting posting = JobPosting.parse(POSTING);

            assertThat(names(posting.requiredSkills())).contains("java", "postgresql");
            assertThat(names(posting.optionalSkills()))
                    .as("Everything under \"Nice to have\" is optional")
                    .contains("kubernetes");
        }

        @Test
        @DisplayName("an in-line softener demotes a skill even under a hard heading")
        void inline_softener_demotes() {
            JobPosting posting = JobPosting.parse("""
                    Requirements:
                    - Strong Java experience
                    - Familiarity with Kubernetes is a plus
                    """);

            assertThat(names(posting.requiredSkills())).contains("java");
            assertThat(names(posting.optionalSkills())).contains("kubernetes");
        }

        @Test
        @DisplayName("takes the title from the first substantial line")
        void detects_a_title() {
            assertThat(JobPosting.parse(POSTING).detectedTitle())
                    .isEqualTo("Software Engineering Intern");
        }

        @Test
        @DisplayName("reads the lowest plausible years requirement")
        void detects_minimum_years() {
            JobPosting posting = JobPosting.parse("""
                    Senior Engineer
                    We need 3+ years of experience. Our company has 40 years of history.
                    """);

            assertThat(posting.minimumYears())
                    .as("40 years is a company age, not a requirement")
                    .isEqualTo(3);
            assertThat(posting.seniority()).isEqualTo("Senior");
        }

        @Test
        @DisplayName("a repeated skill outranks one mentioned once")
        void repetition_raises_priority() {
            JobPosting posting = JobPosting.parse("""
                    Requirements:
                    - Strong Java experience
                    - Java testing with JUnit
                    - Some Ruby familiarity
                    """);

            JobPosting.RequiredSkill java = posting.skills().stream()
                    .filter(skill -> skill.normalizedName().equals("java"))
                    .findFirst().orElseThrow();

            assertThat(java.mentions()).isGreaterThan(1);
            assertThat(java.priority()).isGreaterThan(50);
        }

        @Test
        @DisplayName("an empty posting does not throw")
        void empty_posting_is_survivable() {
            assertThat(JobPosting.parse("").skills()).isEmpty();
            assertThat(JobPosting.parse(null).skills()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Comparing")
    class Comparing {

        @Test
        @DisplayName("a matching resume scores well and lists no required gaps")
        void strong_match() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills("Java", "Spring Boot", "SQL", "PostgreSQL", "Git", "Docker")),
                    JobPosting.parse(POSTING));

            assertThat(outcome.overallScore()).isGreaterThanOrEqualTo(60);
            assertThat(outcome.missing().stream().filter(MatchOutcome.SkillComparison::required))
                    .isEmpty();
        }

        @Test
        @DisplayName("a gap quotes the line of the posting that asked for it")
        void gaps_carry_jd_evidence() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills("Python", "Django")),
                    JobPosting.parse(POSTING));

            MatchOutcome.SkillComparison gap = outcome.missing().stream()
                    .filter(skill -> skill.normalizedName().equals("java"))
                    .findFirst().orElseThrow();

            assertThat(gap.jdEvidence())
                    .as("\"Missing: Java\" is unactionable; the line that asked for it is not")
                    .isNotBlank()
                    .containsIgnoringCase("java");
        }

        @Test
        @DisplayName("gaps are ranked, required and repeated first")
        void gaps_are_ranked() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills("Python")),
                    JobPosting.parse(POSTING));

            List<MatchOutcome.SkillComparison> gaps = outcome.missing();
            assertThat(gaps).isNotEmpty();
            assertThat(gaps.get(0).required())
                    .as("A required gap must never rank below an optional one")
                    .isTrue();
            for (int i = 1; i < gaps.size(); i++) {
                assertThat(gaps.get(i - 1).priority()).isGreaterThanOrEqualTo(gaps.get(i).priority());
            }
        }

        @Test
        @DisplayName("a skill only in a bullet still counts as a match")
        void skill_in_prose_counts() {
            ResumeSnapshot snapshot = new ResumeSnapshot(
                    List.of("EXPERIENCE", "- Built three services in Java over one summer"),
                    List.of(new ResumeSnapshot.SectionView("EXPERIENCE", "EXPERIENCE", 0, 1, 1, 95, true)),
                    ResumeSnapshot.ContactView.none(),
                    List.of(), List.of(), List.of(),
                    Set.of(), 1, 40, 240, null, null);

            MatchOutcome outcome = MatchEngine.match(snapshot, JobPosting.parse(POSTING));

            assertThat(outcome.matched())
                    .as("The candidate has the skill and is simply not being credited for it")
                    .anyMatch(skill -> skill.normalizedName().equals("java"));
            assertThat(outcome.suggestions())
                    .anyMatch(s -> s.kind().equals(MatchOutcome.Suggestion.KIND_SURFACE_SKILL));
        }

        @Test
        @DisplayName("skills the posting never mentions are kept as extras")
        void extra_skills_are_kept() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills("Java", "Photoshop", "Figma")),
                    JobPosting.parse(POSTING));

            assertThat(outcome.extra())
                    .extracting(MatchOutcome.SkillComparison::normalizedName)
                    .contains("photoshop", "figma");
        }

        @Test
        @DisplayName("a posting that asks for nothing does not blame the candidate")
        void empty_posting_scores_neutrally() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills("Java")),
                    JobPosting.parse("A very short posting with no stated requirements at all."));

            assertThat(outcome.requiredSkillScore())
                    .as("The candidate failed nothing; the posting simply did not ask")
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("every score stays within 0-100")
        void scores_are_bounded() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills()),
                    JobPosting.parse(POSTING));

            assertThat(outcome.overallScore()).isBetween(0, 100);
            assertThat(outcome.requiredSkillScore()).isBetween(0, 100);
            assertThat(outcome.titleScore()).isBetween(0, 100);
            assertThat(outcome.experienceScore()).isBetween(0, 100);
        }

        @Test
        @DisplayName("no suggestion invents experience the resume does not contain")
        void suggestions_never_fabricate() {
            MatchOutcome outcome = MatchEngine.match(
                    resume(skills("Python")),
                    JobPosting.parse(POSTING));

            outcome.suggestions().stream()
                    .filter(suggestion -> suggestion.before() != null)
                    .forEach(suggestion -> assertThat(suggestion.after())
                            .as("Standing commitment 2: a rewrite must be grounded in the "
                                    + "candidate's own words")
                            .contains(suggestion.before()));

            outcome.suggestions().stream()
                    .filter(s -> s.kind().equals(MatchOutcome.Suggestion.KIND_QUANTIFY))
                    .forEach(s -> assertThat(s.after())
                            .as("The candidate is the only party who knows the number")
                            .contains("["));
        }
    }

    // ------------------------------------------------------------------

    private static List<String> names(List<JobPosting.RequiredSkill> skills) {
        return skills.stream().map(JobPosting.RequiredSkill::normalizedName).toList();
    }

    private static List<ResumeSnapshot.SkillView> skills(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> new ResumeSnapshot.SkillView(
                        name, name.toLowerCase(java.util.Locale.ROOT), "LANGUAGE", 95, 9))
                .toList();
    }

    private static ResumeSnapshot resume(List<ResumeSnapshot.SkillView> skills) {
        return new ResumeSnapshot(
                List.of("Aditi Sharma", "EXPERIENCE", "- Built a service", "SKILLS",
                        skills.stream().map(ResumeSnapshot.SkillView::name)
                                .reduce((a, b) -> a + ", " + b).orElse("")),
                List.of(
                        new ResumeSnapshot.SectionView("EXPERIENCE", "EXPERIENCE", 1, 2, 2, 95, true),
                        new ResumeSnapshot.SectionView("SKILLS", "SKILLS", 3, 4, 4, 95, true)),
                ResumeSnapshot.ContactView.none(),
                skills,
                List.of(),
                List.of(new ResumeSnapshot.ExperienceView("Acme", "Software Engineering Intern",
                        LocalDate.of(2023, 6, 1), LocalDate.of(2024, 8, 31), false, null, 2, 2)),
                Set.of(), 1, 300, 1800, null, null);
    }
}
