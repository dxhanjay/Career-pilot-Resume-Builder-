package com.careerpilot.interview.domain;

import com.careerpilot.interview.domain.InterviewEnums.QuestionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("Interview answer scoring")
class AnswerRubricTest {

    private static final List<String> EXPECTED = List.of(
            "Context: what the situation actually was",
            "Your specific contribution",
            "The outcome, with a number if one exists");

    private static final String STRONG = """
            Last summer I was working on the checkout service at Acme, and the payment
            confirmation page was taking around 4 seconds to load. I profiled it and found we
            were issuing 30 separate queries per render. I decided to batch them into a single
            join and add a Redis cache for the exchange rates. As a result the page came down
            to 600 milliseconds, and support tickets about failed checkouts dropped by 40% over
            the following month. Looking back I would have added the metric dashboard first,
            because I spent two days guessing before I measured anything.
            """;

    @Nested
    @DisplayName("Guard rails")
    class GuardRails {

        @Test
        @DisplayName("a one-line answer is refused rather than scored generously")
        void too_short_is_refused() {
            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate("I did it well.", QuestionKind.BEHAVIOURAL, EXPECTED, null);

            assertThat(assessment.overallScore()).isLessThan(30);
            assertThat(assessment.improvements())
                    .as("The candidate needs to know why, not just that it scored badly")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("an empty answer scores zero without throwing")
        void empty_answer_is_survivable() {
            assertThat(AnswerRubric.evaluate("", QuestionKind.TECHNICAL, EXPECTED, null).overallScore())
                    .isZero();
            assertThat(AnswerRubric.evaluate(null, QuestionKind.TECHNICAL, EXPECTED, null).overallScore())
                    .isZero();
        }

        @Test
        @DisplayName("every score stays within 0-100")
        void scores_are_bounded() {
            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate(STRONG, QuestionKind.BEHAVIOURAL, EXPECTED, null);

            assertThat(assessment.overallScore()).isBetween(0, 100);
            assertThat(assessment.structureScore()).isBetween(0, 100);
            assertThat(assessment.specificityScore()).isBetween(0, 100);
            assertThat(assessment.relevanceScore()).isBetween(0, 100);
            assertThat(assessment.clarityScore()).isBetween(0, 100);
        }

        @Test
        @DisplayName("the same answer always scores the same")
        void scoring_is_deterministic() {
            assertThat(AnswerRubric.evaluate(STRONG, QuestionKind.BEHAVIOURAL, EXPECTED, null))
                    .as("Practice is worthless if the score moves on its own")
                    .isEqualTo(AnswerRubric.evaluate(STRONG, QuestionKind.BEHAVIOURAL, EXPECTED, null));
        }
    }

    @Nested
    @DisplayName("Structure")
    class Structure {

        @Test
        @DisplayName("situation, action and result together score well and earn a strength")
        void full_story_scores_well() {
            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate(STRONG, QuestionKind.BEHAVIOURAL, EXPECTED, null);

            assertThat(assessment.structureScore()).isGreaterThanOrEqualTo(80);
            assertThat(assessment.strengths()).isNotEmpty();
        }

        @Test
        @DisplayName("an answer that is all conclusion is told what is missing")
        void missing_context_is_named() {
            AnswerRubric.AnswerAssessment assessment = AnswerRubric.evaluate("""
                    It went really well overall and the team was pleased with how everything
                    turned out in the end. People said good things about the outcome and it was
                    generally regarded as a success by everybody who was involved at the time.
                    """, QuestionKind.BEHAVIOURAL, EXPECTED, null);

            assertThat(assessment.improvements())
                    .anyMatch(line -> line.toLowerCase().contains("context")
                            || line.toLowerCase().contains("personally did"));
        }

        @Test
        @DisplayName("an answer written entirely in \"we\" is called out")
        void we_over_i_is_penalised() {
            String team = """
                    We were building a new dashboard and we decided we needed to rewrite the
                    query layer. We looked at three options and we chose the one we thought was
                    simplest. We shipped it in June and we saw the load times improve. We were
                    all pleased with how we handled the whole project together as a team.
                    """;

            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate(team, QuestionKind.BEHAVIOURAL, EXPECTED, null);

            assertThat(assessment.improvements())
                    .as("The interviewer is hiring the candidate, not their old team")
                    .anyMatch(line -> line.contains("\"we\""));
        }
    }

    @Nested
    @DisplayName("Specificity")
    class Specificity {

        @Test
        @DisplayName("numbers raise the score and are named as a strength")
        void numbers_are_rewarded() {
            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate(STRONG, QuestionKind.BEHAVIOURAL, EXPECTED, null);

            assertThat(assessment.specificityScore()).isGreaterThanOrEqualTo(70);
            assertThat(assessment.strengths()).anyMatch(line -> line.contains("numbers"));
        }

        @Test
        @DisplayName("an answer with nothing checkable is told so")
        void vague_answers_are_told() {
            String vague = """
                    I worked on improving the performance of the application because it was
                    running slowly for the users. I looked into the problem and made changes to
                    the code that helped the situation, and afterwards things were better than
                    they had been before which everybody appreciated quite a lot really.
                    """;

            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate(vague, QuestionKind.EXPERIENCE_PROBE, EXPECTED, null);

            assertThat(assessment.improvements())
                    .anyMatch(line -> line.contains("checked") || line.contains("scale"));
        }
    }

    @Nested
    @DisplayName("Relevance")
    class Relevance {

        @Test
        @DisplayName("never naming the skill under test costs relevance")
        void unnamed_focus_skill_is_penalised() {
            AnswerRubric.AnswerAssessment named =
                    AnswerRubric.evaluate(STRONG, QuestionKind.TECHNICAL, EXPECTED, "Redis");
            AnswerRubric.AnswerAssessment unnamed =
                    AnswerRubric.evaluate(STRONG, QuestionKind.TECHNICAL, EXPECTED, "Kubernetes");

            assertThat(unnamed.relevanceScore()).isLessThan(named.relevanceScore());
            assertThat(unnamed.improvements())
                    .anyMatch(line -> line.contains("Kubernetes"));
        }

        @Test
        @DisplayName("a question with no expected points does not penalise the answer")
        void no_expected_points_is_neutral() {
            assertThat(AnswerRubric.evaluate(STRONG, QuestionKind.MOTIVATION, List.of(), null)
                    .relevanceScore())
                    .isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Clarity")
    class Clarity {

        @Test
        @DisplayName("repeated hedging is counted and quoted")
        void hedging_is_penalised() {
            String hedged = """
                    I guess I sort of worked on the thing, and it was kind of a database problem
                    I think maybe. You know, I basically looked at it and honestly it was sort of
                    fixed afterwards, probably. I guess it was kind of fine in the end really.
                    """;

            AnswerRubric.AnswerAssessment assessment =
                    AnswerRubric.evaluate(hedged, QuestionKind.TECHNICAL, EXPECTED, null);

            assertThat(assessment.clarityScore()).isLessThan(80);
            assertThat(assessment.improvements()).anyMatch(line -> line.contains("hedged"));
        }

        @Test
        @DisplayName("a strong answer of the right length beats a weak one")
        void strong_beats_weak() {
            int strong = AnswerRubric.evaluate(STRONG, QuestionKind.BEHAVIOURAL, EXPECTED, null)
                    .overallScore();
            int weak = AnswerRubric.evaluate(
                    "I guess I helped with some stuff on the project and it was fine mostly.",
                    QuestionKind.BEHAVIOURAL, EXPECTED, null).overallScore();

            assertThat(strong).isGreaterThan(weak);
        }
    }
}
