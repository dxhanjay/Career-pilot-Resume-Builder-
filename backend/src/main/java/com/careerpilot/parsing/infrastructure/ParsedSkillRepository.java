package com.careerpilot.parsing.infrastructure;

import com.careerpilot.parsing.domain.ParsedSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Persistence access for {@link ParsedSkill}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface ParsedSkillRepository extends JpaRepository<ParsedSkill, UUID> {

    /**
     * Every skill found in a parse, strongest evidence first.
     *
     * @param parseId the parse
     * @return the skills
     */
    List<ParsedSkill> findByParseIdOrderByConfidenceDesc(UUID parseId);

    /**
     * The canonical skill names found in a parse.
     *
     * <p>The read side of {@code FR-JD-03}: job matching needs the set of names
     * to subtract a job description's requirements from, and loading whole
     * entities to call one getter on each wastes the index-only scan this can
     * otherwise be.
     *
     * @param parseId the parse
     * @return canonical skill names
     */
    @Query("SELECT s.normalizedName FROM ParsedSkill s WHERE s.parseId = :parseId")
    List<String> findNormalizedNamesByParseId(@Param("parseId") UUID parseId);

    /**
     * Removes every skill for a parse.
     *
     * <p>Used when a parse is re-extracted in place, so a second run replaces
     * the previous skill set rather than duplicating it.
     *
     * @param parseId the parse
     */
    void deleteByParseId(UUID parseId);
}
