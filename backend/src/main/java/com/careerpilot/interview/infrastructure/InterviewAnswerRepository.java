package com.careerpilot.interview.infrastructure;

import com.careerpilot.interview.domain.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * Answers, at most one per question.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, UUID> {

    Optional<InterviewAnswer> findByQuestionId(UUID questionId);

    List<InterviewAnswer> findBySessionId(UUID sessionId);
}
