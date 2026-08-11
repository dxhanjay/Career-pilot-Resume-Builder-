package com.careerpilot.parsing.domain.extract;

import com.careerpilot.parsing.domain.section.DocumentLine;
import com.careerpilot.parsing.domain.section.LineModel;
import com.careerpilot.parsing.domain.section.ResumeSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a section into its individual entries.
 *
 * <p>Resumes do not mark where one job ends and the next begins, so this reads
 * the two conventions that every template follows regardless of styling:
 *
 * <ol>
 *   <li>a blank line separates entries</li>
 *   <li>a non-bullet line following bullets starts a new entry — bullets belong
 *       to the thing above them, so the first line that stops being a bullet is
 *       the next thing</li>
 * </ol>
 *
 * <p>A date on the line reinforces a boundary but does not create one on its
 * own. Achievement bullets are full of years ("cut costs 30% in 2023"), and
 * treating every year as a boundary would shatter one job into five.
 *
 * <p>Where a section runs entries together with no blank lines and no bullets
 * at all, this returns one entry covering the section. That under-splits rather
 * than over-splits, deliberately: merging two jobs produces one low-confidence
 * row a user can see is wrong, whereas splitting one job in half produces two
 * plausible rows that are both wrong.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class EntrySplitter {

    private EntrySplitter() {
    }

    /**
     * Splits a section's content into entries.
     *
     * @param model   the document
     * @param section the section to split
     * @return the entries, in order; empty if the section has no content
     */
    public static List<SectionEntry> split(LineModel model, ResumeSection section) {
        if (model == null || section == null || section.isEmpty()) {
            return List.of();
        }

        List<DocumentLine> lines = model.range(section.startLine(), section.endLine());
        if (lines.isEmpty()) {
            return List.of();
        }

        List<SectionEntry> entries = new ArrayList<>();
        List<DocumentLine> current = new ArrayList<>();
        boolean previousWasBullet = false;
        boolean sawBlank = false;

        for (DocumentLine line : lines) {
            if (line.isBlank()) {
                sawBlank = true;
                continue;
            }

            boolean isBullet = line.isBullet();
            boolean startsNewEntry = !current.isEmpty()
                    && !isBullet
                    && (sawBlank || previousWasBullet);

            if (startsNewEntry) {
                entries.add(toEntry(current));
                current = new ArrayList<>();
            }

            current.add(line);
            previousWasBullet = isBullet;
            sawBlank = false;
        }

        if (!current.isEmpty()) {
            entries.add(toEntry(current));
        }

        return List.copyOf(entries);
    }

    private static SectionEntry toEntry(List<DocumentLine> lines) {
        return new SectionEntry(
                lines.get(0).index(),
                lines.get(lines.size() - 1).index(),
                List.copyOf(lines));
    }
}
