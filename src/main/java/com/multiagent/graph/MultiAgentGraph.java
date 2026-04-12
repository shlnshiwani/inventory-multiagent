package com.multiagent.graph;

import com.multiagent.agents.AnalyticsAgent;
import com.multiagent.agents.InventoryAgent;
import com.multiagent.agents.ReportAgent;
import com.multiagent.service.AgentExecutionService;
import jakarta.annotation.PostConstruct;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Builds and compiles the LangGraph4j 1.8.11 state graph.
 *
 * <h3>Execution + sharing flow per node</h3>
 * <pre>
 *  1. Supervisor decides next agent (route stored in WorkflowState)
 *  2. Agent node builds its prompt (task + in-memory history from state)
 *  3. Agent calls Gemini, saves input+output to agent_executions table
 *  4. Report agent reads all prior outputs from DB via AgentExecutionService
 *     (DB-backed sharing rather than just in-memory state)
 *  5. Output appended to WorkflowState.history (Channels.appender)
 *  6. Back to supervisor
 * </pre>
 *
 * <pre>
 *  START → supervisor ──[inventory]→ InventoryAgent ──┐
 *               │                                      ├→ supervisor → …
 *               │──[analytics]→ AnalyticsAgent ────────┤
 *               │──[report]───→ ReportAgent ────────────┘
 *               └──[END]──────────────────────────────→ END
 * </pre>
 */
@Component
public class MultiAgentGraph {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentGraph.class);

    private final SupervisorNode        supervisorNode;
    private final InventoryAgent        inventoryAgent;
    private final AnalyticsAgent        analyticsAgent;
    private final ReportAgent           reportAgent;
    private final AgentExecutionService executionService;

    private CompiledGraph<WorkflowState> compiled;

    public MultiAgentGraph(SupervisorNode        supervisorNode,
                           InventoryAgent        inventoryAgent,
                           AnalyticsAgent        analyticsAgent,
                           ReportAgent           reportAgent,
                           AgentExecutionService executionService) {
        this.supervisorNode   = supervisorNode;
        this.inventoryAgent   = inventoryAgent;
        this.analyticsAgent   = analyticsAgent;
        this.reportAgent      = reportAgent;
        this.executionService = executionService;
    }

    @PostConstruct
    void buildGraph() throws Exception {

        // ── Specialist agent nodes ────────────────────────────────────────────

        AsyncNodeAction<WorkflowState> inventoryNode = node_async(state -> {
            String prompt = contextPrompt(state);
            String output = inventoryAgent.process(prompt, state.sessionId(), state.iteration());
            log.info("[InventoryNode] done.");
            return Map.of("agentOutput", output,
                          "history",     "[InventoryAgent]:\n" + output);
        });

        AsyncNodeAction<WorkflowState> analyticsNode = node_async(state -> {
            String prompt = contextPrompt(state);
            String output = analyticsAgent.process(prompt, state.sessionId(), state.iteration());
            log.info("[AnalyticsNode] done.");
            return Map.of("agentOutput", output,
                          "history",     "[AnalyticsAgent]:\n" + output);
        });

        AsyncNodeAction<WorkflowState> reportNode = node_async(state -> {
            // Report agent gets its prompt enriched with DB-backed shared context
            // so it synthesises ALL prior agent outputs, not just in-memory history
            String prompt = reportPrompt(state);
            String output = reportAgent.process(prompt, state.sessionId(), state.iteration());
            log.info("[ReportNode] done.");
            return Map.of("agentOutput", output,
                          "history",     "[ReportAgent]:\n" + output);
        });

        AsyncNodeAction<WorkflowState> supervisorAsync = node_async(supervisorNode);

        AsyncEdgeAction<WorkflowState> routingEdge =
                edge_async(state -> state.route());

        Map<String, String> routingMap = Map.of(
            WorkflowState.ROUTE_INVENTORY, "inventory",
            WorkflowState.ROUTE_ANALYTICS, "analytics",
            WorkflowState.ROUTE_REPORT,    "report",
            WorkflowState.ROUTE_END,       END
        );

        this.compiled = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
            .addNode("supervisor", supervisorAsync)
            .addNode("inventory",  inventoryNode)
            .addNode("analytics",  analyticsNode)
            .addNode("report",     reportNode)
            .addEdge(START, "supervisor")
            .addConditionalEdges("supervisor", routingEdge, routingMap)
            .addEdge("inventory", "supervisor")
            .addEdge("analytics", "supervisor")
            .addEdge("report",    "supervisor")
            .compile();

        log.info("MultiAgentGraph compiled successfully.");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run the workflow. A fresh UUID is generated for every call so executions
     * can be grouped and queried by session in the {@code agent_executions} table.
     */
    public WorkflowState run(String task) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        log.info("=== Workflow start | session={} ===", sessionId);

        Optional<WorkflowState> result = compiled.invoke(
                Map.of("task", task, "sessionId", sessionId));

        WorkflowState finalState = result.orElseThrow(
                () -> new RuntimeException("Graph produced no output state"));

        log.info("=== Workflow complete | session={} | iterations={} ===",
                 sessionId, finalState.iteration());
        return finalState;
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    /**
     * Prompt for Inventory and Analytics agents.
     * Uses in-memory {@code WorkflowState.history} for context.
     */
    private String contextPrompt(WorkflowState state) {
        List<String> history = state.history();
        if (history.isEmpty()) return state.task();
        return """
            Original task:
            %s

            Context from previous agents (in-memory):
            %s

            Perform your part of the work now using your tools.
            """.formatted(state.task(), String.join("\n\n", history));
    }

    /**
     * Prompt for the Report agent.
     * Enriched with DB-backed shared context from {@link AgentExecutionService}
     * so the report includes every agent's actual logged output, not just in-memory state.
     */
    private String reportPrompt(WorkflowState state) {
        // DB-backed: reads all prior executions logged to agent_executions table
        String dbContext = executionService.getSharedContext(state.sessionId());

        // In-memory fallback if DB context is empty
        List<String> history = state.history();
        String memoryContext = history.isEmpty()
                ? "(no prior analysis)"
                : String.join("\n\n", history);

        String sharedData = dbContext.isBlank() ? memoryContext : dbContext;

        return """
            Original task:
            %s

            Data collected by previous agents (sourced from DB audit log):
            %s

            Compose a professional inventory management report:
              1. Executive Summary
              2. Key Findings
              3. Recommendations

            Save it using the saveReport tool and confirm the saved ID.
            """.formatted(state.task(), sharedData);
    }
}
