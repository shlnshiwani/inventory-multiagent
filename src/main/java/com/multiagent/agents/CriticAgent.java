package com.multiagent.agents;

import com.multiagent.model.CriticResult;
import com.multiagent.service.AgentExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Critic Agent — evaluates each specialist agent's output and returns a numeric
 * quality score (1–10) plus a one-sentence feedback string.
 *
 * <h3>Role in the workflow</h3>
 * After every specialist agent (Inventory, Analytics, Report) runs, the graph
 * invokes {@link #evaluate}. If the score is below {@link #PASS_THRESHOLD} AND
 * the agent has not yet used all its allowed attempts, the graph retries the
 * agent with the critic's feedback included in the prompt. Otherwise it proceeds.
 *
 * <h3>Max attempts</h3>
 * The graph allows at most 2 attempts per agent. On attempt 2 the workflow
 * always proceeds regardless of the score, avoiding infinite loops.
 *
 * <h3>Logging</h3>
 * Each evaluation is persisted to {@code agent_executions} under the name
 * {@code "CriticAgent[<evaluated-agent>]"} so it appears in the audit trail.
 */
@Service
public class CriticAgent {

    private static final Logger log = LoggerFactory.getLogger(CriticAgent.class);

    /** Minimum score to pass without retry. */
    public static final int PASS_THRESHOLD = 7;

    private final CriticAssistant       assistant;
    private final AgentExecutionService executionService;

    public CriticAgent(CriticAssistant assistant, AgentExecutionService executionService) {
        this.assistant        = assistant;
        this.executionService = executionService;
    }

    /**
     * Evaluate one specialist agent's output.
     *
     * @param agentName   name of the agent being evaluated (e.g. "InventoryAgent")
     * @param agentOutput the agent's response text
     * @param originalTask the original workflow task (for correctness reference)
     * @param sessionId   workflow session UUID for DB logging
     * @param iteration   current iteration counter for DB logging
     * @return score 1–10 and one-sentence feedback
     */
    public CriticResult evaluate(String agentName, String agentOutput,
                                 String originalTask, String sessionId, int iteration) {
        String prompt = """
                Evaluated agent: %s
                Original task: %s
                Agent response:
                %s
                """.formatted(agentName, originalTask, agentOutput);

        String raw = assistant.evaluate(prompt);
        CriticResult result = parse(raw);

        log.info("[CriticAgent] agent={} score={} feedback='{}'",
                 agentName, result.score(), result.feedback());

        executionService.log(sessionId, "CriticAgent[" + agentName + "]",
                             iteration, prompt, raw);
        return result;
    }

    /** Returns {@code true} when the given score clears the pass threshold. */
    public static boolean passes(int score) {
        return score >= PASS_THRESHOLD;
    }

    // ── Response parser ───────────────────────────────────────────────────────

    private CriticResult parse(String raw) {
        int    score    = 5;              // default mid-range if parsing fails
        String feedback = raw.trim();     // fallback: raw text

        for (String line : raw.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("SCORE:")) {
                try {
                    score = Integer.parseInt(trimmed.substring(6).trim());
                } catch (NumberFormatException ignored) { /* keep default */ }
            } else if (trimmed.startsWith("FEEDBACK:")) {
                feedback = trimmed.substring(9).trim();
            }
        }
        return new CriticResult(score, feedback);
    }
}
