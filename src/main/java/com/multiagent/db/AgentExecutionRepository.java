package com.multiagent.db;

import com.multiagent.entity.AgentExecutionEntity;
import com.multiagent.model.AgentExecution;
import com.multiagent.repository.AgentExecutionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Facade repository that translates between {@link AgentExecutionEntity} (JPA)
 * and the lightweight {@link AgentExecution} domain record.
 *
 * {@link com.multiagent.service.AgentExecutionService} depends only on this class.
 * The underlying {@link AgentExecutionJpaRepository} is an implementation detail.
 */
@Repository
public class AgentExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionRepository.class);

    private final AgentExecutionJpaRepository jpaRepo;

    public AgentExecutionRepository(AgentExecutionJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    public AgentExecution save(String sessionId, String agentName,
                               int iteration, String input, String output) {
        AgentExecutionEntity saved = jpaRepo.save(
                new AgentExecutionEntity(sessionId, agentName, iteration, input, output));
        log.debug("[ExecRepo] saved id={} agent={} session={}",
                  saved.getId(), agentName, sessionId);
        return toRecord(saved);
    }

    public List<AgentExecution> findBySession(String sessionId) {
        return jpaRepo.findBySessionIdOrderByExecutedAtAsc(sessionId)
                .stream().map(this::toRecord).toList();
    }

    public List<AgentExecution> findBySessionAndAgent(String sessionId, String agentName) {
        return jpaRepo.findBySessionIdAndAgentNameOrderByExecutedAtAsc(sessionId, agentName)
                .stream().map(this::toRecord).toList();
    }

    public String findLatestOutput(String sessionId, String agentName) {
        return jpaRepo.findTopBySessionIdAndAgentNameOrderByExecutedAtDesc(sessionId, agentName)
                .map(AgentExecutionEntity::getOutput)
                .orElse(null);
    }

    public List<AgentExecution> findAll(int limit) {
        return jpaRepo.findTop50ByOrderByExecutedAtDesc()
                .stream().limit(limit).map(this::toRecord).toList();
    }

    // ── Entity → Record ───────────────────────────────────────────────────────

    private AgentExecution toRecord(AgentExecutionEntity e) {
        return new AgentExecution(
                e.getId(), e.getSessionId(), e.getAgentName(),
                e.getIteration(), e.getInput(), e.getOutput(), e.getExecutedAt());
    }
}
