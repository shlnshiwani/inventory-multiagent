package com.multiagent.repository;

import com.multiagent.entity.AgentExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AgentExecutionEntity}.
 *
 * All query methods are derived from the method name — no SQL or JPQL needed.
 */
@Repository
public interface AgentExecutionJpaRepository
        extends JpaRepository<AgentExecutionEntity, Integer> {

    /** All executions for a workflow session, ordered chronologically. */
    List<AgentExecutionEntity> findBySessionIdOrderByExecutedAtAsc(String sessionId);

    /** Executions for a specific agent within a session, ordered chronologically. */
    List<AgentExecutionEntity> findBySessionIdAndAgentNameOrderByExecutedAtAsc(
            String sessionId, String agentName);

    /** Most recent execution of a specific agent in a session (for inter-agent sharing). */
    Optional<AgentExecutionEntity> findTopBySessionIdAndAgentNameOrderByExecutedAtDesc(
            String sessionId, String agentName);

    /** Most recent 50 executions across all sessions — global audit feed. */
    List<AgentExecutionEntity> findTop50ByOrderByExecutedAtDesc();
}
