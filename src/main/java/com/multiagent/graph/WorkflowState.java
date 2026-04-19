package com.multiagent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared state that flows through every node of the sequential critic workflow.
 *
 * <h3>Channel types</h3>
 * <ul>
 *   <li>{@code Channels.base(supplier)} — last-value semantics (overwrites on update)</li>
 *   <li>{@code Channels.appender(listSupplier)} — accumulates entries into a List</li>
 * </ul>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code sessionId}        — UUID for this workflow run; groups all DB audit rows</li>
 *   <li>{@code task}             — original user task (unchanged throughout the run)</li>
 *   <li>{@code agentOutput}      — latest specialist agent's response (overwritten each node)</li>
 *   <li>{@code history}          — appender list; every agent output entry is appended</li>
 *   <li>{@code iteration}        — incremented by each critic node</li>
 *   <li>{@code inventoryAttempt} — how many times InventoryAgent has been invoked this session</li>
 *   <li>{@code analyticsAttempt} — how many times AnalyticsAgent has been invoked</li>
 *   <li>{@code reportAttempt}    — how many times ReportAgent has been invoked</li>
 *   <li>{@code criticScore}      — last score assigned by the Critic Agent (1–10)</li>
 *   <li>{@code criticFeedback}   — last one-sentence feedback from the Critic Agent</li>
 * </ul>
 */
public class WorkflowState extends AgentState {

    // ── Channel schema ────────────────────────────────────────────────────────
    // Map.ofEntries used because Map.of() supports at most 10 key-value pairs.

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
        Map.entry("sessionId",        Channels.base(() -> "")),
        Map.entry("task",             Channels.base(() -> "")),
        Map.entry("agentOutput",      Channels.base(() -> "")),
        Map.entry("history",          Channels.appender(ArrayList::new)),
        Map.entry("iteration",        Channels.base(() -> 0)),
        Map.entry("inventoryAttempt", Channels.base(() -> 0)),
        Map.entry("analyticsAttempt", Channels.base(() -> 0)),
        Map.entry("reportAttempt",    Channels.base(() -> 0)),
        Map.entry("criticScore",      Channels.base(() -> 0)),
        Map.entry("criticFeedback",   Channels.base(() -> ""))
    );

    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    // ── Typed accessors ───────────────────────────────────────────────────────

    public String sessionId() {
        return this.<String>value("sessionId").orElse("unknown");
    }

    public String task() {
        return this.<String>value("task").orElse("");
    }

    public String agentOutput() {
        return this.<String>value("agentOutput").orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<String> history() {
        return this.<List<String>>value("history").orElse(List.of());
    }

    public int iteration() {
        return this.<Integer>value("iteration").orElse(0);
    }

    public int inventoryAttempt() {
        return this.<Integer>value("inventoryAttempt").orElse(0);
    }

    public int analyticsAttempt() {
        return this.<Integer>value("analyticsAttempt").orElse(0);
    }

    public int reportAttempt() {
        return this.<Integer>value("reportAttempt").orElse(0);
    }

    public int criticScore() {
        return this.<Integer>value("criticScore").orElse(0);
    }

    public String criticFeedback() {
        return this.<String>value("criticFeedback").orElse("");
    }
}
