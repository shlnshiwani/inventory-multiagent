package com.multiagent.service;

import com.multiagent.db.AgentExecutionRepository;
import com.multiagent.model.AgentExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central service for agent execution tracking and inter-agent output sharing.
 *
 * <h3>Usage pattern</h3>
 * <pre>{@code
 * // Inside any agent's process() method:
 * AgentExecution record = executionService.log(sessionId, "InventoryAgent", iteration, input, output);
 *
 * // Any agent can read what other agents produced in the same session:
 * String sharedContext = executionService.getSharedContext(sessionId);
 * }</pre>
 *
 * <h3>Inter-agent sharing</h3>
 * {@link #getSharedContext(String)} returns all prior agent outputs for a session
 * formatted as readable text. Agents append this to their own prompt so they
 * have full awareness of what every other agent has already done.
 */
@Service
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentExecutionRepository repo;

    public AgentExecutionService(AgentExecutionRepository repo) {
        this.repo = repo;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persist one agent invocation (input + output) and return the saved record.
     * Called by every agent immediately after it finishes processing.
     */
    public AgentExecution log(String sessionId, String agentName,
                              int iteration, String input, String output) {
        AgentExecution saved = repo.save(sessionId, agentName, iteration, input, output);
        log.info("[ExecService] logged {} | session={} | iter={}",
                 agentName, sessionId, iteration);
        return saved;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns all executions for a workflow session, ordered chronologically.
     * Use this for display / audit.
     */
    public List<AgentExecution> getExecutions(String sessionId) {
        return repo.findBySession(sessionId);
    }

    /**
     * Returns executions for a specific agent within a session.
     */
    public List<AgentExecution> getExecutionsByAgent(String sessionId, String agentName) {
        return repo.findBySessionAndAgent(sessionId, agentName);
    }

    /**
     * Returns the most recent output produced by a specific agent in this session,
     * or {@code null} if that agent has not yet run.
     *
     * <p>Use this when an agent needs to read one specific peer's result:
     * <pre>{@code
     * String analyticsResult = executionService.getLatestOutput(sessionId, "AnalyticsAgent");
     * }</pre>
     */
    public String getLatestOutput(String sessionId, String agentName) {
        return repo.findLatestOutput(sessionId, agentName);
    }

    /**
     * Builds a shared-context string from ALL prior agent outputs in this session.
     *
     * <p>Agents include this in their prompt so they can build on each other's work.
     * Returns an empty string if no prior executions exist yet.
     *
     * <p>Format:
     * <pre>
     * === Shared Agent Outputs (session: abc-123) ===
     * [Step 1 | InventoryAgent | iter=1]
     * Found 4 Electronics products: ...
     * ---
     * [Step 2 | AnalyticsAgent | iter=2]
     * Total inventory value: $18,452.00 ...
     * ===
     * </pre>
     */
    public String getSharedContext(String sessionId) {
        List<AgentExecution> executions = repo.findBySession(sessionId);
        if (executions.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== Shared Agent Outputs (session: ").append(sessionId).append(") ===\n");

        for (int i = 0; i < executions.size(); i++) {
            AgentExecution e = executions.get(i);
            sb.append("[Step ").append(i + 1)
              .append(" | ").append(e.agentName())
              .append(" | iter=").append(e.iteration())
              .append("]\n")
              .append(e.output())
              .append("\n---\n");
        }

        sb.append("===");
        return sb.toString();
    }

    /**
     * Formatted execution summary for a session — useful for logging or display.
     */
    public String getSummary(String sessionId) {
        List<AgentExecution> executions = repo.findBySession(sessionId);
        if (executions.isEmpty()) return "No executions recorded for session: " + sessionId;

        return "Session %s — %d execution(s):\n%s".formatted(
                sessionId,
                executions.size(),
                executions.stream()
                          .map(AgentExecution::summary)
                          .collect(Collectors.joining("\n"))
        );
    }
}
