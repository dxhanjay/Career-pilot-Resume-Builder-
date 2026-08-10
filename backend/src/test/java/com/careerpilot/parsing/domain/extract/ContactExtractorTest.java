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
 * Unit tests for {@link ContactExtractor}.
 *
 * <p>The name heuristic gets the most attention here because it is the only
 * genuinely uncertain field, and because it is the most visible: a wrong name on
 * the "here's what the machine saw" screen discredits every other number on the
 * page. The tests assert that it declines to answer rather than guessing when
 * the signals are weak.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("ContactExtractor")
class ContactExtractorTest {

    private static ContactDetails extract(String text) {
        LineModel model = LineModel.of(text);
        List<ResumeSection> sections = SectionSegmenter.segment(model);
        return ContactExtractor.extract(model, sections);
    }

    private static final String STANDARD_HEADER = """
            Aditi Sharma
            aditi.sharma@example.com | +91 98765 43210
            linkedin.com/in/aditisharma | github.com/aditisharma
            Bengaluru, Karnataka

            PROFESSIONAL SUMMARY
            Backend engineer.""";

    @Nested
    @DisplayName("on a standard header")
    class StandardHeader {

        private final ContactDetails contact = extract(STANDARD_HEADER);

        @Test
        @DisplayName("extracts the email address")
        void extractsEmail() {
            assertThat(contact.email()).isEqualTo("aditi.sharma@example.com");
        }

        @Test
        @DisplayName("extracts the phone number")
        void extractsPhone() {
            assertThat(contact.phone()).contains("98765");
            assertThat(contact.phone().replaceAll("\\D", "")).hasSizeBetween(9, 15);
        }

        @Test
        @DisplayName("extracts and classifies profile links")
        void extractsLinks() {
            assertThat(contact.linkedinUrl()).contains("linkedin.com/in/aditisharma");
            assertThat(contact.githubUrl()).contains("github.com/aditisharma");
        }

        @Test
        @DisplayName("extracts the name")
        void extractsName() {
            assertThat(contact.fullName()).isEqualTo("Aditi Sharma");
            assertThat(contact.nameConfidence()).isGreaterThanOrEqualTo(80);
        }

        @Test
        @DisplayName("extracts a City, Region location")
        void extractsLocation() {
            assertThat(contact.location()).isEqualTo("Bengaluru, Karnataka");
        }

