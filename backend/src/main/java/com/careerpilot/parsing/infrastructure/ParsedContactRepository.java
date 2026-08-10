package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.domain.ParsedContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link ParsedContact}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface ParsedContactRepository extends JpaRepository<ParsedContact, UUID> {

    /**
     * The contact block for a parse.
     *
     * @param parseId the parse
     * @return the contact row, if extraction found one
     */
    Optional<ParsedContact> findByParseId(UUID parseId);

    /**
     * Removes the contact row for a parse.
     *
     * <p>Used when a parse is re-extracted in place, so that a second run does
     * not collide with the one-row-per-parse constraint.
     *
     * @param parseId the parse
     */
    void deleteByParseId(UUID parseId);
}
