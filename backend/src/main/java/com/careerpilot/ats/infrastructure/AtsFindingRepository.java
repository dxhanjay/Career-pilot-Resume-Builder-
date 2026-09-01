package com.careerpilot.ats.infrastructure;

import com.careerpilot.ats.domain.AtsFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Findings belonging to an analysis, in the order the rubric ranked them.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface AtsFindingRepository extends JpaRepository<AtsFinding, UUID> {

    List<AtsFinding> findByAnalysisIdOrderByDisplayOrderAsc(UUID analysisId);
}
