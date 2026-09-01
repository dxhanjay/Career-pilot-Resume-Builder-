package com.careerpilot.matching.infrastructure;

import com.careerpilot.matching.domain.JdMatchSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Per-skill verdicts belonging to a match, highest-priority gap first.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface JdMatchSkillRepository extends JpaRepository<JdMatchSkill, UUID> {

    List<JdMatchSkill> findByMatchIdOrderByPriorityDesc(UUID matchId);
}
