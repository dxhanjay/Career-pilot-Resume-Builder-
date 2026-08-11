package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.DocumentLine;

import java.util.List;

/**
 * One entry within a section — a single job, degree, project, or certificate.
 *
 * <p>A section is not one thing. "WORK EXPERIENCE" holds several jobs, and
 * extracting a company name from the section as a whole would blend three
 * employers into one row. Splitting into entries first is what makes the
 * per-entry extractors possible.
 *
 * @param startLine first line, inclusive
 * @param endLine   last line, inclusive
 * @param lines     the lines themselves
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record SectionEntry(int startLine, int endLine, List<DocumentLine> lines) {

    /**
     * The lines that are not bullets — the entry's header.
     *
     * <p>A job's company and title live here; its achievements live in the
     * bullets. Separating them is what stops "Reduced latency by 40%" being read
     * as an employer name.
     *
     * @return non-bullet, non-blank lines
     */
    public List<DocumentLine> headerLines() {
        return lines.stream()
                .filter(line -> !line.isBlank() && !line.isBullet())
                .toList();
    }

    /**
     * @return the bullet lines
     */
    public List<DocumentLine> bulletLines() {
        return lines.stream().filter(DocumentLine::isBullet).toList();
    }

    /**
     * The entry's prose, newline-joined.
     *
     * @return bullets and any trailing prose, or {@code null} if there is none
     */
    public String description() {
        List<DocumentLine> bullets = bulletLines();
        if (bullets.isEmpty()) {
            return null;
        }
        return String.join("\n", bullets.stream().map(DocumentLine::stripped).toList());
    }

    /**
     * @return {@code true} if the entry has no content
     */
    public boolean isEmpty() {
        return lines.stream().allMatch(DocumentLine::isBlank);
    }
}
