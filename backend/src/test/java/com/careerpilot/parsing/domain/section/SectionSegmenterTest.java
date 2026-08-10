package com.careerpilot.parsing.domain.section;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SectionSegmenter}.
 *
 * <p>Segmentation is the point where a resume stops being a wall of text, so
 * every extractor built on top of it inherits its mistakes. A heading detected
 * one line late swallows a job title; a heading invented inside an employment
 * list splits one job into two. Both are silent — they produce a plausible
 * structure that is wrong — which is why the negative cases below are as
 * detailed as the positive ones.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("SectionSegmenter")
class SectionSegmenterTest {

    /**
     * A conventional single-column resume.
     *
     * <p>Contains three deliberate traps: "Microsoft Research" (an employer whose
     * name ends in a section keyword), "AWS Certified Cloud Practitioner" (a
     * certification line resembling a heading), and "Backend Engineering Intern"
     * (a job title containing "intern").
     */
    private static final String CONVENTIONAL_RESUME = String.join("\n",
            /*  0 */ "Aditi Sharma",
            /*  1 */ "aditi.sharma@example.com | +91 98765 43210",
            /*  2 */ "linkedin.com/in/aditisharma",
            /*  3 */ "",
            /*  4 */ "PROFESSIONAL SUMMARY",
            /*  5 */ "Final-year computer science student seeking a backend role.",
            /*  6 */ "",
            /*  7 */ "EDUCATION",
            /*  8 */ "B.Tech, Computer Science",
            /*  9 */ "National Institute of Technology",
            /* 10 */ "",
            /* 11 */ "TECHNICAL SKILLS & TOOLS",
            /* 12 */ "Java, Spring Boot, PostgreSQL, Docker",
            /* 13 */ "",
            /* 14 */ "WORK EXPERIENCE",
            /* 15 */ "Backend Engineering Intern",
            /* 16 */ "Microsoft Research",
            /* 17 */ "- Reduced query latency by 40%",
            /* 18 */ "",
            /* 19 */ "CERTIFICATIONS",
            /* 20 */ "AWS Certified Cloud Practitioner");

    private static List<ResumeSection> segment(String text) {
        return SectionSegmenter.segment(LineModel.of(text));
    }

    @Nested
    @DisplayName("on a conventional resume")
    class ConventionalResume {

        private final List<ResumeSection> sections = segment(CONVENTIONAL_RESUME);

        @Test
        @DisplayName("finds every section in document order")
        void findsAllSections() {
            assertThat(sections).extracting(ResumeSection::type).containsExactly(
                    SectionType.CONTACT,
                    SectionType.SUMMARY,
                    SectionType.EDUCATION,
                    SectionType.SKILLS,
                    SectionType.EXPERIENCE,
                    SectionType.CERTIFICATIONS);
        }

        @Test
        @DisplayName("treats the block above the first heading as contact detail")
        void capturesContactPreamble() {
            ResumeSection contact = sections.get(0);

            assertThat(contact.type()).isEqualTo(SectionType.CONTACT);
            assertThat(contact.hasHeading()).isFalse();
            assertThat(contact.startLine()).isZero();
            assertThat(contact.endLine()).isEqualTo(3);
            // High confidence: the block really does contain an email and a link.
            assertThat(contact.confidence()).isEqualTo(90);
        }

        @Test
        @DisplayName("bounds each section from its heading to the next one")
        void boundsSectionsCorrectly() {
            ResumeSection experience = sections.stream()
                    .filter(s -> s.type() == SectionType.EXPERIENCE)
                    .findFirst()
                    .orElseThrow();

            assertThat(experience.headingLine()).isEqualTo(14);
            assertThat(experience.headingText()).isEqualTo("WORK EXPERIENCE");
            assertThat(experience.startLine()).isEqualTo(15);
            assertThat(experience.endLine()).isEqualTo(18);
            assertThat(experience.lineCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("the last section runs to the end of the document")
        void lastSectionReachesEnd() {
            ResumeSection last = sections.get(sections.size() - 1);

            assertThat(last.type()).isEqualTo(SectionType.CERTIFICATIONS);
            assertThat(last.endLine()).isEqualTo(20);
        }

        @Test
        @DisplayName("sections cover the document without gaps or overlaps")
        void coversDocumentContiguously() {
            for (int i = 1; i < sections.size(); i++) {
                ResumeSection previous = sections.get(i - 1);
                ResumeSection current = sections.get(i);
                int nextLine = current.hasHeading() ? current.headingLine() : current.startLine();

                assertThat(nextLine)
                        .as("section %s starts immediately after %s ends", current.type(),
                                previous.type())
                        .isEqualTo(previous.endLine() + 1);
            }
        }

        @Test
        @DisplayName("an employer name ending in a keyword does not become a heading")
        void doesNotSplitOnEmployerName() {
            assertThat(sections).extracting(ResumeSection::type)
                    .doesNotContain(SectionType.PUBLICATIONS);
        }

        @Test
        @DisplayName("exact all-caps headings score higher than keyword ones")
        void scoresExactHeadingsHigher() {
            int education = confidenceOf(SectionType.EDUCATION);
            int skills = confidenceOf(SectionType.SKILLS);

            assertThat(education).isGreaterThan(skills);
            assertThat(skills).isGreaterThanOrEqualTo(SectionSegmenter.HEADING_THRESHOLD);
        }

        private int confidenceOf(SectionType type) {
            return sections.stream()
                    .filter(s -> s.type() == type)
                    .findFirst()
                    .orElseThrow()
                    .confidence();
        }
    }

    @Nested
    @DisplayName("disqualifiers")
    class Disqualifiers {

        @Test
        @DisplayName("a bulleted line is never a heading")
        void ignoresBullets() {
            List<ResumeSection> sections = segment(
                    "EXPERIENCE\n- Skills: built a payment service\n- More work");

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).type()).isEqualTo(SectionType.EXPERIENCE);
        }

