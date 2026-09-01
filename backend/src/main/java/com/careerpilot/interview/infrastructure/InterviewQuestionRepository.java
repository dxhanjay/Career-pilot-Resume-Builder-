package com.careerpilot.interview.infrastructure;

import com.careerpilot.interview.domain.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Questions belonging to a session, in the order they are asked.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {

    List<InterviewQuestion> findBySessionIdOrderByPositionAsc(UUID sessionId);

    Optional<InterviewQuestion> findByIdAndUserId(UUID id, UUID userId);
}