        @Test
        @DisplayName("reports the block as reachable and bounded")
        void reportsBlockMetadata() {
            assertThat(contact.isEmpty()).isFalse();
            assertThat(contact.isReachable()).isTrue();
            assertThat(contact.lineStart()).isZero();
            assertThat(contact.confidence()).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("the name heuristic")
    class NameHeuristic {

        @Test
        @DisplayName("skips a document-title line above the name")
        void skipsDocumentTitle() {
            // The most common template opening in Indian and European resumes.
            ContactDetails contact = extract("""
                    CURRICULUM VITAE
                    Aditi Sharma
                    aditi@example.com

                    EDUCATION
                    B.Tech""");

            assertThat(contact.fullName()).isEqualTo("Aditi Sharma");
        }

        @Test
        @DisplayName("skips \"Resume\" and \"Bio Data\" titles too")
        void skipsOtherDocumentTitles() {
            assertThat(extract("Resume\nAditi Sharma\naditi@example.com\n\nEDUCATION\nB.Tech")
                    .fullName()).isEqualTo("Aditi Sharma");
            assertThat(extract("Bio Data\nAditi Sharma\naditi@example.com\n\nEDUCATION\nB.Tech")
                    .fullName()).isEqualTo("Aditi Sharma");
        }

        @Test
        @DisplayName("never returns a section heading as a name")
        void skipsSectionHeadings() {
            ContactDetails contact = extract("""
                    Professional Summary
                    aditi@example.com

                    EDUCATION
                    B.Tech""");

            assertThat(contact.fullName()).isNotEqualTo("Professional Summary");
        }

        @Test
        @DisplayName("never returns a line containing digits")
        void skipsLinesWithDigits() {
            ContactDetails contact = extract("""
                    Flat 12 Rose Apartments
                    aditi@example.com

                    EDUCATION
                    B.Tech""");

            assertThat(contact.fullName()).isNull();
            assertThat(contact.nameConfidence()).isNull();
        }

        @Test
        @DisplayName("never returns a line carrying contact details")
        void skipsContactLines() {
            ContactDetails contact = extract("""
                    aditi.sharma@example.com

                    EDUCATION
                    B.Tech""");

            assertThat(contact.fullName()).isNull();
            assertThat(contact.email()).isEqualTo("aditi.sharma@example.com");
        }

        @Test
        @DisplayName("declines a single-word line rather than guessing")
        void declinesSingleWord() {
            ContactDetails contact = extract("""
                    Aditi
                    aditi@example.com

                    EDUCATION
                    B.Tech""");

            assertThat(contact.fullName()).isNull();
        }

        @Test
        @DisplayName("accepts names with hyphens and apostrophes")
        void acceptsRealNamePunctuation() {
            assertThat(extract("Mary-Jane O'Connor\nmj@example.com\n\nEDUCATION\nB.Tech")
                    .fullName()).isEqualTo("Mary-Jane O'Connor");
        }

        @Test
        @DisplayName("scores a first-line name above one found further down")
        void scoresPositionally() {
            Integer first = extract("Aditi Sharma\naditi@example.com\n\nEDUCATION\nB.Tech")
                    .nameConfidence();
            Integer later = extract("CURRICULUM VITAE\nAditi Sharma\naditi@example.com\n\nEDUCATION\nB.Tech")
                    .nameConfidence();

            assertThat(first).isGreaterThan(later);
        }
    }

    @Nested
    @DisplayName("phone detection")
    class PhoneDetection {

        @Test
        @DisplayName("ignores a year range")
        void ignoresYearRanges() {
            ContactDetails contact = extract("""
                    Aditi Sharma
                    aditi@example.com
                    2022 - 2026

                    EDUCATION
                    B.Tech""");

            assertThat(contact.phone()).isNull();
        }

        @Test
        @DisplayName("accepts common Indian and international formats")
        void acceptsCommonFormats() {
            assertThat(extract("Aditi\n+91-98765-43210\n\nEDUCATION\nB.Tech").phone())
                    .isNotNull();
            assertThat(extract("Aditi\n(555) 123-4567\n\nEDUCATION\nB.Tech").phone())
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("link classification")
    class LinkClassification {

        @Test
        @DisplayName("a personal site becomes the portfolio link, not LinkedIn or GitHub")
        void classifiesPortfolio() {
            ContactDetails contact = extract("""
                    Aditi Sharma
                    aditi@example.com
                    linkedin.com/in/aditi | https://aditi.dev

                    EDUCATION
                    B.Tech""");

            assertThat(contact.linkedinUrl()).contains("linkedin.com");
            assertThat(contact.portfolioUrl()).isEqualTo("https://aditi.dev");
        }

        @Test
        @DisplayName("does not report a LinkedIn URL as a portfolio")
        void doesNotDuplicateLinks() {
            ContactDetails contact = extract("""
                    Aditi Sharma
                    aditi@example.com
                    linkedin.com/in/aditi

                    EDUCATION
                    B.Tech""");

            assertThat(contact.portfolioUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("degenerate input")
    class DegenerateInput {

        @Test
        @DisplayName("an empty document yields an empty result")
        void handlesEmpty() {
            assertThat(ContactExtractor.extract(LineModel.of(""), List.of()).isEmpty()).isTrue();
            assertThat(ContactExtractor.extract(null, null).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("a header extracted as nothing is reported as unreachable, not invented")
        void reportsMissingContactBlock() {
            ContactDetails contact = extract("""
                    EDUCATION
                    B.Tech, Computer Science
                    National Institute of Technology""");

            assertThat(contact.isReachable()).isFalse();
            assertThat(contact.email()).isNull();
            assertThat(contact.phone()).isNull();
        }

        @Test
        @DisplayName("finds an email in a footer when the header has none")
        void findsEmailAnywhere() {
            ContactDetails contact = extract("""
                    Aditi Sharma

                    EDUCATION
                    B.Tech

                    Contact me at aditi@example.com""");

            assertThat(contact.email()).isEqualTo("aditi@example.com");
        }
    }
}
