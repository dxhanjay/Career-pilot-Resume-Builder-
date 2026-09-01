package com.careerpilot.interview.domain;

import com.careerpilot.interview.domain.InterviewEnums.QuestionKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Scores an interview answer on what it says.
 *
 * <p>ADR-0003: content only. There is no audio, no video, and no affect model
 * anywhere in this module, so nothing here can score a candidate on their accent,
 * their face, or how nervous they sounded.
 *
 * <p>Four axes, each honest about what it can actually detect:
 *
 * <ul>
 *   <li><b>Structure</b> — whether the answer moves through situation, action and
 *       result rather than jumping straight to a conclusion.</li>
 *   <li><b>Specificity</b> — whether anything in it could be checked. Numbers,
 *       named tools, named artefacts.</li>
 *   <li><b>Relevance</b> — whether it covers the cues this question was asking
 *       for.</li>
 *   <li><b>Clarity</b> — length, hedging, and filler.</li>
 * </ul>
 *
 * <p>Deterministic. A candidate who resubmits the same answer gets the same
 * score, which is the minimum requirement for practice to mean anything.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class AnswerRubric {

    /** Stored on every answer; bump on any change to scoring. */
    public static final String VERSION = "1.0.0";

    /** Below this, there is not enough answer to judge. */
    private static final int TOO_SHORT_WORDS = 25;

    /** A good spoken answer runs roughly 90 seconds — about this many words. */
    private static final int IDEAL_MIN_WORDS = 70;
    private static final int IDEAL_MAX_WORDS = 320;

    private static final Pattern SITUATION = Pattern.compile(
            "(?i)\\b(when|while|during|at the time|the situation|we were|i was working|"
                    + "last (year|summer|semester|term)|in my (role|internship|project)|"
                    + "the (project|team|company|client))\\b");

    private static final Pattern ACTION = Pattern.compile(
            "(?i)\\b(i (built|wrote|designed|implemented|refactored|migrated|debugged|led|"
                    + "proposed|decided|chose|tested|automated|fixed|shipped|reviewed|"
                    + "investigated|rewrote|set up|added|removed|measured|profiled)|"
                    + "so i |what i did|my approach|i started by|i decided)\\b");

    private static final Pattern RESULT = Pattern.compile(
            "(?i)\\b(as a result|which (meant|led|reduced|improved|allowed)|the outcome|"
                    + "in the end|we ended up|this (reduced|improved|saved|increased|cut)|"
                    + "afterwards|the result was|it went live|we shipped|finally)\\b");

    private static final Pattern REFLECTION = Pattern.compile(
            "(?i)\\b(i learned|i would|next time|looking back|in hindsight|if i did it again|"
                    + "what i took from|i now)\\b");

    private static final Pattern NUMBER = Pattern.compile(
            "(?i)(\\d+\\s*%|[$£€₹]\\s*\\d|\\b\\d[\\d,.]*\\b|\\b(half|double|triple|twice)\\b)");

    private static final Pattern HEDGE = Pattern.compile(
            "(?i)\\b(kind of|sort of|i guess|i think maybe|probably|somewhat|a bit|"
                    + "i'm not really sure|or something|whatever|stuff like that|"
                    + "you know|basically|literally|honestly)\\b");

    /** Token separator for the cue fallback. A field, so the escaping lives in one place. */
    private static final String NON_LETTER = "[^\\p{L}]+";

    private static final Pattern WE_NOT_I = Pattern.compile("(?i)\\bwe\\b");
    private static final Pattern I_DID = Pattern.compile("(?i)\\bi\\b");

    private AnswerRubric() {
    }

    /**
     * Scores one answer.
     *
     * @param answer         what the candidate wrote
     * @param kind           the question type, which changes what "good" means
     * @param expectedPoints the cues the question was fishing for
     * @param focusSkill     the skill under test, or null
     * @return the assessment, never null
     */
    public static AnswerAssessment evaluate(String answer,
                                            QuestionKind kind,
                                            List<String> expectedPoints,
                                            String focusSkill) {
        String text = answer == null ? "" : answer.strip();
        int words = countWords(text);

        if (words < TOO_SHORT_WORDS) {
            return new AnswerAssessment(
                    words == 0 ? 0 : 15, 10, 10, 10, 20, words, VERSION,
                    List.of(),
                    List.of("This is too short for an interviewer to judge. Aim for 90 seconds "
                                    + "of speech — roughly 120 to 200 words.",
                            "Start with the situation, then what you personally did, then what "
                                    + "happened as a result."));
        }

        String lower = text.toLowerCase(Locale.ROOT);
        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();

        int structure = scoreStructure(lower, kind, strengths, improvements);
        int specificity = scoreSpecificity(text, lower, strengths, improvements);
        int relevance = scoreRelevance(lower, expectedPoints, focusSkill, strengths, improvements);
        int clarity = scoreClarity(text, lower, words, strengths, improvements);

        // Structure and specificity carry the most weight because they are what
        // separates a rehearsed answer from a real one. Clarity matters least:
        // an interviewer will forgive a rambling answer that contains evidence,
        // and will not forgive a polished one that contains none.
        int overall = (int) Math.round(
                structure * 0.30 + specificity * 0.30 + relevance * 0.25 + clarity * 0.15);

        if (strengths.isEmpty()) {
            strengths.add("You answered in full sentences and stayed on the question.");
        }

        return new AnswerAssessment(overall, structure, specificity, relevance, clarity,
                words, VERSION, List.copyOf(strengths), List.copyOf(improvements));
    }

    // ------------------------------------------------------------------

    private static int scoreStructure(String lower, QuestionKind kind,
                                      List<String> strengths, List<String> improvements) {
        boolean hasSituation = SITUATION.matcher(lower).find();
        boolean hasAction = ACTION.matcher(lower).find();
        boolean hasResult = RESULT.matcher(lower).find();
        boolean hasReflection = REFLECTION.matcher(lower).find();

        int score = 25;
        if (hasSituation) {
            score += 20;
        }
        if (hasAction) {
            score += 25;
        }
        if (hasResult) {
            score += 20;
        }
        if (hasReflection) {
            score += 10;
        }
        score = Math.min(100, score);

        boolean narrative = kind == QuestionKind.BEHAVIOURAL
                || kind == QuestionKind.EXPERIENCE_PROBE
                || kind == QuestionKind.PROJECT_DEEP_DIVE;

        if (hasSituation && hasAction && hasResult) {
            strengths.add("The answer moves through situation, action and outcome — which is "
                    + "what makes a story easy for an interviewer to follow and score.");
        } else if (narrative) {
            if (!hasSituation) {
                improvements.add("Open with one sentence of context: where you were, when, and "
                        + "what the problem was. Interviewers cannot judge a decision without it.");
            }
            if (!hasAction) {
                improvements.add("Say what you personally did, in the first person. \"I decided\", "
                        + "\"I built\", \"I argued for\" — not what the team did around you.");
            }
            if (!hasResult) {
                improvements.add("Finish with what happened. An answer with no outcome sounds "
                        + "like an activity rather than an achievement.");
            }
        }

        long weCount = WE_NOT_I.matcher(lower).results().count();
        long iCount = I_DID.matcher(lower).results().count();
        if (weCount > iCount * 2 && weCount >= 3) {
            score = Math.max(0, score - 15);
            improvements.add("You said \"we\" far more than \"I\". The interviewer is hiring you, "
                    + "not your old team — be specific about your own contribution.");
        }

        return score;
    }

    private static int scoreSpecificity(String text, String lower,
                                        List<String> strengths, List<String> improvements) {
        long numbers = NUMBER.matcher(text).results().count();
        long properNouns = countProperNouns(text);

        int score = 20;
        score += Math.min(45, numbers * 15);
        score += Math.min(35, properNouns * 7);
        score = Math.min(100, score);

        if (numbers >= 2) {
            strengths.add("You used concrete numbers. That is the single biggest difference "
                    + "between an answer that is believed and one that is politely noted.");
        } else if (numbers == 0) {
            improvements.add("Nothing in this answer can be checked. Add scale or outcome — how "
                    + "many users, how much faster, how long it took, how many people.");
        }

        if (properNouns == 0) {
            improvements.add("Name the actual tools, systems, or products involved. Generic "
                    + "answers are indistinguishable from answers about nothing.");
        }

        return score;
    }

    private static int scoreRelevance(String lower, List<String> expectedPoints,
                                      String focusSkill, List<String> strengths,
                                      List<String> improvements) {
        if (expectedPoints == null || expectedPoints.isEmpty()) {
            return 70;
        }

        int covered = 0;
        List<String> missed = new ArrayList<>();
        for (String point : expectedPoints) {
            if (coversPoint(lower, point)) {
                covered++;
            } else {
                missed.add(point);
            }
        }

        int score = (int) Math.round(100.0 * covered / expectedPoints.size());

        if (focusSkill != null && !lower.contains(focusSkill.toLowerCase(Locale.ROOT))) {
            score = Math.max(0, score - 25);
            improvements.add("You were asked about " + focusSkill + " and never named it. Use "
                    + "the interviewer's own vocabulary — it is what they are listening for.");
        }

        if (covered == expectedPoints.size()) {
            strengths.add("You covered everything the question was actually fishing for.");
        } else if (!missed.isEmpty()) {
            improvements.add("The answer did not clearly cover: "
                    + missed.get(0).toLowerCase(Locale.ROOT) + ".");
        }

        return score;
    }

    /**
     * Whether an answer covers what an expected point was asking for.
     *
     * <p>Not literal keyword overlap. An expected point is a <em>description</em>
     * of what a good answer contains — "Context: what the situation actually was"
     * — and a strong answer describing a specific incident will not contain the
     * words "context" or "situation" anywhere in it. Matching those words
     * literally marks every good answer as having missed the point, which is
     * worse than not scoring relevance at all.
     *
     * <p>So each cue is first mapped to the <em>kind</em> of content it asks for
     * and checked against the same structural signals the structure axis uses.
     * Only a cue that matches no known theme — a domain-specific one written for
     * a particular question — falls back to token overlap.
     */
    private static boolean coversPoint(String lowerAnswer, String point) {
        String cue = point.toLowerCase(Locale.ROOT);
        boolean recognisedTheme = false;

        if (mentionsAny(cue, "context", "situation", "what the situation", "where you were",
                "the problem was", "one sentence", "summary")) {
            recognisedTheme = true;
            if (!SITUATION.matcher(lowerAnswer).find()) {
                return false;
            }
        }

        if (mentionsAny(cue, "did", "contribution", "action", "approach", "decision", "decided",
                "chose", "method", "plan", "own", "acknowledge", "honest", "plainly")) {
            recognisedTheme = true;
            if (!ACTION.matcher(lowerAnswer).find()) {
                return false;
            }
        }

        if (mentionsAny(cue, "outcome", "result", "happened", "impact", "proof", "evidence",
                "worked", "shipped")) {
            recognisedTheme = true;
            if (!RESULT.matcher(lowerAnswer).find() && !NUMBER.matcher(lowerAnswer).find()) {
                return false;
            }
        }

        if (mentionsAny(cue, "number", "measur", "scale", "how many", "how much")) {
            recognisedTheme = true;
            if (!NUMBER.matcher(lowerAnswer).find()) {
                return false;
            }
        }

        if (mentionsAny(cue, "learn", "differently", "change", "hindsight", "next time",
                "would you")) {
            recognisedTheme = true;
            if (!REFLECTION.matcher(lowerAnswer).find()) {
                return false;
            }
        }

        if (recognisedTheme) {
            return true;
        }

        // An unrecognised cue is question-specific vocabulary. Loose token
        // overlap is the honest fallback: a false positive here is much cheaper
        // than telling somebody they missed a point they made.
        String[] tokens = cue.split(NON_LETTER);
        int significant = 0;
        int hits = 0;
        for (String token : tokens) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) {
                continue;
            }
            significant++;
            if (lowerAnswer.contains(token)) {
                hits++;
            }
        }
        return significant == 0 || hits * 2 >= significant;
    }

    private static boolean mentionsAny(String cue, String... needles) {
        for (String needle : needles) {
            if (cue.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static int scoreClarity(String text, String lower, int words,
                                    List<String> strengths, List<String> improvements) {
        int score = 100;

        if (words < IDEAL_MIN_WORDS) {
            score -= 30;
            improvements.add("At " + words + " words this is under 45 seconds of speech. Most "
                    + "interview answers want 90 seconds — there is room for a second example.");
        } else if (words > IDEAL_MAX_WORDS) {
            score -= 25;
            improvements.add("At " + words + " words this runs past three minutes. Interviewers "
                    + "stop listening; cut the setup, keep the decision and the result.");
        } else {
            strengths.add("Length is right for a spoken answer — long enough to be substantive, "
                    + "short enough to be heard.");
        }

        long hedges = HEDGE.matcher(lower).results().count();
        if (hedges >= 3) {
            score -= (int) Math.min(30, hedges * 6);
            improvements.add("You hedged " + hedges + " times (\"kind of\", \"I guess\", \"you "
                    + "know\"). Hedging makes a true claim sound uncertain. State it, then "
                    + "qualify once if you must.");
        }

        double averageSentence = averageSentenceLength(text);
        if (averageSentence > 38) {
            score -= 12;
            improvements.add("Your sentences average " + Math.round(averageSentence)
                    + " words. Long sentences are hard to follow out loud — break them.");
        }

        return Math.max(0, Math.min(100, score));
    }

    // ------------------------------------------------------------------


    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "what", "with", "that", "this", "your", "from", "they", "them", "have", "been",
            "were", "will", "would", "about", "which", "their", "there", "than", "then",
            "into", "just", "only", "some", "such", "more", "most", "does", "actually");

    private static long countProperNouns(String text) {
        // A capitalised word that is not the first of a sentence is usually a
        // product, company, or technology name — the thing that makes an answer
        // checkable.
        String[] words = text.split("\\s+");
        long count = 0;
        for (int i = 1; i < words.length; i++) {
            String word = words[i].replaceAll("[^\\p{L}\\p{N}+#.]", "");
            if (word.length() < 2) {
                continue;
            }
            boolean previousEndedSentence = words[i - 1].endsWith(".")
                    || words[i - 1].endsWith("!") || words[i - 1].endsWith("?");
            if (!previousEndedSentence && Character.isUpperCase(word.charAt(0))) {
                count++;
            }
        }
        return count;
    }

    private static double averageSentenceLength(String text) {
        String[] sentences = text.split("[.!?]+");
        int counted = 0;
        int words = 0;
        for (String sentence : sentences) {
            int length = countWords(sentence);
            if (length > 0) {
                counted++;
                words += length;
            }
        }
        return counted == 0 ? 0 : (double) words / counted;
    }

    private static int countWords(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    /**
     * The scored result of one answer.
     *
     * @param strengths    what the answer already does well; never empty
     * @param improvements what to change, most important first
     */
    public record AnswerAssessment(
            int overallScore,
            int structureScore,
            int specificityScore,
            int relevanceScore,
            int clarityScore,
            int wordCount,
            String rubricVersion,
            List<String> strengths,
            List<String> improvements
    ) {
    }
}
