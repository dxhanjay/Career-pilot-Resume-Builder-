package com.careerpilot.ats.domain;

import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rubric. Every point this product ever takes off a resume is taken off
 * here, by a named rule, with a quote attached.
 *
 * <p>Deterministic and pure — same snapshot in, same assessment out, no clock,
 * no network, no model. ADR-0029 puts the deterministic cascade first for
 * exactly this reason: a score a student can reproduce and argue with is worth
 * more than a slightly better score they cannot.
 *
 * <p>Deductions are expressed on each category's own 0-100 scale.
 * {@link AtsAssessment#of} applies the category weights afterwards, so a rule
 * author never has to reason about the global weighting to know what their rule
 * costs.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class AtsRubric {

    /**
     * Bump this whenever a rule is added, removed, or reweighted.
     *
     * <p>It is stored on every analysis. Without it, a score from before a
     * rubric change and a score from after look like a change in the resume.
     */
    public static final String VERSION = "1.0.0";

    private static final int IDEAL_MIN_WORDS = 250;
    private static final int IDEAL_MAX_WORDS = 900;
    private static final int MAX_BULLET_WORDS = 34;
    private static final int MIN_SKILLS = 6;
    private static final int HEALTHY_SKILLS = 12;
    private static final int KEYWORD_STUFFING_SKILLS = 60;

    private AtsRubric() {
    }

    /**
     * Scores a resume.
     *
     * @param snapshot everything the parser found; never null
     * @return the assessment, always containing at least one finding
     */
    public static AtsAssessment evaluate(ResumeSnapshot snapshot) {
        Map<AtsCategory, Integer> deductions = new EnumMap<>(AtsCategory.class);
        List<RuleFinding> findings = new ArrayList<>();

        evaluateParseability(snapshot, deductions, findings);
        evaluateStructure(snapshot, deductions, findings);
        evaluateContent(snapshot, deductions, findings);
        evaluateSkills(snapshot, deductions, findings);
        evaluateContact(snapshot, deductions, findings);

        return AtsAssessment.of(deductions, findings, VERSION);
    }

    // ------------------------------------------------------------------
    // Parseability — can the machine read it, and in the right order?
    // ------------------------------------------------------------------

    private static void evaluateParseability(ResumeSnapshot snapshot,
                                             Map<AtsCategory, Integer> deductions,
                                             List<RuleFinding> findings) {
        Set<String> warnings = snapshot.parseWarningCodes();

        if (warnings.contains("NO_TEXT_LAYER")) {
            deduct(deductions, AtsCategory.PARSEABILITY, 100);
            findings.add(RuleFinding.problem("NO_TEXT_LAYER", AtsCategory.PARSEABILITY,
                    AtsSeverity.CRITICAL,
                    "No selectable text in this file",
                    "Nothing in this document is machine-readable. It is almost certainly a scan "
                            + "or an image exported as a PDF. Screening software sees an empty "
                            + "document, so none of your experience exists as far as it is "
                            + "concerned.",
                    "Export a PDF directly from Word, Google Docs, or LaTeX rather than "
                            + "scanning or screenshotting. Test it by trying to select the text "
                            + "in a PDF reader.",
                    100));
            return;
        }

        if (warnings.contains("MULTI_COLUMN_LAYOUT")) {
            deduct(deductions, AtsCategory.PARSEABILITY, 40);
            findings.add(RuleFinding.problem("MULTI_COLUMN_LAYOUT", AtsCategory.PARSEABILITY,
                    AtsSeverity.CRITICAL,
                    "Multi-column layout detected",
                    "This resume appears to use side-by-side columns. Most parsers read a page as "
                            + "one stream from top to bottom, so a sidebar gets interleaved into "
                            + "your work history. The result is a job title attached to the wrong "
                            + "employer, or a skills list spliced through a bullet.",
                    "Move to a single-column layout. It is the single highest-impact change "
                            + "available on most student resumes.",
                    snapshot.textOf(0, Math.min(6, snapshot.lines().size() - 1)), 0,
                    Math.min(6, Math.max(0, snapshot.lines().size() - 1)),
                    40));
        } else {
            findings.add(RuleFinding.pass("SINGLE_COLUMN", AtsCategory.PARSEABILITY,
                    "Reads as a single column",
                    "Text came out in a sensible top-to-bottom order, which is what a screener "
                            + "will see."));
        }

        if (warnings.contains("ENCRYPTED_DOCUMENT")) {
            deduct(deductions, AtsCategory.PARSEABILITY, 30);
            findings.add(RuleFinding.problem("ENCRYPTED_DOCUMENT", AtsCategory.PARSEABILITY,
                    AtsSeverity.HIGH,
                    "The document is protected",
                    "This file carries password or permission restrictions. Many applicant "
                            + "tracking systems reject protected files outright rather than "
                            + "attempting to open them.",
                    "Re-export without a password or usage restrictions.",
                    30));
        }

        if (warnings.contains("FALLBACK_PARSER_USED")) {
            deduct(deductions, AtsCategory.PARSEABILITY, 12);
            findings.add(RuleFinding.problem("FALLBACK_PARSER_USED", AtsCategory.PARSEABILITY,
                    AtsSeverity.MEDIUM,
                    "Our primary reader could not open this file",
                    "We fell back to a secondary extractor. A file that is awkward for one reader "
                            + "is usually awkward for others, and different systems will disagree "
                            + "about what your resume says.",
                    "Re-export the document from its original source, or save it as a fresh PDF.",
                    12));
        }

        if (warnings.contains("SPARSE_TEXT")) {
            deduct(deductions, AtsCategory.PARSEABILITY, 35);
            findings.add(RuleFinding.problem("SPARSE_TEXT", AtsCategory.PARSEABILITY,
                    AtsSeverity.HIGH,
                    "Very little readable text",
                    "We found only " + orZero(snapshot.wordCount()) + " words. Content held in "
                            + "text boxes, images, or graphics does not survive parsing, so a "
                            + "visually full page can arrive nearly empty.",
                    "Put every claim in ordinary body text rather than in a graphic or a "
                            + "text box.",
                    35));
        }

        Integer pages = snapshot.pageCount();
        if (pages != null && pages > 2) {
            int lost = Math.min(20, (pages - 2) * 8);
            deduct(deductions, AtsCategory.PARSEABILITY, lost);
            findings.add(RuleFinding.problem("TOO_MANY_PAGES", AtsCategory.PARSEABILITY,
                    AtsSeverity.MEDIUM,
                    pages + " pages is long for this stage",
                    "Recruiters screening early-career applicants spend seconds per resume. Past "
                            + "two pages, the material at the end is rarely reached.",
                    "Cut to one page if you have under three years of experience, two at most.",
                    lost));
        } else if (pages != null) {
            findings.add(RuleFinding.pass("LENGTH_APPROPRIATE", AtsCategory.PARSEABILITY,
                    pages + (pages == 1 ? " page" : " pages"),
                    "A length a recruiter will actually finish."));
        }

        int words = orZero(snapshot.wordCount());
        if (words > 0 && words < IDEAL_MIN_WORDS && !warnings.contains("SPARSE_TEXT")) {
            deduct(deductions, AtsCategory.PARSEABILITY, 15);
            findings.add(RuleFinding.problem("THIN_CONTENT", AtsCategory.PARSEABILITY,
                    AtsSeverity.MEDIUM,
                    "Thin on detail",
                    words + " words is short. There is likely room to describe what you actually "
                            + "did on the projects already listed.",
                    "Aim for roughly 300-700 words. Add outcomes to the entries you already have "
                            + "rather than adding new entries.",
                    15));
        } else if (words > IDEAL_MAX_WORDS) {
            deduct(deductions, AtsCategory.PARSEABILITY, 10);
            findings.add(RuleFinding.problem("VERBOSE", AtsCategory.PARSEABILITY,
                    AtsSeverity.LOW,
                    "Denser than a screener will read",
                    words + " words is a lot to scan. Density buries your strongest evidence "
                            + "among your weakest.",
                    "Cut the oldest and least relevant entries rather than shortening every "
                            + "bullet uniformly.",
                    10));
        }
    }

    // ------------------------------------------------------------------
    // Structure — are the expected sections there and labelled normally?
    // ------------------------------------------------------------------

    private static void evaluateStructure(ResumeSnapshot snapshot,
                                          Map<AtsCategory, Integer> deductions,
                                          List<RuleFinding> findings) {
        checkSection(snapshot, deductions, findings, "EXPERIENCE", "Experience", 30,
                AtsSeverity.CRITICAL,
                "No section that a parser recognises as work experience was found. Many systems "
                        + "index candidates by employer and title; with no experience block, "
                        + "those fields come back empty.",
                "Add a heading spelled exactly \"Experience\", \"Work Experience\", or "
                        + "\"Professional Experience\". If your experience is internships or "
                        + "freelance work, it still belongs under that heading.");

        checkSection(snapshot, deductions, findings, "EDUCATION", "Education", 20,
                AtsSeverity.HIGH,
                "No education section was found. For early-career applicants this is often the "
                        + "field a filter is set on, so an unreadable one can fail a screen "
                        + "outright.",
                "Add a heading spelled \"Education\" with your institution, qualification, and "
                        + "dates.");

        checkSection(snapshot, deductions, findings, "SKILLS", "Skills", 20,
                AtsSeverity.HIGH,
                "No skills section was found. Keyword matching leans heavily on this block, and "
                        + "skills mentioned only inside prose match far less reliably.",
                "Add a \"Skills\" heading listing the technologies you can actually be "
                        + "questioned on.");

        if (snapshot.hasSection("SUMMARY")) {
            findings.add(RuleFinding.pass("HAS_SUMMARY", AtsCategory.STRUCTURE,
                    "Opens with a summary",
                    "A short opening statement gives a human reader somewhere to start."));
        }

        List<ResumeSnapshot.SectionView> weak = snapshot.sections().stream()
                .filter(section -> section.core() && section.confidence() < 70
                        && section.headingText() != null)
                .toList();
        if (!weak.isEmpty()) {
            ResumeSnapshot.SectionView first = weak.get(0);
            deduct(deductions, AtsCategory.STRUCTURE, 10);
            findings.add(RuleFinding.problem("AMBIGUOUS_HEADING", AtsCategory.STRUCTURE,
                    AtsSeverity.MEDIUM,
                    "A section heading is hard to recognise",
                    "We identified \"" + first.headingText() + "\" as a section heading, but only "
                            + "barely. Creative headings such as \"Where I've Been\" read well to "
                            + "a person and match nothing in a parser's vocabulary.",
                    "Use the conventional word for the section. Save the personality for the "
                            + "bullets.",
                    first.headingText(), first.headingLine(), first.headingLine(),
                    10));
        }

        if (!snapshot.experience().isEmpty()) {
            long undated = snapshot.experience().stream()
                    .filter(entry -> entry.startDate() == null && entry.endDate() == null)
                    .count();
            if (undated > 0) {
                int lost = (int) Math.min(20, undated * 8);
                deduct(deductions, AtsCategory.STRUCTURE, lost);
                ResumeSnapshot.ExperienceView entry = snapshot.experience().stream()
                        .filter(e -> e.startDate() == null && e.endDate() == null)
                        .findFirst().orElseThrow();
                findings.add(RuleFinding.problem("UNDATED_EXPERIENCE", AtsCategory.STRUCTURE,
                        AtsSeverity.HIGH,
                        undated + " experience " + (undated == 1 ? "entry has" : "entries have")
                                + " no readable dates",
                        "We could not read a date range for "
                                + describe(entry.jobTitle(), entry.company())
                                + ". Systems that compute years of experience treat an undated "
                                + "role as zero.",
                        "Write dates in a plain format on the same line as the role, for example "
                                + "\"Jun 2024 - Aug 2024\". Avoid dates that live only in a "
                                + "sidebar or a graphic.",
                        evidenceFor(snapshot, entry.lineStart(), entry.lineEnd()),
                        entry.lineStart(), entry.lineEnd(),
                        lost));
            } else {
                findings.add(RuleFinding.pass("DATED_EXPERIENCE", AtsCategory.STRUCTURE,
                        "Every role carries dates",
                        "Date ranges parsed cleanly, so your years of experience compute "
                                + "correctly."));
            }

            findChronologyGap(snapshot).ifPresent(findings::add);
        }
    }

    private static void checkSection(ResumeSnapshot snapshot,
                                     Map<AtsCategory, Integer> deductions,
                                     List<RuleFinding> findings,
                                     String type, String label, int cost,
                                     AtsSeverity severity, String detail, String fix) {
        ResumeSnapshot.SectionView section = snapshot.section(type);
        if (section == null) {
            deduct(deductions, AtsCategory.STRUCTURE, cost);
            findings.add(RuleFinding.problem("MISSING_" + type, AtsCategory.STRUCTURE, severity,
                    "No " + label.toLowerCase(java.util.Locale.ROOT) + " section found",
                    detail, fix, cost));
        } else {
            findings.add(RuleFinding.pass("HAS_" + type, AtsCategory.STRUCTURE,
                    label + " section found",
                    section.headingText() == null
                            ? "Identified from position and content."
                            : "Recognised from the heading \"" + section.headingText() + "\"."));
        }
    }

    /**
     * A gap of more than a year between the end of one role and the start of the
     * next. Reported, never judged — gaps have reasons, and the point is that the
     * reader will notice it, so the candidate should decide what to say first.
     */
    private static java.util.Optional<RuleFinding> findChronologyGap(ResumeSnapshot snapshot) {
        List<ResumeSnapshot.ExperienceView> dated = snapshot.experience().stream()
                .filter(entry -> entry.startDate() != null)
                .sorted(java.util.Comparator.comparing(ResumeSnapshot.ExperienceView::startDate))
                .toList();

        for (int i = 1; i < dated.size(); i++) {
            LocalDate previousEnd = dated.get(i - 1).endDate();
            LocalDate nextStart = dated.get(i).startDate();
            if (previousEnd == null || dated.get(i - 1).current()) {
                continue;
            }
            if (previousEnd.plusMonths(13).isBefore(nextStart)) {
                ResumeSnapshot.ExperienceView after = dated.get(i);
                return java.util.Optional.of(RuleFinding.problem(
                        "TIMELINE_GAP", AtsCategory.STRUCTURE, AtsSeverity.LOW,
                        "A gap in the timeline",
                        "There is more than a year between " + previousEnd + " and " + nextStart
                                + ". A reader will notice it, so it is better to have decided "
                                + "what it says than to be asked cold.",
                        "If the time was spent studying, caring, travelling, or job-hunting, one "
                                + "dated line saying so removes the question entirely.",
                        evidenceFor(snapshot, after.lineStart(), after.lineEnd()),
                        after.lineStart(), after.lineEnd(),
                        0));
            }
        }
        return java.util.Optional.empty();
    }

    // ------------------------------------------------------------------
    // Content — is this evidence, or a job description?
    // ------------------------------------------------------------------

    private static void evaluateContent(ResumeSnapshot snapshot,
                                        Map<AtsCategory, Integer> deductions,
                                        List<RuleFinding> findings) {
        List<Line> bullets = bulletLines(snapshot);

        if (bullets.isEmpty()) {
            deduct(deductions, AtsCategory.CONTENT, 45);
            findings.add(RuleFinding.problem("NO_BULLETS", AtsCategory.CONTENT,
                    AtsSeverity.HIGH,
                    "No bullet points found",
                    "Experience is written as prose or as bare lines. Recruiters scan; paragraphs "
                            + "are the first thing skipped, and a parser has no way to tell where "
                            + "one achievement ends and the next begins.",
                    "Rewrite each role as three to five bullets, one achievement each.",
                    45));
            return;
        }

        long withVerb = bullets.stream().filter(l -> ResumeLanguage.startsWithActionVerb(l.text)).count();
        double verbRatio = (double) withVerb / bullets.size();
        if (verbRatio < 0.6) {
            int lost = (int) Math.round((0.6 - verbRatio) * 60);
            deduct(deductions, AtsCategory.CONTENT, lost);
            Line example = bullets.stream()
                    .filter(l -> !ResumeLanguage.startsWithActionVerb(l.text))
                    .findFirst().orElse(bullets.get(0));
            findings.add(RuleFinding.problem("WEAK_BULLET_OPENERS", AtsCategory.CONTENT,
                    AtsSeverity.HIGH,
                    "Only " + percent(verbRatio) + " of bullets open with an action verb",
                    "A bullet that opens with a verb states what you did. One that opens any "
                            + "other way usually states what existed around you.",
                    "Start each bullet with the strongest true verb for it — built, migrated, "
                            + "reduced, automated, led — and put the object second.",
                    ResumeLanguage.stripBullet(example.text), example.index, example.index,
                    lost));
        } else {
            findings.add(RuleFinding.pass("STRONG_BULLET_OPENERS", AtsCategory.CONTENT,
                    percent(verbRatio) + " of bullets open with an action verb",
                    "The writing reads as things you did rather than things you were near."));
        }

        long quantified = bullets.stream().filter(l -> ResumeLanguage.isQuantified(l.text)).count();
        double quantRatio = (double) quantified / bullets.size();
        if (quantRatio < 0.3) {
            int lost = (int) Math.round((0.3 - quantRatio) * 90);
            deduct(deductions, AtsCategory.CONTENT, lost);
            Line example = bullets.stream()
                    .filter(l -> !ResumeLanguage.isQuantified(l.text))
                    .findFirst().orElse(bullets.get(0));
            findings.add(RuleFinding.problem("UNQUANTIFIED", AtsCategory.CONTENT,
                    AtsSeverity.HIGH,
                    "Only " + percent(quantRatio) + " of bullets contain a number",
                    "Unmeasured claims are indistinguishable from every other applicant's "
                            + "unmeasured claims. A number is the cheapest credibility available.",
                    "Add scale or outcome to your strongest bullets: how many users, how much "
                            + "faster, how many records, how long it took, how many people.",
                    ResumeLanguage.stripBullet(example.text), example.index, example.index,
                    lost));
        } else {
            findings.add(RuleFinding.pass("QUANTIFIED", AtsCategory.CONTENT,
                    percent(quantRatio) + " of bullets are quantified",
                    "Measured claims are what separate a resume from a wish list."));
        }

        List<Line> weakOpeners = bullets.stream()
                .filter(l -> ResumeLanguage.weakOpener(l.text) != null)
                .toList();
        if (!weakOpeners.isEmpty()) {
            int lost = Math.min(20, weakOpeners.size() * 4);
            deduct(deductions, AtsCategory.CONTENT, lost);
            Line example = weakOpeners.get(0);
            findings.add(RuleFinding.problem("PASSIVE_PHRASING", AtsCategory.CONTENT,
                    AtsSeverity.MEDIUM,
                    weakOpeners.size() + " bullet" + (weakOpeners.size() == 1 ? "" : "s")
                            + " describe duties rather than results",
                    "Phrases like \"" + ResumeLanguage.weakOpener(example.text)
                            + "\" describe the job that existed, not what you did with it. They "
                            + "are copied from the job description you were given.",
                    "Replace the opener with the verb for what you actually produced, and add "
                            + "the outcome.",
                    ResumeLanguage.stripBullet(example.text), example.index, example.index,
                    lost));
        }

        List<Line> firstPerson = bullets.stream()
                .filter(l -> ResumeLanguage.firstPersonMarker(l.text) != null)
                .toList();
        if (!firstPerson.isEmpty()) {
            int lost = Math.min(10, firstPerson.size() * 3);
            deduct(deductions, AtsCategory.CONTENT, lost);
            Line example = firstPerson.get(0);
            findings.add(RuleFinding.problem("FIRST_PERSON", AtsCategory.CONTENT,
                    AtsSeverity.LOW,
                    "First-person pronouns in the bullets",
                    "Resumes are conventionally written in an implied first person. \"I built\" "
                            + "reads as an essay; \"Built\" reads as a resume.",
                    "Drop the pronoun and start with the verb.",
                    ResumeLanguage.stripBullet(example.text), example.index, example.index,
                    lost));
        }

        List<Line> overlong = bullets.stream()
                .filter(l -> ResumeLanguage.wordCount(l.text) > MAX_BULLET_WORDS)
                .toList();
        if (!overlong.isEmpty()) {
            int lost = Math.min(12, overlong.size() * 3);
            deduct(deductions, AtsCategory.CONTENT, lost);
            Line example = overlong.get(0);
            findings.add(RuleFinding.problem("OVERLONG_BULLETS", AtsCategory.CONTENT,
                    AtsSeverity.LOW,
                    overlong.size() + " bullet" + (overlong.size() == 1 ? " is" : "s are")
                            + " longer than a scan survives",
                    "A bullet over " + MAX_BULLET_WORDS + " words is a paragraph wearing a dot. "
                            + "The achievement inside it does not get read.",
                    "One achievement per bullet, under about 25 words. Split rather than trim.",
                    ResumeLanguage.stripBullet(example.text), example.index, example.index,
                    lost));
        }
    }

    // ------------------------------------------------------------------
    // Skills — is there enough matchable vocabulary, and is it varied?
    // ------------------------------------------------------------------

    private static void evaluateSkills(ResumeSnapshot snapshot,
                                       Map<AtsCategory, Integer> deductions,
                                       List<RuleFinding> findings) {
        int count = snapshot.skills().size();

        if (count == 0) {
            deduct(deductions, AtsCategory.SKILLS, 100);
            findings.add(RuleFinding.problem("NO_SKILLS_DETECTED", AtsCategory.SKILLS,
                    AtsSeverity.CRITICAL,
                    "No recognisable skills found",
                    "We could not match a single technology, language, or tool. Keyword matching "
                            + "is the first filter most systems apply, and a resume with no "
                            + "matchable vocabulary fails it regardless of what the candidate "
                            + "can do.",
                    "List the concrete technologies you have used by their usual names — the "
                            + "language, the framework, the database, the cloud, the tooling.",
                    100));
            return;
        }

        if (count < MIN_SKILLS) {
            int lost = (MIN_SKILLS - count) * 10;
            deduct(deductions, AtsCategory.SKILLS, lost);
            findings.add(RuleFinding.problem("FEW_SKILLS", AtsCategory.SKILLS,
                    AtsSeverity.MEDIUM,
                    "Only " + count + " skill" + (count == 1 ? "" : "s") + " detected",
                    "A short skills list narrows the set of postings you can match at all. Named "
                            + "tools you have genuinely used are worth listing even when they feel "
                            + "minor.",
                    "Aim for around " + HEALTHY_SKILLS + " concrete, named technologies you could "
                            + "answer a question about.",
                    lost));
        } else if (count > KEYWORD_STUFFING_SKILLS) {
            deduct(deductions, AtsCategory.SKILLS, 20);
            findings.add(RuleFinding.problem("KEYWORD_STUFFING", AtsCategory.SKILLS,
                    AtsSeverity.MEDIUM,
                    count + " skills is more than a list — it is a wall",
                    "Very long skill lists dilute the signal and invite an interviewer to pick "
                            + "the one you know least. A recruiter reads the first line and "
                            + "stops.",
                    "Cut to the technologies you would be comfortable being questioned on, "
                            + "grouped by kind.",
                    20));
        } else {
            findings.add(RuleFinding.pass("SKILLS_PRESENT", AtsCategory.SKILLS,
                    count + " skills detected",
                    "Enough matchable vocabulary for keyword screening to find you."));
        }

        Set<String> categories = new HashSet<>();
        snapshot.skills().forEach(skill -> categories.add(skill.category()));
        boolean hasTechnicalDepth = categories.contains("LANGUAGE") || categories.contains("FRAMEWORK");

        if (!hasTechnicalDepth && count >= MIN_SKILLS) {
            deduct(deductions, AtsCategory.SKILLS, 25);
            findings.add(RuleFinding.problem("NO_TECHNICAL_SKILLS", AtsCategory.SKILLS,
                    AtsSeverity.MEDIUM,
                    "No programming languages or frameworks recognised",
                    "The skills we found are tools and general concepts. For technical roles the "
                            + "language and framework names are what a filter is actually set on.",
                    "Name the languages and frameworks explicitly, even the obvious ones.",
                    25));
        } else if (categories.size() >= 3) {
            findings.add(RuleFinding.pass("VARIED_SKILLS", AtsCategory.SKILLS,
                    "Skills span " + categories.size() + " categories",
                    "Breadth across languages, frameworks, and tooling matches a wider set of "
                            + "postings."));
        }
    }

    // ------------------------------------------------------------------
    // Contact — can someone who wants to reply actually do it?
    // ------------------------------------------------------------------

    private static void evaluateContact(ResumeSnapshot snapshot,
                                        Map<AtsCategory, Integer> deductions,
                                        List<RuleFinding> findings) {
        ResumeSnapshot.ContactView contact =
                snapshot.contact() == null ? ResumeSnapshot.ContactView.none() : snapshot.contact();

        if (contact.email() == null) {
            deduct(deductions, AtsCategory.CONTACT, 50);
            findings.add(RuleFinding.problem("NO_EMAIL", AtsCategory.CONTACT,
                    AtsSeverity.CRITICAL,
                    "No email address found",
                    "We could not extract an email address. If it is in a header, a footer, or a "
                            + "graphic, many parsers will not see it either — and a candidate "
                            + "with no contact field is unreachable no matter how good the rest "
                            + "is.",
                    "Put your email in the body of the first few lines, as plain text.",
                    50));
        } else {
            findings.add(RuleFinding.pass("HAS_EMAIL", AtsCategory.CONTACT,
                    "Email address found",
                    contact.email() + " parsed cleanly from the contact block."));
        }

        if (contact.phone() == null) {
            deduct(deductions, AtsCategory.CONTACT, 20);
            findings.add(RuleFinding.problem("NO_PHONE", AtsCategory.CONTACT,
                    AtsSeverity.MEDIUM,
                    "No phone number found",
                    "Some recruiters call before they email, and some systems require the field.",
                    "Add a phone number in plain text with its country code.",
                    20));
        }

        if (contact.fullName() == null) {
            deduct(deductions, AtsCategory.CONTACT, 20);
            findings.add(RuleFinding.problem("NO_NAME", AtsCategory.CONTACT,
                    AtsSeverity.HIGH,
                    "Could not identify your name",
                    "The name is usually the first strong line of a resume. If it is set as an "
                            + "image, a logo, or a page header, the parsed record has an empty "
                            + "name field.",
                    "Put your name as ordinary text on the first line.",
                    20));
        }

        if (contact.linkedinUrl() == null && contact.githubUrl() == null
                && contact.portfolioUrl() == null) {
            deduct(deductions, AtsCategory.CONTACT, 15);
            findings.add(RuleFinding.problem("NO_PROFILE_LINKS", AtsCategory.CONTACT,
                    AtsSeverity.LOW,
                    "No LinkedIn, GitHub, or portfolio link",
                    "A link is the one place a reader can verify a claim themselves. For "
                            + "technical roles a GitHub profile does more work than another "
                            + "bullet.",
                    "Add the profile links that show your work, written out as full URLs.",
                    15));
        } else {
            findings.add(RuleFinding.pass("HAS_PROFILE_LINKS", AtsCategory.CONTACT,
                    "Profile link found",
                    "A reader can check your work rather than take it on trust."));
        }

        if (contact.location() == null) {
            deduct(deductions, AtsCategory.CONTACT, 10);
            findings.add(RuleFinding.problem("NO_LOCATION", AtsCategory.CONTACT,
                    AtsSeverity.LOW,
                    "No location found",
                    "Location filters are common, and a blank location is often treated as a "
                            + "non-match rather than as unknown.",
                    "Add your city and country. A full street address is not needed and is "
                            + "better left off.",
                    10));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Line(int index, String text) {
    }

    /**
     * Bullet lines from the experience and projects blocks only.
     *
     * <p>Scanning the whole document would count a skills list written with
     * dashes as unquantified bullets, which is both wrong and confusing to read
     * in a report.
     */
    private static List<Line> bulletLines(ResumeSnapshot snapshot) {
        List<Line> bullets = new ArrayList<>();
        List<ResumeSnapshot.SectionView> relevant = snapshot.sections().stream()
                .filter(section -> section.type().equals("EXPERIENCE")
                        || section.type().equals("PROJECTS")
                        || section.type().equals("ACHIEVEMENTS"))
                .toList();

        List<ResumeSnapshot.SectionView> scope = relevant.isEmpty()
                ? snapshot.sections().stream().filter(s -> !s.type().equals("SKILLS")
                        && !s.type().equals("CONTACT")).toList()
                : relevant;

        for (ResumeSnapshot.SectionView section : scope) {
            for (int i = Math.max(0, section.startLine());
                 i <= Math.min(section.endLine(), snapshot.lines().size() - 1); i++) {
                String text = snapshot.lineAt(i);
                if (ResumeLanguage.isBullet(text) && ResumeLanguage.wordCount(text) >= 3) {
                    bullets.add(new Line(i, text));
                }
            }
        }
        return bullets;
    }

    private static String evidenceFor(ResumeSnapshot snapshot, Integer start, Integer end) {
        if (start == null) {
            return null;
        }
        return snapshot.textOf(start, end == null ? start : end);
    }

    private static String describe(String title, String company) {
        if (title != null && company != null) {
            return "\"" + title + "\" at " + company;
        }
        if (title != null) {
            return "\"" + title + "\"";
        }
        if (company != null) {
            return company;
        }
        return "one of your roles";
    }

    private static void deduct(Map<AtsCategory, Integer> deductions, AtsCategory category, int points) {
        deductions.merge(category, points, Integer::sum);
    }

    private static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