        @Test
        @DisplayName("a line carrying contact details is never a heading")
        void ignoresContactLines() {
            List<ResumeSection> sections = segment(
                    "SUMMARY\nA developer\n\nCONTACT: me@example.com\nCall me");

            assertThat(sections).extracting(ResumeSection::type)
                    .containsExactly(SectionType.SUMMARY);
        }

        @Test
        @DisplayName("a sentence is never a heading, even with a keyword in it")
        void ignoresProse() {
            List<ResumeSection> sections = segment(
                    "SUMMARY\nExperience with distributed systems, including Kafka.\nMore text");

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).type()).isEqualTo(SectionType.SUMMARY);
        }

        @Test
        @DisplayName("a long line is never a heading")
        void ignoresLongLines() {
            String longLine = "Skills gained across many roles and many teams and many years";

            assertThat(SectionSegmenter.isDisqualified(new DocumentLine(0, longLine))).isTrue();
        }
    }

    @Nested
    @DisplayName("dense layouts")
    class DenseLayouts {

        @Test
        @DisplayName("headings are found without blank lines between sections")
        void findsHeadingsWithoutBlankLines() {
            List<ResumeSection> sections = segment(String.join("\n",
                    "Aditi Sharma",
                    "aditi@example.com",
                    "EDUCATION",
                    "B.Tech, Computer Science",
                    "TECHNICAL SKILLS & TOOLS",
                    "Java, Spring Boot",
                    "WORK EXPERIENCE",
                    "Backend Intern"));

            assertThat(sections).extracting(ResumeSection::type).containsExactly(
                    SectionType.CONTACT,
                    SectionType.EDUCATION,
                    SectionType.SKILLS,
                    SectionType.EXPERIENCE);
        }
    }

    @Nested
    @DisplayName("degenerate documents")
    class DegenerateDocuments {

        @Test
        @DisplayName("a resume with no recognisable headings becomes one unknown block")
        void handlesNoHeadings() {
            List<ResumeSection> sections = segment(String.join("\n",
                    "Aditi Sharma",
                    "Where I have been",
                    "Built a payment service at a startup",
                    "What I know",
                    "Java and Spring Boot"));

            assertThat(sections).hasSize(1);
            ResumeSection only = sections.get(0);
            assertThat(only.type()).isEqualTo(SectionType.UNKNOWN);
            assertThat(only.startLine()).isZero();
            assertThat(only.endLine()).isEqualTo(4);
            // Zero confidence is the honest answer, and the signal an LLM repair
            // pass will later select on.
            assertThat(only.confidence()).isZero();
        }

        @Test
        @DisplayName("an empty document yields no sections")
        void handlesEmptyDocument() {
            assertThat(SectionSegmenter.segment(LineModel.of(""))).isEmpty();
            assertThat(SectionSegmenter.segment(LineModel.of(null))).isEmpty();
            assertThat(SectionSegmenter.segment(null)).isEmpty();
        }

        @Test
        @DisplayName("a heading with nothing under it is reported as empty, not dropped")
        void reportsEmptySection() {
            List<ResumeSection> sections = segment("EDUCATION\nB.Tech\n\nSKILLS");

            ResumeSection skills = sections.get(sections.size() - 1);
            assertThat(skills.type()).isEqualTo(SectionType.SKILLS);
            assertThat(skills.isEmpty()).isTrue();
            assertThat(skills.lineCount()).isZero();
        }

        @Test
        @DisplayName("a document starting at line zero has no contact preamble")
        void handlesHeadingOnFirstLine() {
            List<ResumeSection> sections = segment("EDUCATION\nB.Tech");

            assertThat(sections).hasSize(1);
            assertThat(sections.get(0).type()).isEqualTo(SectionType.EDUCATION);
        }

        @Test
        @DisplayName("a repeated heading produces two sections rather than merging them")
        void keepsRepeatedHeadingsSeparate() {
            List<ResumeSection> sections = segment(
                    "EXPERIENCE\nRole A\n\nEDUCATION\nB.Tech\n\nEXPERIENCE\nRole B");

            assertThat(sections).extracting(ResumeSection::type).containsExactly(
                    SectionType.EXPERIENCE, SectionType.EDUCATION, SectionType.EXPERIENCE);
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        @DisplayName("the same text always segments identically")
        void isDeterministic() {
            assertThat(segment(CONVENTIONAL_RESUME))
                    .isEqualTo(segment(CONVENTIONAL_RESUME));
        }
    }
}
