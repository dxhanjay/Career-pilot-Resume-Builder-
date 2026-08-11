package com.careerpilot.parsing.domain.extract;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads date ranges out of resume lines.
 *
 * <p>Dates are the anchor for entry detection across every section: a line
 * carrying a range is nearly always the header of a job, a degree, or a
 * certificate. Getting them right therefore matters beyond the date columns
 * themselves.
 *
 * <p>Handles the formats resumes actually use — "Jan 2022 – Present",
 * "2022-2026", "06/2021 to 08/2021", "March 2022 - December 2023" — and
 * deliberately refuses anything else rather than guessing. A misread date
 * produces a wrong employment gap, which is worse than no date at all.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class DateRangeParser {

    private static final Map<String, Integer> MONTHS = buildMonths();

    /** "Jan 2022", "January, 2022", "Sept. 2022". */
    private static final Pattern MONTH_YEAR = Pattern.compile(
            "(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?,?\\s+((?:19|20)\\d{2})\\b");

    /** "06/2021", "6-2021". */
    private static final Pattern NUMERIC_MONTH_YEAR = Pattern.compile(
            "\\b(0?[1-9]|1[0-2])[/.](\\d{4})\\b");

    /** A bare four-digit year. */
    private static final Pattern YEAR = Pattern.compile("\\b((?:19|20)\\d{2})\\b");

    /** Words meaning "still going". */
    private static final Pattern PRESENT = Pattern.compile(
            "(?i)\\b(present|current|currently|now|ongoing|till\\s+date|to\\s+date)\\b");

    private DateRangeParser() {
    }

    /**
     * Parses a date range from a line.
     *
     * <p>Two dates become a range. One date plus a "Present" marker becomes an
     * open range. One date alone goes into {@code end} and is flagged
     * {@link DateRange#single()} — a lone year on a resume is a graduation or
     * completion date far more often than a start date.
     *
     * @param text the line
     * @return what was found, never {@code null}
     */
    public static DateRange parse(String text) {
        if (text == null || text.isBlank()) {
            return DateRange.none();
        }

        List<Token> tokens = findTokens(text);
        boolean present = PRESENT.matcher(text).find();

        if (tokens.isEmpty()) {
            return DateRange.none();
        }

        tokens.sort(java.util.Comparator.comparingInt(Token::position));
        DateRange.DatePrecision precision = tokens.stream()
                .anyMatch(token -> token.precision() == DateRange.DatePrecision.MONTH)
                ? DateRange.DatePrecision.MONTH
                : DateRange.DatePrecision.YEAR;

        if (tokens.size() >= 2) {
            return new DateRange(tokens.get(0).date(), tokens.get(1).date(),
                    false, false, precision);
        }

        LocalDate only = tokens.get(0).date();

        if (present) {
            return new DateRange(only, null, true, false, precision);
        }

        return new DateRange(null, only, false, true, precision);
    }

    /**
     * Whether a line carries a date range at all.
     *
     * <p>Used as an entry-boundary signal, so it must not fire on the ordinary
     * numbers in an achievement bullet — "reduced latency by 40% in 2023" has a
     * year but is not a header. Callers pair this with structural checks.
     *
     * @param text the line
     * @return {@code true} if any date was found
     */
    public static boolean hasDate(String text) {
        return !parse(text).isEmpty();
    }

    /**
     * Removes date text from a line.
     *
     * <p>Entry headers mix the date with the thing being dated — "Software
     * Engineer, Google (2020 – 2022)". Stripping the date leaves the part the
     * other extractors care about.
     *
     * @param text the line
     * @return the line without dates, separators, or empty brackets
     */
    public static String stripDates(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = MONTH_YEAR.matcher(text).replaceAll(" ");
        cleaned = NUMERIC_MONTH_YEAR.matcher(cleaned).replaceAll(" ");
        cleaned = YEAR.matcher(cleaned).replaceAll(" ");
        cleaned = PRESENT.matcher(cleaned).replaceAll(" ");
        // Separators and brackets left stranded by the removals.
        cleaned = cleaned.replaceAll("[\\u2010-\\u2015]", "-");
        cleaned = cleaned.replaceAll("\\(\\s*[-–—to,]*\\s*\\)", " ");
        cleaned = cleaned.replaceAll("\\[\\s*[-–—to,]*\\s*\\]", " ");
        cleaned = cleaned.replaceAll("(?i)\\s+to\\s*$", " ");
        // A run, not a single character: removing "2020 - 2022" from
        // "Google | 2020 - 2022" strands both the pipe and the dash.
        cleaned = cleaned.replaceAll("[\\s\\-–—|,;:]+$", "");
        cleaned = cleaned.replaceAll("^[\\s\\-–—|,;:]+", "");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static List<Token> findTokens(String text) {
        List<Token> tokens = new ArrayList<>();
        boolean[] consumed = new boolean[text.length()];

        Matcher monthYear = MONTH_YEAR.matcher(text);
        while (monthYear.find()) {
            Integer month = MONTHS.get(monthYear.group(1).toLowerCase(Locale.ROOT));
            int year = Integer.parseInt(monthYear.group(2));
            if (month != null) {
                tokens.add(new Token(LocalDate.of(year, month, 1), monthYear.start(),
                        DateRange.DatePrecision.MONTH));
                markConsumed(consumed, monthYear.start(), monthYear.end());
            }
        }

        Matcher numeric = NUMERIC_MONTH_YEAR.matcher(text);
        while (numeric.find()) {
            if (isConsumed(consumed, numeric.start(), numeric.end())) {
                continue;
            }
            int month = Integer.parseInt(numeric.group(1));
            int year = Integer.parseInt(numeric.group(2));
            tokens.add(new Token(LocalDate.of(year, month, 1), numeric.start(),
                    DateRange.DatePrecision.MONTH));
            markConsumed(consumed, numeric.start(), numeric.end());
        }

        Matcher year = YEAR.matcher(text);
        while (year.find()) {
            if (isConsumed(consumed, year.start(), year.end())) {
                continue;
            }
            tokens.add(new Token(LocalDate.of(Integer.parseInt(year.group(1)), 1, 1),
                    year.start(), DateRange.DatePrecision.YEAR));
            markConsumed(consumed, year.start(), year.end());
        }

        return tokens;
    }

    private static void markConsumed(boolean[] consumed, int from, int to) {
        for (int i = from; i < to && i < consumed.length; i++) {
            consumed[i] = true;
        }
    }

    private static boolean isConsumed(boolean[] consumed, int from, int to) {
        for (int i = from; i < to && i < consumed.length; i++) {
            if (consumed[i]) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> buildMonths() {
        Map<String, Integer> months = new java.util.HashMap<>();
        String[] names = {"jan", "feb", "mar", "apr", "may", "jun",
                "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < names.length; i++) {
            months.put(names[i], i + 1);
        }
        months.put("sept", 9);
        return Map.copyOf(months);
    }

    private record Token(LocalDate date, int position, DateRange.DatePrecision precision) {
    }
}
