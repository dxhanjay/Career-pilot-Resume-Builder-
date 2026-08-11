package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.domain.ParsedEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence access for {@link ParsedEducation}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface ParsedEducationRepository extends JpaRepository<ParsedEducation, UUID> {

    /**
     * Every qualification found in a parse, most recent first.
     *
     * <p>Nulls sort last so that entries with no date — which are usually the
     * least complete — do not lead the list on the parse-review screen.
     *
     * @param parseId the parse
     * @return the qualifications
     */
    List<ParsedEducation> findByParseIdOrderByEndDateDesc(UUID parseId);

    /**
     * Removes every qualification for a parse, so a re-extraction replaces
     * rather than duplicates.
     *
     * @param parseId the parse
     */
    void deleteByParseId(UUID parseId);
}
