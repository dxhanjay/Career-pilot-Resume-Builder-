package com.careerpilot.ats.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The lexical facts the content rules need: what a strong bullet opens with,
 * what a weak one opens with, and what counts as a measurement.
 *
 * <p>Kept in one place because these lists are the part of the rubric most
 * likely to be argued with, and an argument is easier to have with a visible
 * list than with a regex buried in a scoring method.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class ResumeLanguage {

    /**
     * Verbs that describe a thing done, not a thing assigned.
     *
     * <p>Stored as lowercase stems without the -ed/-s ending so that "Built",
     * "Build" and "Builds" all match without three entries each.
     */
    private static final Set<String> ACTION_VERB_STEMS = Set.of(
            "achiev", "acquir", "adapt", "add", "address", "administer", "advis", "advocat",
            "analys", "analyz", "architect", "assembl", "assess", "audit", "authent", "author",
            "automat", "benchmark", "boost", "build", "built", "cach", "captur", "central",
            "chair", "clarifi", "coach", "cod", "collaborat", "collect", "compil", "complet",
            "compos", "comput", "conceiv", "conduct", "configur", "consolidat", "construct",
            "consult", "contribut", "convert", "coordinat", "creat", "cut", "debug", "decreas",
            "defin", "deliver", "demonstrat", "deploy", "design", "detect", "develop", "devis",
            "diagnos", "direct", "document", "doubl", "draft", "drove", "driv", "earn", "edit",
            "eliminat", "enabl", "engineer", "enhanc", "ensur", "establish", "evaluat", "execut",
            "expand", "experiment", "explor", "extend", "facilitat", "fix", "forecast", "formul",
            "found", "generat", "grew", "grow", "guid", "halv", "handl", "headed", "identifi",
            "implement", "improv", "increas", "influenc", "initiat", "innovat", "instal",
            "instrument", "integrat", "introduc", "invent", "investigat", "launch", "led",
            "lead", "leverag", "maintain", "manag", "map", "measur", "mentor", "migrat",
            "minimis", "minimiz", "model", "modernis", "moderniz", "monitor", "negotiat",
            "observ", "obtain", "optimis", "optimiz", "orchestrat", "organis", "organiz",
            "overhaul", "own", "partner", "perform", "pilot", "pioneer", "plan", "predict",
            "prepar", "present", "prioritis", "prioritiz", "process", "produc", "program",
            "project", "promot", "propos", "prototyp", "prov", "publish", "queri", "rais",
            "rank", "rebuilt", "rebuild", "reciev", "recommend", "reconcil", "record",
            "recruit", "redesign", "reduc", "refactor", "reengineer", "regist", "releas",
            "remediat", "remov", "render", "reorganis", "reorganiz", "repair", "replac",
            "report", "represent", "research", "resolv", "restructur", "retriev", "revamp",
            "review", "revis", "rewrote", "rewrit", "sav", "scal", "schedul", "secur",
            "select", "shipp", "ship", "simplifi", "simulat", "solv", "sourc", "spearhead",
            "specifi", "stabilis", "stabiliz", "standardis", "standardiz", "streamlin",
            "strengthen", "structur", "studi", "submit", "supervis", "support", "surveyed",
            "sustain", "synthesis", "synthesiz", "system", "target", "taught", "teach", "test",
            "track", "train", "transform", "translat", "trim", "troubleshoot", "tun", "unifi",
            "upgrad", "validat", "verifi", "won", "win", "wrote", "writ");

    /**
     * Openers that describe a job description rather than a person's work.
     *
     * <p>"Responsible for X" says the task existed. It does not say it was done,
     * done well, or done by the applicant.
     */
    private static final List<String> WEAK_OPENERS = List.of(
            "responsible for", "responsibilities included", "duties included", "duties:",
            "tasked with", "in charge of", "helped with", "helped to", "assisted with",
            "worked on", "worked with", "involved in", "participated in", "part of a team",
            "was responsible", "familiar with", "exposure to", "knowledge of");

    /** Filler that survives from a school essay into a first resume. */
    private static final List<String> FIRST_PERSON = List.of(
            "i ", "i'm", "i've", "my ", "me ", "mine ", "myself");

    /**
     * Anything a reader can check. A number, a percentage, an amount of money, a
     * duration, or a magnitude suffix.
     */
    private static final Pattern QUANTIFIED = Pattern.compile(
            "(?i)(\\d+\\s*%|[$£€₹]\\s*\\d|\\d+\\s*(k|m|bn|b)\\b|\\b\\d[\\d,.]*\\b)");

    /** A bullet marker at the start of a line, in any of the glyphs writers use. */
    private static final Pattern BULLET =
            Pattern.compile("^\\s*[\\-\\u2013\\u2014*\\u2022\\u25AA\\u25CF\\u2023\\u00B7]\\s+");

    private ResumeLanguage() {
    }

    /** Whether a line is written as a bullet. */
    public static boolean isBullet(String line) {
        return line != null && BULLET.matcher(line).find();
    }

    /** The line with any leading bullet glyph and whitespace removed. */
    public static String stripBullet(String line) {
        if (line == null) {
            return "";
        }
        return BULLET.matcher(line).replaceFirst("").strip();
    }

    /** Whether the first word is a verb describing something done. */
    public static boolean startsWithActionVerb(String line) {
        String body = stripBullet(line).toLowerCase(Locale.ROOT);
        if (body.isEmpty()) {
            return false;
        }
        String first = body.split("[^\\p{L}]+", 2)[0];
        if (first.length() < 3) {
            return false;
        }
        return ACTION_VERB_STEMS.stream().anyMatch(first::startsWith);
    }

    /** Whether the line contains something a reader could verify. */
    public static boolean isQuantified(String line) {
        return line != null && QUANTIFIED.matcher(line).find();
    }

    /**
     * The weak opener this line begins with, or null.
     *
     * @return the matched phrase, so the finding can quote the exact wording back
     */
    public static String weakOpener(String line) {
        String body = stripBullet(line).toLowerCase(Locale.ROOT);
        return WEAK_OPENERS.stream().filter(body::startsWith).findFirst().orElse(null);
    }

    /** The first-person marker this line uses, or null. */
    public static String firstPersonMarker(String line) {
        String body = (" " + stripBullet(line).toLowerCase(Locale.ROOT) + " ");
        return FIRST_PERSON.stream()
                .filter(marker -> body.contains(" " + marker.strip() + " ")
                        || body.startsWith(" " + marker))
                .findFirst()
                .orElse(null);
    }

    /** Word count of a line, ignoring the bullet glyph. */
    public static int wordCount(String line) {
        String body = stripBullet(line);
        return body.isBlank() ? 0 : body.split("\\s+").length;
    }

    /** Exposed so the rubric's explanatory text and its tests share one list. */
    public static List<String> weakOpeners() {
        return WEAK_OPENERS;
    }
}
