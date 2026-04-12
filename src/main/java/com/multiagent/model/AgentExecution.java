package com.multiagent.model;

import java.time.LocalDateTime;

/**
 * Immutable audit record for a single agent invocation.
 *
 * <ul>
 *   <li>{@code sessionId}  — UUID that groups all executions in one workflow run</li>
 *   <li>{@code agentName}  — e.g. "InventoryAgent", "AnalyticsAgent", "ReportAgent"</li>
 *   <li>{@code iteration}  — supervisor decision counter at the time of this call</li>
 *   <li>{@code input}      — the full prompt text sent to the agent</li>
 *   <li>{@code output}     — the full response text returned by the agent</li>
 *   <li>{@code executedAt} — wall-clock time of the call</li>
 * </ul>
 */
public record AgentExecution(
        int           id,
        String        sessionId,
        String        agentName,
        int           iteration,
        String        input,
        String        output,
        LocalDateTime executedAt
) {
    /** Compact single-line summary for log output. */
    public String summary() {
        int inLen  = input  == null ? 0 : input.length();
        int outLen = output == null ? 0 : output.length();
        return "[%s] session=%s iter=%d in=%d chars out=%d chars at=%s"
                .formatted(agentName, sessionId, iteration, inLen, outLen, executedAt);
    }
}
