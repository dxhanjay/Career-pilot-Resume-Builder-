package com.careerpilot.interview.domain;

import com.careerpilot.interview.domain.InterviewEnums.Focus;
import com.careerpilot.interview.domain.InterviewEnums.QuestionKind;
import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Generates an interview from a candidate's own material.
 *
 * <p>ADR-0033: a blueprint decides the <em>shape</em> of the interview — how
 * many of each kind, in what order — and slots are then filled from the resume
 * and the posting. That separation is what stops a session becoming five
 * variations of the same question when a resume happens to list five databases.
 *
 * <p>Every generated question names the thing it came from. "You list Docker"
 * is a question an interviewer who read the resume would ask; "Tell me about
 * containerisation" is one anybody could ask, and is worth much less practice.
 *
 * <p>Deterministic given a seed, so a session can be regenerated identically for
 * debugging, and two candidates with identical resumes get identical practice.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class QuestionBlueprint {

    /** Stored on every session; bump on any change to generation. */
    public static final String VERSION = "1.0.0";

    private QuestionBlueprint() {
    }

    /**
     * Builds a session's questions.
     *
     * @param resume   the candidate's parsed resume, or null for a generic session
     * @param gaps     skills a target posting asks for and the resume lacks; may be empty
     * @param focus    what kind of practice this is
     * @param count    how many questions to produce
     * @param seed     makes selection reproducible
     * @return exactly {@code count} questions, ordered as they should be asked
     */
    public static List<GeneratedQuestion> generate(ResumeSnapshot resume,
                                                   List<String> gaps,
                                                   Focus focus,
                                                   int count,
                                                   long seed) {
        Random random = new Random(seed);
        List<GeneratedQuestion> pool = new ArrayList<>();

        pool.add(opener(resume));

        switch (focus) {
            case BEHAVIOURAL -> pool.addAll(behavioural(random, count));
            case JOB_SPECIFIC -> {
                pool.addAll(gapProbes(gaps, 3));
                pool.addAll(technical(resume, random, 2));
                pool.addAll(experienceProbes(resume, 2));
                pool.addAll(behavioural(random, 2));
            }
            case RESUME_DEEP_DIVE -> {
                pool.addAll(experienceProbes(resume, 3));
                pool.addAll(technical(resume, random, 3));
                pool.addAll(projectProbes(resume, 1));
                pool.addAll(behavioural(random, 1));
            }
            case GENERAL -> {
                pool.addAll(technical(resume, random, 2));
                pool.addAll(experienceProbes(resume, 2));
                pool.addAll(gapProbes(gaps, 1));
                pool.addAll(behavioural(random, 3));
            }
            default -> pool.addAll(behavioural(random, count));
        }

        // Behavioural questions are the safety net: they need no resume, so a
        // candidate whose parse found little still gets a full session.
        if (pool.size() < count) {
            pool.addAll(behavioural(random, count - pool.size() + 3));
        }

        List<GeneratedQuestion> chosen = new ArrayList<>();
        Set<String> seenPrompts = new LinkedHashSet<>();
        for (GeneratedQuestion question : pool) {
            if (chosen.size() == count) {
                break;
            }
            if (seenPrompts.add(question.prompt())) {
                chosen.add(question);
            }
        }
        return List.copyOf(chosen);
    }

    // ------------------------------------------------------------------

    private static GeneratedQuestion opener(ResumeSnapshot resume) {
        String role = mostRecentTitle(resume);
        if (role != null) {
            return new GeneratedQuestion(
                    QuestionKind.MOTIVATION,
                    "Walk me through your background, ending with your time as " + role
                            + ". Keep it to about two minutes.",
                    null,
                    "Almost every interview opens with this, and almost every candidate "
                            + "rambles. It is the one answer worth rehearsing word for word.",
                    List.of("A one-line summary of who you are now",
                            "Two or three steps that got you here, not every step",
                            "Why the most recent role mattered",
                            "A closing sentence pointing at the role you are applying for"));
        }
        return new GeneratedQuestion(
                QuestionKind.MOTIVATION,
                "Tell me about yourself. Keep it to about two minutes.",
                null,
                "The opening question in almost every interview, and the one candidates "
                        + "most often waste.",
                List.of("Who you are now, in one line",
                        "Two or three steps that got you here",
                        "Why you are in this conversation"));
    }

    private static List<GeneratedQuestion> technical(ResumeSnapshot resume, Random random,
                                                     int max) {
        if (resume == null) {
            return List.of();
        }
        List<ResumeSnapshot.SkillView> skills = new ArrayList<>(resume.skills().stream()
                .filter(skill -> "LANGUAGE".equals(skill.category())
                        || "FRAMEWORK".equals(skill.category())
                        || "DATABASE".equals(skill.category())
                        || "CLOUD_DEVOPS".equals(skill.category()))
                .toList());
        if (skills.isEmpty()) {
            skills = new ArrayList<>(resume.skills());
        }
        java.util.Collections.shuffle(skills, random);

        List<GeneratedQuestion> questions = new ArrayList<>();
        String[] templates = {
                "You list %s on your resume. Describe a specific problem you solved with it "
                        + "where the obvious approach did not work. What did you try first, and "
                        + "why was it wrong?",
                "Someone on your team argues that %s is the wrong choice for a new project. "
                        + "Make the case for it, then make the case against it.",
                "What is something about %s that you got wrong when you were learning it, and "
                        + "what made it click?"
        };

        for (int i = 0; i < skills.size() && questions.size() < max; i++) {
            String skill = skills.get(i).name();
            String template = templates[questions.size() % templates.length];
            questions.add(new GeneratedQuestion(
                    QuestionKind.TECHNICAL,
                    template.formatted(skill),
                    skill,
                    "You claimed " + skill + " on your resume, so an interviewer is entitled "
                            + "to test it. A skill you cannot discuss concretely is worse than "
                            + "one you never listed.",
                    List.of("A specific situation, not a general description of " + skill,
                            "The decision you made and the alternative you rejected",
                            "What actually happened as a result",
                            "Honesty about the limits of what you know")));
        }
        return questions;
    }

    private static List<GeneratedQuestion> experienceProbes(ResumeSnapshot resume, int max) {
        if (resume == null) {
            return List.of();
        }
        List<GeneratedQuestion> questions = new ArrayList<>();
        String[] templates = {
                "Tell me about your work %s. What was the hardest technical decision you had "
                        + "to make, and what did you decide?",
                "%s — what would you do differently if you started that work again tomorrow?",
                "Describe something you shipped %s that you were genuinely proud of. What made "
                        + "it good?"
        };

        for (ResumeSnapshot.ExperienceView entry : resume.experience()) {
            if (questions.size() >= max) {
                break;
            }
            String where = describe(entry);
            if (where == null) {
                continue;
            }
            questions.add(new GeneratedQuestion(
                    QuestionKind.EXPERIENCE_PROBE,
                    templates[questions.size() % templates.length].formatted(where),
                    null,
                    "This is on your resume, so it will be asked about. An interviewer reads "
                            + "the entry and asks the first question it invites.",
                    List.of("Context: what the situation actually was",
                            "Your specific contribution, not the team's",
                            "The outcome, with a number if one exists",
                            "What you would change now")));
        }
        return questions;
    }

    private static List<GeneratedQuestion> projectProbes(ResumeSnapshot resume, int max) {
        if (resume == null || !resume.hasSection("PROJECTS")) {
            return List.of();
        }
        return List.of(new GeneratedQuestion(
                QuestionKind.PROJECT_DEEP_DIVE,
                "Pick the project on your resume you are least confident defending, and walk "
                        + "me through how it works end to end.",
                null,
                "Interviewers pick the project you least want to discuss, not the one you "
                        + "rehearsed. Practising the uncomfortable one is the point.",
                List.of("What it does, in one sentence, before any implementation detail",
                        "The main design decision and why",
                        "What is genuinely unfinished or weak about it",
                        "What you learned that transfers to other work")));
    }

    private static List<GeneratedQuestion> gapProbes(List<String> gaps, int max) {
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (String gap : gaps) {
            if (questions.size() >= max) {
                break;
            }
            questions.add(new GeneratedQuestion(
                    QuestionKind.GAP_PROBE,
                    "This role asks for " + gap + ", and it does not appear on your resume. "
                            + "How would you get up to speed, and what experience of yours is "
                            + "closest to it?",
                    gap,
                    "This is the question you are most likely to be asked and least likely to "
                            + "have prepared. A confident, honest answer to a gap does more good "
                            + "than pretending the gap is not there.",
                    List.of("Acknowledge it plainly — do not bluff",
                            "Name the closest thing you have actually done",
                            "A concrete plan, with a timeframe",
                            "Evidence you have picked something up quickly before")));
        }
        return questions;
    }

    /**
     * The behavioural bank.
     *
     * <p>Fixed rather than generated: these questions are near-universal, and
     * inventing variations of "tell me about a conflict" produces worse practice
     * than the standard wording a candidate will actually hear.
     */
    private static List<GeneratedQuestion> behavioural(Random random, int max) {
        List<GeneratedQuestion> bank = new ArrayList<>(List.of(
                behaviouralQuestion(
                        "Tell me about a time you disagreed with a decision on your team. What "
                                + "did you do?",
                        "Disagreement is the fastest way to learn how someone behaves when they "
                                + "are not in charge.",
                        "The disagreement, stated fairly from both sides",
                        "What you actually did, not what you thought",
                        "The outcome, including if you were wrong"),
                behaviouralQuestion(
                        "Describe something you worked on that failed. What went wrong, and what "
                                + "was your part in it?",
                        "Candidates who cannot name a failure read as either inexperienced or "
                                + "evasive. Both cost the offer.",
                        "A real failure, not a disguised strength",
                        "Your own contribution to it, owned plainly",
                        "What specifically changed in how you work"),
                behaviouralQuestion(
                        "Tell me about a time you had to learn something difficult quickly.",
                        "Most early-career hiring is a bet on learning speed rather than on "
                                + "current knowledge.",
                        "What you needed to learn and by when",
                        "The method — how you actually went about it",
                        "Proof it worked"),
                behaviouralQuestion(
                        "Give me an example of feedback that was hard to hear. What did you do "
                                + "with it?",
                        "How someone handles criticism predicts how they will be to work with far "
                                + "better than a list of skills.",
                        "The feedback, quoted honestly",
                        "Your first reaction, including if it was defensive",
                        "The specific change you made"),
                behaviouralQuestion(
                        "Tell me about a time you had to deliver under a deadline you thought was "
                                + "unrealistic.",
                        "This tests judgement under pressure and whether you communicate early or "
                                + "go quiet.",
                        "Why the deadline was unrealistic",
                        "What you cut, and who you told",
                        "What shipped in the end"),
                behaviouralQuestion(
                        "Describe a time you took ownership of something nobody had asked you to "
                                + "do.",
                        "Initiative is claimed on every resume and demonstrable in almost none of "
                                + "them.",
                        "How you noticed the gap",
                        "What you did without being asked",
                        "Whether it actually mattered")));

        java.util.Collections.shuffle(bank, random);
        return bank.subList(0, Math.min(max, bank.size()));
    }

    private static GeneratedQuestion behaviouralQuestion(String prompt, String rationale,
                                                         String... expected) {
        return new GeneratedQuestion(QuestionKind.BEHAVIOURAL, prompt, null, rationale,
                List.of(expected));
    }

    // ------------------------------------------------------------------

    private static String mostRecentTitle(ResumeSnapshot resume) {
        if (resume == null) {
            return null;
        }
        return resume.experience().stream()
                .filter(entry -> entry.jobTitle() != null)
                .map(entry -> entry.company() == null
                        ? entry.jobTitle()
                        : entry.jobTitle() + " at " + entry.company())
                .findFirst()
                .orElse(null);
    }

    private static String describe(ResumeSnapshot.ExperienceView entry) {
        if (entry.jobTitle() != null && entry.company() != null) {
            return "as " + entry.jobTitle() + " at " + entry.company();
        }
        if (entry.company() != null) {
            return "at " + entry.company();
        }
        if (entry.jobTitle() != null) {
            return "as " + entry.jobTitle();
        }
        return null;
    }

    /**
     * One generated question, before it is persisted.
     *
     * @param focusSkill     the skill this probes, or null
     * @param rationale      why the candidate is being asked this
     * @param expectedPoints cues a good answer covers, revealed after answering
     */
    public record GeneratedQuestion(
            QuestionKind kind,
            String prompt,
            String focusSkill,
            String rationale,
            List<String> expectedPoints
    ) {
    }
}
