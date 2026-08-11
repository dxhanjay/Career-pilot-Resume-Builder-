package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;
import com.careerpilot.parsing.domain.section.SectionSegmenter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExperienceExtractor}.
 *
 * <p>Company-versus-title is the parser's hardest decision, so the layout
 * variants below are the substance of this class. The description assertions
 * matter for a different reason: {@code PRD §7.2} requires that an AI rewrite be
 * diffable against the candidate's original words, which is impossible if the
 * original is normalised on the way in.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("ExperienceExtractor")
class ExperienceExtractorTest {

    private static List<ExperienceEntry> extract(String text) {
        LineModel model = LineModel.of(text);
        List<ResumeSection> sections = SectionSegmenter.segment(model);
        return ExperienceExtractor.extract(model, sections);
    }

    @Nested
    @DisplayName("layout variants")
    class LayoutVariants {

        @Test
        @DisplayName("title and company on separate lines")
        void separateLines() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Backend Engineering Intern
                    Microsoft Research
                    Jan 2024 - Jun 2024
                    - Reduced query latency by 40%""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Backend Engineering Intern");
            assertThat(entry.company()).isEqualTo("Microsoft Research");
            assertThat(entry.startDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(entry.endDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        }

        @Test
        @DisplayName("title and company on one comma-separated line")
        void commaSeparated() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Software Engineer, Google
                    2020 - 2022""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Software Engineer");
            assertThat(entry.company()).isEqualTo("Google");
        }

        @Test
        @DisplayName("company first, pipe-separated, with dates inline")
        void pipeSeparatedCompanyFirst() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Infosys | Systems Engineer | 2021 - 2023""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Systems Engineer");
            assertThat(entry.company()).isEqualTo("Infosys");
        }

        @Test
        @DisplayName("\"at\" as the separator")
        void atSeparator() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Data Analyst at Deloitte
                    2022 - 2024""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Data Analyst");
            assertThat(entry.company()).isEqualTo("Deloitte");
        }
    }

    @Nested
    @DisplayName("ongoing roles")
    class OngoingRoles {

        @Test
        @DisplayName("are flagged current with no end date")
        void flagsCurrent() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Software Engineer, Razorpay
                    Mar 2023 - Present
                    - Own the payments reconciliation service""").get(0);

            assertThat(entry.current()).isTrue();
            assertThat(entry.endDate()).isNull();
            assertThat(entry.startDate()).isEqualTo(LocalDate.of(2023, 3, 1));
        }
    }

    @Nested
    @DisplayName("descriptions")
    class Descriptions {

        @Test
        @DisplayName("keep the candidate's bullets verbatim")
        void keepsBulletsVerbatim() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Backend Intern
                    Acme Systems
                    - Reduced query latency by 40%
                    - Migrated 12 services to Docker""").get(0);

            assertThat(entry.description())
                    .contains("Reduced query latency by 40%")
                    .contains("Migrated 12 services to Docker");
        }

        @Test
        @DisplayName("a bullet is never mistaken for a company or a title")
        void bulletsAreNotHeaders() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Backend Intern
                    Acme Systems
                    - Led a team of engineers building a new analyst dashboard""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Backend Intern");
            assertThat(entry.company()).isEqualTo("Acme Systems");
        }
    }

    @Nested
    @DisplayName("entry separation")
    class EntrySeparation {

        @Test
        @DisplayName("a blank line separates two jobs")
        void blankLineSeparates() {
            List<ExperienceEntry> entries = extract("""
                    WORK EXPERIENCE
                    Backend Intern
                    Acme Systems
                    - Built a service

                    Data Analyst
                    Globex
                    - Built dashboards""");

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).company()).isEqualTo("Acme Systems");
            assertThat(entries.get(1).company()).isEqualTo("Globex");
        }

        @Test
        @DisplayName("a non-bullet line after bullets starts a new job")
        void bulletBoundarySeparates() {
            List<ExperienceEntry> entries = extract("""
                    WORK EXPERIENCE
                    Backend Intern, Acme Systems
                    - Built a service
                    Data Analyst, Globex
                    - Built dashboards""");

            assertThat(entries).hasSize(2);
            assertThat(entries.get(1).jobTitle()).isEqualTo("Data Analyst");
        }

        @Test
        @DisplayName("a year inside an achievement bullet does not split a job")
        void yearInBulletDoesNotSplit() {
            List<ExperienceEntry> entries = extract("""
                    WORK EXPERIENCE
                    Backend Intern
                    Acme Systems
                    - Cut infrastructure costs 30% in 2023
                    - Shipped the 2024 migration ahead of schedule""");

            assertThat(entries).hasSize(1);
        }
    }

    @Nested
    @DisplayName("company markers")
    class CompanyMarkers {

        @Test
        @DisplayName("an organisation suffix outranks a role word")
        void organisationSuffixWins() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Consultant
                    Engineer Solutions Pvt Ltd
                    2021 - 2022""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Consultant");
            assertThat(entry.company()).isEqualTo("Engineer Solutions Pvt Ltd");
        }
    }

    @Nested
    @DisplayName("scope")
    class Scope {

        @Test
        @DisplayName("a resume with no experience section yields nothing")
        void handlesNoSection() {
            assertThat(extract("EDUCATION\nB.Tech")).isEmpty();
            assertThat(ExperienceExtractor.extract(null, null)).isEmpty();
        }

        @Test
        @DisplayName("an entry with no recognisable company still yields a row, scored lower")
        void partialEntryStillReturned() {
            ExperienceEntry entry = extract("""
                    WORK EXPERIENCE
                    Freelance Developer
                    - Built websites for local businesses""").get(0);

            assertThat(entry.jobTitle()).isEqualTo("Freelance Developer");
            assertThat(entry.company()).isNull();
            assertThat(entry.confidence()).isLessThan(90);
        }
    }
}
