package com.careerpilot.parsing.domain.extract;

import java.util.Locale;

/**
 * Whole-token search shared by the lexicon-based extractors.
 *
 * <p>Every extractor here asks the same question — "does this text contain this
 * term as a term, rather than as a fragment of a longer word?" — and every one
 * of them gets it wrong in the same way if it uses plain {@code contains}:
 * "java" matches inside "javascript", "be" inside "before", "lead" inside
 * "leader".
 *
 * <p>A token boundary is anything that is not a letter or a digit, which is
 * what lets terms containing punctuation work without special cases: "b.tech",
 * "c++", "node.js", and "ci/cd" are all searched the same way.
 *
 * <p>Public because job matching asks the same question of a job posting that
 * the extractors ask of a resume, and a second implementation of token
 * boundaries is a second set of "java matched inside javascript" bugs.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class TextTokens {

    private TextTokens() {
    }

    /**
     * Locates a term bounded by non-alphanumeric characters.
     *
     * @param haystack lowercased text to search
     * @param term     lowercase term to find
     * @return the index of the match, or {@code -1}
     */
    public static int indexOfToken(String haystack, String term) {
        if (haystack == null || term == null || term.isEmpty()) {
            return -1;
        }
        int from = 0;
        while (from <= haystack.length() - term.length()) {
            int at = haystack.indexOf(term, from);
            if (at < 0) {
                return -1;
            }
            boolean cleanStart = at == 0 || !isWordChar(haystack.charAt(at - 1));
            int after = at + term.length();
            boolean cleanEnd = after == haystack.length() || !isWordChar(haystack.charAt(after));
            if (cleanStart && cleanEnd) {
                return at;
            }
            from = at + 1;
        }
        return -1;
    }

    /**
     * @param text text to search, any case
     * @param term lowercase term
     * @return {@code true} if the term appears as a whole token
     */
    public static boolean containsToken(String text, String term) {
        return text != null && indexOfToken(text.toLowerCase(Locale.ROOT), term) >= 0;
    }

    /**
     * @param text  text to search, any case
     * @param terms lowercase terms
     * @return the first term that appears as a whole token, or {@code null}
     */
    static String firstToken(String text, Iterable<String> terms) {
        if (text == null) {
            return null;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        String best = null;
        int bestAt = Integer.MAX_VALUE;
        for (String term : terms) {
            int at = indexOfToken(haystack, term);
            if (at >= 0 && at < bestAt) {
                bestAt = at;
                best = term;
            }
        }
        return best;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }
}
