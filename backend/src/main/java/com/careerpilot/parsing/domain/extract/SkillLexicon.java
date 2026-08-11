package com.careerpilot.parsing.domain.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The vocabulary of skills the parser can recognise, and their canonical forms.
 *
 * <h2>Why a lexicon rather than a model</h2>
 *
 * <p>Skill detection looks like a task for a language model and is not. The set
 * of skills that matter is small, slow-moving, and shared across every resume;
 * a lookup table gets it right every time, in microseconds, for nothing. What a
 * lexicon cannot do is recognise a skill nobody has added to it — which is a
 * real limitation, and the reason {@code UNKNOWN}-heavy resumes are a candidate
 * for the Phase 7 repair pass rather than an argument against this approach.
 *
 * <h2>Ambiguous entries</h2>
 *
 * <p>Some skill names are ordinary English words: C, R, Go, Rust, Swift, Scala.
 * Searching a whole resume for "go" finds "go-to-market" and "ongoing"; the
 * boundary rules in {@link #findIn} stop the worst of it, but "Go" in "Go the
 * extra mile" is a genuine collision that no boundary rule can resolve.
 *
 * <p>Those entries are flagged {@link Entry#ambiguous()} and are only matched
 * inside a skills section, where a bare "Go" means the language. The cost is
 * missing a language mentioned only in an experience bullet; the alternative is
 * telling a candidate they know Rust because they wrote "rust-proof". Under
 * FR-JD-03 a wrongly-detected skill hides a real gap, which is the more
 * damaging of the two errors.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class SkillLexicon {

    /**
     * One recognised skill.
     *
     * @param canonical the normalised name matching joins on
     * @param category  what kind of skill it is
     * @param ambiguous whether the name collides with ordinary English, and so
     *                  may only be matched inside a skills section
     * @param aliases   every spelling that resolves to this entry, lowercase
     */
    public record Entry(String canonical, SkillCategory category, boolean ambiguous,
                        Set<String> aliases) {
    }

    /** Where in a line an alias was found. */
    public record Hit(Entry entry, int start, int end) {
    }

    private static final List<Entry> ENTRIES = build();

    private SkillLexicon() {
    }

    /**
     * @return every known skill
     */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * Finds every skill mentioned in a line.
     *
     * <p>Matching is whole-token: an alias must be bounded by something that is
     * not a letter or digit on both sides. That is what stops "java" matching
     * inside "javascript" and "c" matching inside "computer", and it is why
     * aliases containing punctuation — "c++", "node.js", ".net" — work without
     * special cases.
     *
     * @param line              the line to search
     * @param includeAmbiguous  whether ordinary-English skill names may match;
     *                          true only inside a skills section
     * @return the hits, in the order they appear
     */
    public static List<Hit> findIn(String line, boolean includeAmbiguous) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        String haystack = line.toLowerCase(Locale.ROOT);
        List<Hit> hits = new ArrayList<>();

        for (Entry entry : ENTRIES) {
            if (entry.ambiguous() && !includeAmbiguous) {
                continue;
            }
            for (String alias : entry.aliases()) {
                int at = indexOfToken(haystack, alias);
                if (at >= 0) {
                    hits.add(new Hit(entry, at, at + alias.length()));
                    break;
                }
            }
        }

        hits.sort(java.util.Comparator.comparingInt(Hit::start));
        return List.copyOf(hits);
    }

    /**
     * Locates an alias bounded by non-alphanumeric characters.
     *
     * @param haystack lowercased text to search
     * @param alias    lowercase alias to find
     * @return the index of the match, or {@code -1}
     */
    static int indexOfToken(String haystack, String alias) {
        return TextTokens.indexOfToken(haystack, alias);
    }

    private static List<Entry> build() {
        List<Entry> entries = new ArrayList<>();

        // --- Languages -----------------------------------------------------
        lang(entries, "java", "java");
        lang(entries, "python", "python", "python3");
        lang(entries, "javascript", "javascript", "java script", "js", "ecmascript");
        lang(entries, "typescript", "typescript", "ts");
        lang(entries, "c++", "c++", "cpp");
        lang(entries, "c#", "c#", "csharp", "c sharp");
        lang(entries, "php", "php");
        lang(entries, "ruby", "ruby");
        lang(entries, "kotlin", "kotlin");
        lang(entries, "sql", "sql");
        lang(entries, "html", "html", "html5");
        lang(entries, "css", "css", "css3");
        lang(entries, "bash", "bash", "shell scripting");
        lang(entries, "matlab", "matlab");
        lang(entries, "perl", "perl");

        // Ordinary English words as well as languages — skills section only.
        ambiguous(entries, "c", SkillCategory.LANGUAGE, "c");
        ambiguous(entries, "r", SkillCategory.LANGUAGE, "r");
        ambiguous(entries, "go", SkillCategory.LANGUAGE, "go", "golang");
        ambiguous(entries, "rust", SkillCategory.LANGUAGE, "rust");
        ambiguous(entries, "swift", SkillCategory.LANGUAGE, "swift");
        ambiguous(entries, "scala", SkillCategory.LANGUAGE, "scala");
        ambiguous(entries, "dart", SkillCategory.LANGUAGE, "dart");
        ambiguous(entries, "assembly", SkillCategory.LANGUAGE, "assembly");

        // --- Frameworks ----------------------------------------------------
        framework(entries, "spring boot", "spring boot", "springboot");
        framework(entries, "spring", "spring framework", "spring mvc");
        framework(entries, "hibernate", "hibernate");
        framework(entries, "react", "react", "reactjs", "react.js");
        framework(entries, "angular", "angular", "angularjs");
        framework(entries, "vue", "vue", "vuejs", "vue.js");
        framework(entries, "next.js", "next.js", "nextjs");
        // No bare "node" alias: it is ordinary vocabulary in any resume that
        // mentions trees, graphs, or clusters.
        framework(entries, "node.js", "node.js", "nodejs");
        framework(entries, "express", "express", "expressjs", "express.js");
        framework(entries, "django", "django");
        ambiguous(entries, "flask", SkillCategory.FRAMEWORK, "flask");
        framework(entries, "fastapi", "fastapi");
        framework(entries, ".net", ".net", "dotnet", "asp.net");
        framework(entries, "laravel", "laravel");
        framework(entries, "flutter", "flutter");
        framework(entries, "react native", "react native");
        framework(entries, "tailwind css", "tailwind", "tailwind css");
        framework(entries, "bootstrap", "bootstrap");
        framework(entries, "jquery", "jquery");
        framework(entries, "junit", "junit");
        framework(entries, "mockito", "mockito");
        framework(entries, "selenium", "selenium");
        framework(entries, "pytorch", "pytorch", "torch");
        framework(entries, "tensorflow", "tensorflow");
        framework(entries, "scikit-learn", "scikit-learn", "sklearn", "scikit learn");
        framework(entries, "pandas", "pandas");
        framework(entries, "numpy", "numpy");
        framework(entries, "opencv", "opencv");

        // --- Databases -----------------------------------------------------
        database(entries, "postgresql", "postgresql", "postgres", "psql");
        database(entries, "mysql", "mysql");
        database(entries, "mongodb", "mongodb", "mongo");
        database(entries, "redis", "redis");
        database(entries, "sqlite", "sqlite");
        database(entries, "oracle", "oracle db", "oracle database");
        database(entries, "elasticsearch", "elasticsearch", "elastic search");
        database(entries, "cassandra", "cassandra");
        database(entries, "dynamodb", "dynamodb");
        database(entries, "firebase", "firebase");
        database(entries, "sql server", "sql server", "mssql");

        // --- Cloud and DevOps ----------------------------------------------
        cloud(entries, "aws", "aws", "amazon web services");
        cloud(entries, "azure", "azure", "microsoft azure");
        cloud(entries, "gcp", "gcp", "google cloud", "google cloud platform");
        cloud(entries, "docker", "docker");
        cloud(entries, "kubernetes", "kubernetes", "k8s");
        cloud(entries, "jenkins", "jenkins");
        cloud(entries, "terraform", "terraform");
        cloud(entries, "ansible", "ansible");
        cloud(entries, "github actions", "github actions");
        cloud(entries, "ci/cd", "ci/cd", "cicd", "ci cd");
        cloud(entries, "nginx", "nginx");
        cloud(entries, "linux", "linux", "unix");
        cloud(entries, "heroku", "heroku");
        cloud(entries, "vercel", "vercel");
        cloud(entries, "netlify", "netlify");

        // --- Tools ---------------------------------------------------------
        tool(entries, "git", "git");
        tool(entries, "github", "github");
        tool(entries, "gitlab", "gitlab");
        tool(entries, "jira", "jira");
        tool(entries, "postman", "postman");
        tool(entries, "maven", "maven");
        tool(entries, "gradle", "gradle");
        tool(entries, "npm", "npm");
        tool(entries, "webpack", "webpack");
        tool(entries, "figma", "figma");
        tool(entries, "tableau", "tableau");
        tool(entries, "power bi", "power bi", "powerbi");
        // "excel" is a verb before it is a spreadsheet — "excel at delivery".
        ambiguous(entries, "excel", SkillCategory.TOOL, "excel", "ms excel");
        tool(entries, "intellij", "intellij", "intellij idea");
        tool(entries, "vs code", "vs code", "vscode", "visual studio code");

        // --- Concepts ------------------------------------------------------
        // No bare "rest" alias: "the rest of the team" is not a REST API.
        concept(entries, "rest api", "rest api", "rest apis", "restful api", "restful");
        concept(entries, "graphql", "graphql");
        concept(entries, "microservices", "microservices", "microservice");
        concept(entries, "data structures", "data structures", "dsa");
        concept(entries, "algorithms", "algorithms", "algorithm");
        concept(entries, "object oriented programming", "object oriented programming", "oop",
                "object-oriented programming");
        concept(entries, "machine learning", "machine learning", "ml");
        concept(entries, "deep learning", "deep learning");
        concept(entries, "artificial intelligence", "artificial intelligence", "ai");
        concept(entries, "natural language processing", "natural language processing", "nlp");
        concept(entries, "computer vision", "computer vision");
        concept(entries, "data analysis", "data analysis", "data analytics");
        concept(entries, "agile", "agile", "scrum");
        concept(entries, "system design", "system design");
        concept(entries, "operating systems", "operating systems");
        concept(entries, "computer networks", "computer networks", "networking");
        concept(entries, "dbms", "dbms", "database management");
        concept(entries, "unit testing", "unit testing");
        concept(entries, "tdd", "tdd", "test driven development", "test-driven development");
        concept(entries, "oauth", "oauth", "oauth2");
        concept(entries, "jwt", "jwt");
        concept(entries, "web development", "web development");
        concept(entries, "responsive design", "responsive design");

        // --- Soft skills ---------------------------------------------------
        soft(entries, "communication", "communication", "communication skills");
        soft(entries, "teamwork", "teamwork", "team work", "collaboration");
        soft(entries, "leadership", "leadership");
        soft(entries, "problem solving", "problem solving", "problem-solving");
        soft(entries, "time management", "time management");
        soft(entries, "critical thinking", "critical thinking");
        soft(entries, "adaptability", "adaptability");
        soft(entries, "public speaking", "public speaking");

        return List.copyOf(entries);
    }

    private static void lang(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.LANGUAGE, false, Set.of(aliases)));
    }

    /**
     * Registers a skill whose name collides with ordinary English.
     *
     * <p>Only matched inside a skills section. See the class note for why a
     * false positive is worse here than a false negative.
     */
    private static void ambiguous(List<Entry> out, String canonical, SkillCategory category,
                                  String... aliases) {
        out.add(new Entry(canonical, category, true, Set.of(aliases)));
    }

    private static void framework(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.FRAMEWORK, false, Set.of(aliases)));
    }

    private static void database(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.DATABASE, false, Set.of(aliases)));
    }

    private static void cloud(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.CLOUD_DEVOPS, false, Set.of(aliases)));
    }

    private static void tool(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.TOOL, false, Set.of(aliases)));
    }

    private static void concept(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.CONCEPT, false, Set.of(aliases)));
    }

    private static void soft(List<Entry> out, String canonical, String... aliases) {
        out.add(new Entry(canonical, SkillCategory.SOFT_SKILL, false, Set.of(aliases)));
    }

    /** Canonical name to entry, for tests and for job-description matching. */
    public static Map<String, Entry> byCanonical() {
        Map<String, Entry> map = new java.util.LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            map.put(entry.canonical(), entry);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
