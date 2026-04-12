package com.multiagent.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared state that flows through every node of the LangGraph4j state graph.
 *
 * <p>Channel types:
 * <ul>
 *   <li>{@code Channels.base(supplier)} — last-value semantics (replaces on update)</li>
 *   <li>{@code Channels.appender(listSupplier)} — accumulates entries into a List</li>
 * </ul>
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code sessionId}   — UUID for this workflow run; used to group DB audit rows</li>
 *   <li>{@code task}        — original user task</li>
 *   <li>{@code route}       — supervisor's next routing decision</li>
 *   <li>{@code agentOutput} — last specialist agent response text</li>
 *   <li>{@code history}     — in-memory accumulation of all agent outputs (appender)</li>
 *   <li>{@code iteration}   — how many supervisor decisions have been made</li>
 *   <li>{@code done}        — true when the graph should terminate</li>
 * </ul>
 */
public class WorkflowState extends AgentState {

    // ── Route constants (before SCHEMA to avoid forward-reference) ────────────

    public static final String ROUTE_INVENTORY = "inventory";
    public static final String ROUTE_ANALYTICS = "analytics";
    public static final String ROUTE_REPORT    = "report";
    public static final String ROUTE_END       = "END";

    // ── Channel schema ────────────────────────────────────────────────────────

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        "sessionId",   Channels.base(() -> ""),
        "task",        Channels.base(() -> ""),
        "route",       Channels.base(() -> ROUTE_INVENTORY),
        "agentOutput", Channels.base(() -> ""),
        "history",     Channels.appender(ArrayList::new),
        "iteration",   Channels.base(() -> 0),
        "done",        Channels.base(() -> false)
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

    public String route() {
        return this.<String>value("route").orElse(ROUTE_INVENTORY);
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

    public boolean done() {
        return this.<Boolean>value("done").orElse(false);
    }
}
