package com.careerpilot.parsing.domain.extract;

import java.time.LocalDate;

/**
 * A date range read from a resume line.
 *
 * <p>{@code precision} exists because "2022" and "March 2022" are not the same
 * claim, and both normalise to a {@link LocalDate}. Without it, a resume saying
 * "2022 – 2026" would be indistinguishable from one saying "January 2022 –
 * January 2026", and a gap analysis would report an eight-month gap that the
 * candidate never had.
 *
 * <p>Precision is not persisted yet — the schema stores {@code DATE} — so a
 * year-only date is stored as 1 January. That is a known fidelity loss, and the
 * reason no feature currently computes month-level gaps.
 *
 * @param start     first day of the start period, or {@code null}
 * @param end       first day of the end period, or {@code null} when current
 * @param current   whether the range runs to "Present"
 * @param single    whether only one date was found, in which case it is in
 *                  {@code end} — a lone year on a resume is nearly always a
 *                  graduation or completion date
 * @param precision how precisely the dates were written
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record DateRange(
        LocalDate start,
        LocalDate end,
        boolean current,
        boolean single,
        DatePrecision precision
) {

    /** How precisely a date was written in the resume. */
    public enum DatePrecision {
        /** Only a year was given. */
        YEAR,
        /** A month and year were given. */
        MONTH
    }

    /**
     * @return {@code true} if no date was found at all
     */
    public boolean isEmpty() {
        return start == null && end == null && !current;
    }

    /** An empty range. */
    public static DateRange none() {
        return new DateRange(null, null, false, false, DatePrecision.YEAR);
    }
}
