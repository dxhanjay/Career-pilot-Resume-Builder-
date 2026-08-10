package com.careerpilot.parsing.domain.section;

import java.util.regex.Pattern;

/**
 * One line of normalised resume text, with its position.
 *
 * <p>The index is the contract between extraction and evidence. Every parsed
 * entity stores the line range it came from, and the "here's what the machine
 * saw" screen highlights that range. The index is only meaningful against the
 * {@link LineModel} that produced it — see that class for why normalisation is
 * versioned.
 *
 * <p>The predicates below are structural, not semantic: they describe the shape
 * of the line, not its meaning. Shape is what distinguishes a heading from body
 * text, and it is the same in every language and every template.
 *
 * @param index zero-based position in the normalised document
 * @param text  the line, with trailing whitespace already stripped
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record DocumentLine(int index, String text) {

    /** Characters templates use to open a bullet. */
    private static final Pattern BULLET_START =
            Pattern.compile("^\\s*[\\-\\u2013\\u2014*\\u2022\\u25AA\\u25CF\\u2023\\u00B7o]\\s+");

    /** Deliberately loose: detecting "there is an address here", not capturing it. */
    private static final Pattern EMAIL_LIKE =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");

    private static final Pattern URL_LIKE =
            Pattern.compile("(?i)(https?://|www\\.|linkedin\\.com|github\\.com)");

    /** Seven or more digits, ignoring separators — a phone number, not a year. */
    private static final Pattern PHONE_LIKE =
            Pattern.compile("(?:\\+?\\d[\\d\\s().-]{7,}\\d)");

    /** Words that may stay lowercase inside a title-cased heading. */
    private static final java.util.Set<String> MINOR_WORDS = java.util.Set.of(
            "a", "an", "and", "as", "at", "but", "by", "for", "in", "of", "on",
            "or", "the", "to", "with", "&");

    /**
     * @return {@code true} if the line has no content
     */
    public boolean isBlank() {
        return text == null || text.isBlank();
    }

    /**
     * @return the line without leading or trailing whitespace
     */
    public String stripped() {
        return text == null ? "" : text.strip();
    }

    /**
     * @return the number of whitespace-separated tokens
     */
    public int wordCount() {
        String stripped = stripped();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    /**
     * @return the number of characters, ignoring surrounding whitespace
     */
    public int length() {
        return stripped().length();
    }

    /**
     * Whether every letter on the line is uppercase.
     *
     * <p>The most reliable single signal for a heading in a resume, and the
     * reason templates use it. Requires at least three letters so that "IT" or a
     * stray initial does not qualify.
     *
     * @return {@code true} if the line is written in capitals
     */
    public boolean isAllCaps() {
        String stripped = stripped();
        int letters = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isLowerCase(c)) {
                    return false;
                }
            }
        }
        return letters >= 3;
    }

    /**
     * Whether the line reads as Title Case.
     *
     * <p>Minor words are allowed to stay lowercase, so "Areas of Expertise"
     * qualifies. Weaker evidence than all-caps, because ordinary sentences also
     * start with a capital — which is why it contributes less to the heading
     * score.
     *
     * @return {@code true} if each significant word starts with a capital
     */
    public boolean isTitleCase() {
        String stripped = stripped();
        if (stripped.isEmpty()) {
            return false;
        }
        String[] words = stripped.split("\\s+");
        boolean sawSignificantWord = false;

        for (String word : words) {
            String letters = word.replaceAll("[^\\p{L}]", "");
            if (letters.isEmpty()) {
                continue;
            }
            if (MINOR_WORDS.contains(letters.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            sawSignificantWord = true;
            if (!Character.isUpperCase(letters.charAt(0))) {
                return false;
            }
        }
        return sawSignificantWord;
    }

    /**
     * Whether the line ends the way prose ends rather than the way a heading does.
     *
     * <p>A trailing colon is excluded deliberately — "Technical Skills:" is a
     * heading, and a common one.
     *
     * @return {@code true} if the line ends in sentence punctuation
     */
    public boolean endsLikeProse() {
        String stripped = stripped();
        if (stripped.isEmpty()) {
            return false;
        }
        char last = stripped.charAt(stripped.length() - 1);
        return last == '.' || last == ',' || last == ';';
    }

    /**
     * @return {@code true} if the line opens with a bullet character
     */
    public boolean isBullet() {
        return text != null && BULLET_START.matcher(text).find();
    }

    /**
     * @return {@code true} if the line appears to contain an email address
     */
    public boolean hasEmail() {
        return text != null && EMAIL_LIKE.matcher(text).find();
    }

    /**
     * @return {@code true} if the line appears to contain a URL or profile link
     */
    public boolean hasUrl() {
        return text != null && URL_LIKE.matcher(text).find();
    }

    /**
     * @return {@code true} if the line appears to contain a phone number
     */
    public boolean hasPhone() {
        return text != null && PHONE_LIKE.matcher(text).find();
    }

    /**
     * Whether the line carries contact details.
     *
     * <p>Used to disqualify heading candidates. "GITHUB.COM/ADITI" is short and
     * fully capitalised and would otherwise score as a heading.
     *
     * @return {@code true} if the line holds an email, phone number, or link
     */
    public boolean hasContactDetails() {
        return hasEmail() || hasUrl() || hasPhone();
    }
}
