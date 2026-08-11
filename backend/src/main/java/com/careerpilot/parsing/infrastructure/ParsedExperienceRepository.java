package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.domain.ParsedExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence access for {@link ParsedExperience}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface ParsedExperienceRepository extends JpaRepository<ParsedExperience, UUID> {

    /**
     * Every role found in a parse, most recent first.
     *
     * @param parseId the parse
     * @return the roles
     */
    List<ParsedExperience> findByParseIdOrderByStartDateDesc(UUID parseId);

    /**
     * Removes every role for a parse, so a re-extraction replaces rather than
     * duplicates.
     *
     * @param parseId the parse
     */
    void deleteByParseId(UUID parseId);
}
