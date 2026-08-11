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
 * Unit tests for {@link EducationExtractor}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("EducationExtractor")
class EducationExtractorTest {

    private static List<EducationEntry> extract(String text) {
        LineModel model = LineModel.of(text);
        List<ResumeSection> sections = SectionSegmenter.segment(model);
        return EducationExtractor.extract(model, sections);
    }

    @Nested
    @DisplayName("a conventional entry")
    class ConventionalEntry {

        private final EducationEntry entry = extract("""
                EDUCATION
                B.Tech in Computer Science
                National Institute of Technology, Warangal
                2022 - 2026
                CGPA: 8.7""").get(0);

        @Test
        @DisplayName("extracts the degree as written")
        void extractsDegree() {
            assertThat(entry.degree()).isEqualTo("B.Tech");
        }

        @Test
        @DisplayName("extracts the field of study")
        void extractsField() {
            assertThat(entry.fieldOfStudy()).isEqualTo("Computer Science");
        }

        @Test
        @DisplayName("extracts the institution")
        void extractsInstitution() {
            assertThat(entry.institution()).contains("National Institute of Technology");
        }

        @Test
        @DisplayName("extracts the dates")
        void extractsDates() {
            assertThat(entry.startDate()).isEqualTo(LocalDate.of(2022, 1, 1));
            assertThat(entry.endDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        }

        @Test
        @DisplayName("extracts the grade")
        void extractsGrade() {
            assertThat(entry.grade()).isEqualTo("8.7");
        }

        @Test
        @DisplayName("scores high when everything was found")
        void scoresHigh() {
            assertThat(entry.confidence()).isGreaterThanOrEqualTo(90);
        }
    }

    @Nested
    @DisplayName("variants")
    class Variants {

        @Test
        @DisplayName("handles a percentage grade")
        void handlesPercentage() {
            assertThat(extract("""
                    EDUCATION
                    Higher Secondary, Delhi Public School
                    2020, 92.5%""").get(0).grade()).isEqualTo("92.5%");
        }

        @Test
        @DisplayName("handles degree and institution on one line")
        void handlesSingleLineEntry() {
            EducationEntry entry = extract("""
                    EDUCATION
                    MBA, Indian Institute of Management, 2021 - 2023""").get(0);

            assertThat(entry.degree()).isEqualTo("MBA");
            assertThat(entry.institution()).contains("Indian Institute of Management");
        }

        @Test
        @DisplayName("separates two qualifications into two entries")
        void separatesEntries() {
            List<EducationEntry> entries = extract("""
                    EDUCATION
                    B.Tech in Computer Science
                    National Institute of Technology
                    2022 - 2026

                    Higher Secondary
                    Delhi Public School
                    2020 - 2022""");

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).degree()).isEqualTo("B.Tech");
            assertThat(entries.get(1).degree()).isEqualToIgnoringCase("Higher Secondary");
        }

        @Test
        @DisplayName("a lone year is read as a completion date")
        void loneYearIsCompletion() {
            EducationEntry entry = extract("""
                    EDUCATION
                    B.Tech, 2026""").get(0);

            assertThat(entry.startDate()).isNull();
            assertThat(entry.endDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        }
    }

    @Nested
    @DisplayName("partial data")
    class PartialData {

        @Test
        @DisplayName("a degree with no institution still yields an entry, scored lower")
        void handlesMissingInstitution() {
            EducationEntry entry = extract("""
                    EDUCATION
                    B.Tech in Computer Science""").get(0);

            assertThat(entry.degree()).isEqualTo("B.Tech");
            assertThat(entry.institution()).isNull();
            assertThat(entry.confidence()).isLessThan(90);
        }
    }

    @Nested
    @DisplayName("scope")
    class Scope {

        @Test
        @DisplayName("a degree mentioned in experience is not a qualification")
        void ignoresDegreesElsewhere() {
            assertThat(extract("""
                    WORK EXPERIENCE
                    Research Assistant
                    - Worked alongside PhD researchers at the university""")).isEmpty();
        }

        @Test
        @DisplayName("a resume with no education section yields nothing")
        void handlesNoSection() {
            assertThat(extract("TECHNICAL SKILLS\nJava, Python")).isEmpty();
            assertThat(EducationExtractor.extract(null, null)).isEmpty();
        }
    }
}
