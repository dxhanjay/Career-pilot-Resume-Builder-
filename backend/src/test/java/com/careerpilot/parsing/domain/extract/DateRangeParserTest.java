package com.careerpilot.parsing.domain.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DateRangeParser}.
 *
 * <p>A misread date is worse than a missing one: it produces an employment gap
 * the candidate never had, and the ATS analyser will then advise them to explain
 * it. The refusal cases below matter as much as the parsing ones.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("DateRangeParser")
class DateRangeParserTest {

    @Nested
    @DisplayName("ranges")
    class Ranges {

        @Test
        @DisplayName("parses month-and-year on both sides")
        void parsesMonthYearRange() {
            DateRange range = DateRangeParser.parse("Jan 2022 - Dec 2023");

            assertThat(range.start()).isEqualTo(LocalDate.of(2022, 1, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2023, 12, 1));
            assertThat(range.current()).isFalse();
            assertThat(range.precision()).isEqualTo(DateRange.DatePrecision.MONTH);
        }

        @Test
        @DisplayName("parses full month names")
        void parsesFullMonthNames() {
            DateRange range = DateRangeParser.parse("March 2022 to December 2023");

            assertThat(range.start()).isEqualTo(LocalDate.of(2022, 3, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2023, 12, 1));
        }

        @Test
        @DisplayName("parses a year-only range and reports lower precision")
        void parsesYearRange() {
            DateRange range = DateRangeParser.parse("2022 - 2026");

            assertThat(range.start()).isEqualTo(LocalDate.of(2022, 1, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(range.precision()).isEqualTo(DateRange.DatePrecision.YEAR);
        }

        @Test
        @DisplayName("parses numeric month/year")
        void parsesNumericMonthYear() {
            DateRange range = DateRangeParser.parse("06/2021 - 08/2021");

            assertThat(range.start()).isEqualTo(LocalDate.of(2021, 6, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2021, 8, 1));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Jan 2022 – Dec 2023",
                "Jan 2022 — Dec 2023",
                "Jan 2022 to Dec 2023",
                "Jan 2022 | Dec 2023"
        })
        @DisplayName("accepts the separators templates use")
        void acceptsSeparators(String text) {
            assertThat(DateRangeParser.parse(text).start())
                    .isEqualTo(LocalDate.of(2022, 1, 1));
        }
    }

    @Nested
    @DisplayName("ongoing roles")
    class OngoingRoles {

        @ParameterizedTest
        @ValueSource(strings = {
                "Jan 2022 - Present",
                "Jan 2022 - Current",
                "Jan 2022 - Now",
                "Jan 2022 - Till Date",
                "Jan 2022 - Ongoing"
        })
        @DisplayName("are open-ended and flagged current")
        void detectsCurrent(String text) {
            DateRange range = DateRangeParser.parse(text);

            assertThat(range.start()).isEqualTo(LocalDate.of(2022, 1, 1));
            assertThat(range.end()).isNull();
            assertThat(range.current()).isTrue();
        }
    }

    @Nested
    @DisplayName("a single date")
    class SingleDate {

        @Test
        @DisplayName("goes into end, not start")
        void singleDateIsTerminal() {
            DateRange range = DateRangeParser.parse("Graduated 2026");

            assertThat(range.single()).isTrue();
            assertThat(range.start()).isNull();
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 1));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest
        @ValueSource(strings = {
                "Reduced latency by 40%",
                "Led a team of 5 engineers",
                "Scored 8.7 CGPA",
                "",
                "   "
        })
        @DisplayName("lines with no date yield nothing")
        void refusesNonDates(String text) {
            assertThat(DateRangeParser.parse(text).isEmpty()).isTrue();
            assertThat(DateRangeParser.hasDate(text)).isFalse();
        }

        @Test
        @DisplayName("null is tolerated")
        void tolerantOfNull() {
            assertThat(DateRangeParser.parse(null).isEmpty()).isTrue();
            assertThat(DateRangeParser.stripDates(null)).isNull();
        }

        @Test
        @DisplayName("a three-digit or five-digit number is not a year")
        void refusesNonYears() {
            assertThat(DateRangeParser.parse("Handled 500 requests per second").isEmpty())
                    .isTrue();
            assertThat(DateRangeParser.parse("Processed 12345 records").isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("stripDates")
    class StripDates {

        @Test
        @DisplayName("leaves the non-date part of a header")
        void stripsFromHeader() {
            assertThat(DateRangeParser.stripDates("Software Engineer, Google | 2020 - 2022"))
                    .isEqualTo("Software Engineer, Google");
        }

        @Test
        @DisplayName("removes present markers too")
        void stripsPresent() {
            assertThat(DateRangeParser.stripDates("Backend Intern | Jan 2022 - Present"))
                    .isEqualTo("Backend Intern");
        }

        @Test
        @DisplayName("removes brackets left empty by the removal")
        void stripsEmptyBrackets() {
            assertThat(DateRangeParser.stripDates("Data Analyst (2021 - 2023)"))
                    .isEqualTo("Data Analyst");
        }

        @Test
        @DisplayName("leaves a line with no dates unchanged")
        void leavesPlainTextAlone() {
            assertThat(DateRangeParser.stripDates("Backend Engineering Intern"))
                    .isEqualTo("Backend Engineering Intern");
        }
    }
}
