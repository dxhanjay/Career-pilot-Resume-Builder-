package com.careerpilot.ats.domain;

import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rubric is the product's central claim, so these tests are about the claim
 * rather than about the arithmetic: does a finding exist, does it name a
 * category, and — above all — does it quote the line it is about.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("ATS rubric")
class AtsRubricTest {

    @Nested
    @DisplayName("Parseability")
    class Parseability {

        @Test
        @DisplayName("a document with no text layer is a critical finding and zeroes the category")
        void no_text_layer_is_fatal() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .warnings("NO_TEXT_LAYER")
                    .build());

            assertThat(finding(assessment, "NO_TEXT_LAYER"))
                    .as("A scanned PDF is the single most common real-world parse failure "
                            + "and must be reported as such")
                    .isNotNull()
                    .extracting(RuleFinding::severity)
                    .isEqualTo(AtsSeverity.CRITICAL);

            assertThat(assessment.scoreFor(AtsCategory.PARSEABILITY)).isZero();
        }

        @Test
        @DisplayName("a multi-column layout is critical and quotes the top of the document")
        void multi_column_is_critical_with_evidence() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .lines("Aditi Sharma", "aditi@example.com", "EXPERIENCE", "- Built things")
                    .warnings("MULTI_COLUMN_LAYOUT")
                    .build());

            RuleFinding finding = finding(assessment, "MULTI_COLUMN_LAYOUT");
            assertThat(finding).isNotNull();
            assertThat(finding.severity()).isEqualTo(AtsSeverity.CRITICAL);
            assertThat(finding.evidence())
                    .as("FR-ATS-03: a finding without a quote is an assertion")
                    .isNotBlank();
        }

        @Test
        @DisplayName("a clean single-column parse is reported as a pass, not silence")
        void clean_parse_reports_a_pass() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot().build());

            assertThat(finding(assessment, "SINGLE_COLUMN"))
                    .as("A report of only failures reads as an accusation and gets closed")
                    .isNotNull()
                    .extracting(RuleFinding::severity)
                    .isEqualTo(AtsSeverity.PASS);
        }

        @Test
        @DisplayName("more than two pages costs points and names the count")
        void long_documents_lose_points() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot().pages(4).build());

            RuleFinding finding = finding(assessment, "TOO_MANY_PAGES");
            assertThat(finding).isNotNull();
            assertThat(finding.title()).contains("4");
            assertThat(finding.pointsLost()).isPositive();
        }

        @Test
        @DisplayName("a two-page resume is not penalised")
        void two_pages_is_fine() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot().pages(2).build());
            assertThat(finding(assessment, "TOO_MANY_PAGES")).isNull();
        }
    }

    @Nested
    @DisplayName("Structure")
    class Structure {

        @Test
        @DisplayName("a missing experience section is critical")
        void missing_experience_is_critical() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot().sections().build());

            RuleFinding finding = finding(assessment, "MISSING_EXPERIENCE");
            assertThat(finding).isNotNull();
            assertThat(finding.severity()).isEqualTo(AtsSeverity.CRITICAL);
            assertThat(finding.recommendation())
                    .as("Every problem must say what to do about it")
                    .isNotBlank();
        }

        @Test
        @DisplayName("present sections are reported as passes")
        void present_sections_pass() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot().build());

            assertThat(finding(assessment, "HAS_EXPERIENCE")).isNotNull();
            assertThat(finding(assessment, "HAS_EDUCATION")).isNotNull();
            assertThat(finding(assessment, "HAS_SKILLS")).isNotNull();
        }

        @Test
        @DisplayName("an experience entry with no dates is reported with the entry quoted")
        void undated_experience_is_flagged() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .experience(new ResumeSnapshot.ExperienceView(
                            "Acme", "Intern", null, null, false, null, 3, 4))
                    .build());

            RuleFinding finding = finding(assessment, "UNDATED_EXPERIENCE");
            assertThat(finding).isNotNull();
            assertThat(finding.detail()).contains("Acme");
            assertThat(finding.lineStart()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Content")
    class Content {

        @Test
        @DisplayName("prose with no bullets is a high-severity finding")
        void no_bullets_is_flagged() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .lines("EXPERIENCE", "I worked at a company doing various tasks over time.")
                    .experienceSectionOver(0, 1)
                    .build());

            RuleFinding finding = finding(assessment, "NO_BULLETS");
            assertThat(finding).isNotNull();
            assertThat(finding.severity()).isEqualTo(AtsSeverity.HIGH);
        }

        @Test
        @DisplayName("\"Responsible for\" is quoted back verbatim")
        void weak_openers_are_quoted() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .lines("EXPERIENCE",
                            "- Responsible for testing the payment module every release",
                            "- Reduced page load time by 40% across the checkout flow")
                    .experienceSectionOver(0, 2)
                    .build());

            RuleFinding finding = finding(assessment, "PASSIVE_PHRASING");
            assertThat(finding).isNotNull();
            assertThat(finding.evidence())
                    .as("Naming the exact wording is what makes the advice actionable")
                    .contains("Responsible for testing");
            assertThat(finding.lineStart()).isEqualTo(1);
        }

        @Test
        @DisplayName("bullets that are all measured and verb-led earn passes")
        void strong_bullets_pass() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .lines("EXPERIENCE",
                            "- Reduced page load time by 40% across 12 checkout screens",
                            "- Built a CI pipeline that cut release time from 3 hours to 20 minutes",
                            "- Migrated 8 services to containers, removing 15 manual deploy steps")
                    .experienceSectionOver(0, 3)
                    .build());

            assertThat(finding(assessment, "STRONG_BULLET_OPENERS")).isNotNull();
            assertThat(finding(assessment, "QUANTIFIED")).isNotNull();
            assertThat(finding(assessment, "WEAK_BULLET_OPENERS")).isNull();
            assertThat(finding(assessment, "UNQUANTIFIED")).isNull();
        }

        @Test
        @DisplayName("bullets with no numbers anywhere lose content points")
        void unquantified_bullets_lose_points() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .lines("EXPERIENCE",
                            "- Built a web application for the student council",
                            "- Designed the database schema and wrote the queries")
                    .experienceSectionOver(0, 2)
                    .build());

            assertThat(finding(assessment, "UNQUANTIFIED")).isNotNull();
            assertThat(assessment.scoreFor(AtsCategory.CONTENT)).isLessThan(100);
        }

        @Test
        @DisplayName("a skills list written with dashes is not counted as weak bullets")
        void skills_dashes_are_not_bullets() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .lines("SKILLS", "- Java, Python, SQL", "EXPERIENCE",
                            "- Cut deployment time by 60% for a team of six")
                    .sections(
                            section("SKILLS", 0, 1),
                            section("EXPERIENCE", 2, 3))
                    .build());

            assertThat(finding(assessment, "UNQUANTIFIED"))
                    .as("Scanning the whole document would count the skills list as an "
                            + "unquantified bullet, which is both wrong and confusing to read")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Contact")
    class Contact {

        @Test
        @DisplayName("no email is critical — the candidate is unreachable")
        void missing_email_is_critical() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .contact(new ResumeSnapshot.ContactView(
                            "Aditi Sharma", null, null, null, null, null, null, 40, 0, 1))
                    .build());

            RuleFinding finding = finding(assessment, "NO_EMAIL");
            assertThat(finding).isNotNull();
            assertThat(finding.severity()).isEqualTo(AtsSeverity.CRITICAL);
        }

        @Test
        @DisplayName("a full contact block scores the category outright")
        void complete_contact_scores_full() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot().build());
            assertThat(assessment.scoreFor(AtsCategory.CONTACT)).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Scoring")
    class Scoring {

        @Test
        @DisplayName("the five category weights sum to 100")
        void weights_sum_to_one_hundred() {
            int total = Arrays.stream(AtsCategory.values()).mapToInt(AtsCategory::weight).sum();
            assertThat(total)
                    .as("A weighted average over weights that do not sum to 100 produces a "
                            + "score that is not out of 100")
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("a good resume outscores a bad one")
        void better_resume_scores_higher() {
            AtsAssessment good = AtsRubric.evaluate(snapshot()
                    .lines("Aditi Sharma", "aditi@example.com", "EXPERIENCE",
                            "- Reduced query latency by 45% across 20 endpoints")
                    .experienceSectionOver(2, 3)
                    .build());

            AtsAssessment bad = AtsRubric.evaluate(snapshot()
                    .lines("EXPERIENCE", "- Responsible for various tasks")
                    .experienceSectionOver(0, 1)
                    .warnings("MULTI_COLUMN_LAYOUT", "SPARSE_TEXT")
                    .contact(ResumeSnapshot.ContactView.none())
                    .skills()
                    .build());

            assertThat(good.overallScore()).isGreaterThan(bad.overallScore());
        }

        @Test
        @DisplayName("the score never leaves 0-100 even when every rule fires")
        void score_is_bounded() {
            AtsAssessment assessment = AtsRubric.evaluate(new ResumeSnapshot(
                    List.of(), List.of(), ResumeSnapshot.ContactView.none(),
                    List.of(), List.of(), List.of(),
                    Set.of("NO_TEXT_LAYER", "ENCRYPTED_DOCUMENT", "SPARSE_TEXT",
                            "MULTI_COLUMN_LAYOUT", "FALLBACK_PARSER_USED"),
                    12, 3, 40, null, null));

            assertThat(assessment.overallScore()).isBetween(0, 100);
            assertThat(assessment.band()).isEqualTo(ScoreBand.NEEDS_WORK);
        }

        @Test
        @DisplayName("findings are ordered most urgent first")
        void findings_are_ordered_by_urgency() {
            AtsAssessment assessment = AtsRubric.evaluate(snapshot()
                    .sections()
                    .contact(ResumeSnapshot.ContactView.none())
                    .skills()
                    .build());

            List<RuleFinding> findings = assessment.findings();
            for (int i = 1; i < findings.size(); i++) {
                assertThat(findings.get(i - 1).severity().rank())
                        .as("A user reading top-down should fix the most expensive thing first")
                        .isGreaterThanOrEqualTo(findings.get(i).severity().rank());
            }
        }

        @Test
        @DisplayName("an empty snapshot produces findings rather than an exception")
        void empty_snapshot_is_survivable() {
            AtsAssessment assessment = AtsRubric.evaluate(new ResumeSnapshot(
                    null, null, null, null, null, null, null, null, null, null, null, null));

            assertThat(assessment.findings()).isNotEmpty();
            assertThat(assessment.overallScore()).isBetween(0, 100);
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private static RuleFinding finding(AtsAssessment assessment, String code) {
        return assessment.findings().stream()
                .filter(f -> f.code().equals(code))
                .findFirst()
                .orElse(null);
    }

    private static ResumeSnapshot.SectionView section(String type, int start, int end) {
        return new ResumeSnapshot.SectionView(type, type, start, start + 1, end, 90, true);
    }

    private static Builder snapshot() {
        return new Builder();
    }

    /**
     * A resume that passes every rule, so each test can break exactly one thing
     * and attribute the resulting finding to that change alone.
     */
    private static final class Builder {

        private List<String> lines = List.of(
                "Aditi Sharma",
                "aditi@example.com | +91 98765 43210 | Bengaluru, India",
                "github.com/aditi",
                "EXPERIENCE",
                "- Reduced checkout latency by 35% across 14 screens",
                "- Built a deployment pipeline that cut release time to 12 minutes",
                "EDUCATION",
                "B.Tech Computer Science, 2024",
                "SKILLS",
                "Java, Python, React, PostgreSQL, Docker, AWS, Git");

        private List<ResumeSnapshot.SectionView> sections = List.of(
                new ResumeSnapshot.SectionView("CONTACT", null, -1, 0, 2, 90, true),
                new ResumeSnapshot.SectionView("EXPERIENCE", "EXPERIENCE", 3, 4, 5, 95, true),
                new ResumeSnapshot.SectionView("EDUCATION", "EDUCATION", 6, 7, 7, 95, true),
                new ResumeSnapshot.SectionView("SKILLS", "SKILLS", 8, 9, 9, 95, true));

        private ResumeSnapshot.ContactView contact = new ResumeSnapshot.ContactView(
                "Aditi Sharma", "aditi@example.com", "+91 98765 43210", "Bengaluru, India",
                null, "github.com/aditi", null, 92, 0, 2);

        private List<ResumeSnapshot.SkillView> skills = List.of(
                new ResumeSnapshot.SkillView("Java", "java", "LANGUAGE", 95, 9),
                new ResumeSnapshot.SkillView("Python", "python", "LANGUAGE", 95, 9),
                new ResumeSnapshot.SkillView("React", "react", "FRAMEWORK", 95, 9),
                new ResumeSnapshot.SkillView("PostgreSQL", "postgresql", "DATABASE", 95, 9),
                new ResumeSnapshot.SkillView("Docker", "docker", "CLOUD_DEVOPS", 95, 9),
                new ResumeSnapshot.SkillView("AWS", "aws", "CLOUD_DEVOPS", 95, 9),
                new ResumeSnapshot.SkillView("Git", "git", "TOOL", 95, 9));

        private List<ResumeSnapshot.ExperienceView> experience = List.of(
                new ResumeSnapshot.ExperienceView("Acme", "Software Engineering Intern",
                        LocalDate.of(2024, 6, 1), LocalDate.of(2024, 8, 31), false, null, 4, 5));

        private Set<String> warnings = Set.of();
        private Integer pages = 1;
        private Integer words = 420;

        Builder lines(String... values) {
            this.lines = List.of(values);
            return this;
        }

        Builder sections(ResumeSnapshot.SectionView... values) {
            this.sections = List.of(values);
            return this;
        }

        /** Replaces the section list with a single experience block. */
        Builder experienceSectionOver(int start, int end) {
            this.sections = List.of(
                    new ResumeSnapshot.SectionView("EXPERIENCE", "EXPERIENCE", start,
                            start + 1, end, 95, true));
            return this;
        }

        Builder contact(ResumeSnapshot.ContactView value) {
            this.contact = value;
            return this;
        }

        Builder skills(ResumeSnapshot.SkillView... values) {
            this.skills = List.of(values);
            return this;
        }

        Builder experience(ResumeSnapshot.ExperienceView... values) {
            this.experience = List.of(values);
            return this;
        }

        Builder warnings(String... codes) {
            this.warnings = Set.of(codes);
            return this;
        }

        Builder pages(int value) {
            this.pages = value;
            return this;
        }

        ResumeSnapshot build() {
            return new ResumeSnapshot(lines, sections, contact, skills, List.of(), experience,
                    warnings, pages, words, words * 6, "application/pdf", "resume.pdf");
        }
    }
}
