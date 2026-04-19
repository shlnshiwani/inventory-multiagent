package com.multiagent.graph;

import com.multiagent.agents.AnalyticsAgent;
import com.multiagent.agents.CriticAgent;
import com.multiagent.agents.InventoryAgent;
import com.multiagent.agents.ReportAgent;
import com.multiagent.model.CriticResult;
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
 * Sequential multi-agent workflow with per-agent Critic gates.
 *
 * <h3>Flow</h3>
 * <pre>
 *  START
 *    → inventory ─────────────────────────────────────────────────────────┐
 *    ← critic-inventory: score≥7 OR attempts=2 → analytics; else retry ──┘
 *    → analytics ─────────────────────────────────────────────────────────┐
 *    ← critic-analytics: score≥7 OR attempts=2 → report;    else retry ──┘
 *    → report ────────────────────────────────────────────────────────────┐
 *    ← critic-report:    score≥7 OR attempts=2 → END;       else retry ──┘
 *    → END
 * </pre>
 *
 * <h3>Critic retry rules</h3>
 * <ul>
 *   <li>Pass threshold: score &ge; {@value CriticAgent#PASS_THRESHOLD} / 10</li>
 *   <li>Max attempts per agent: 2 — after the 2nd attempt the workflow always proceeds</li>
 *   <li>Retry prompt includes the critic's feedback so the agent can self-correct</li>
 * </ul>
 *
 * <h3>Shared context</h3>
 * Each agent's prompt is built from DB-logged outputs of prior agents
 * ({@link AgentExecutionService#getLatestOutput}), keeping the context precise and
 * avoiding noise from critic evaluation rows.
 */
@Component
public class MultiAgentGraph {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentGraph.class);

    /** Maximum number of attempts allowed per agent before the workflow proceeds. */
    private static final int MAX_ATTEMPTS = 2;

    private final InventoryAgent        inventoryAgent;
    private final AnalyticsAgent        analyticsAgent;
    private final ReportAgent           reportAgent;
    private final CriticAgent           criticAgent;
    private final AgentExecutionService executionService;

    private CompiledGraph<WorkflowState> compiled;

    public MultiAgentGraph(InventoryAgent        inventoryAgent,
                           AnalyticsAgent        analyticsAgent,
                           ReportAgent           reportAgent,
                           CriticAgent           criticAgent,
                           AgentExecutionService executionService) {
        this.inventoryAgent   = inventoryAgent;
        this.analyticsAgent   = analyticsAgent;
        this.reportAgent      = reportAgent;
        this.criticAgent      = criticAgent;
        this.executionService = executionService;
    }

    @PostConstruct
    void buildGraph() throws Exception {

        // ── Inventory ─────────────────────────────────────────────────────────

        AsyncNodeAction<WorkflowState> inventoryNode = node_async(state -> {
            String prompt = inventoryPrompt(state);
            String output = inventoryAgent.process(prompt, state.sessionId(), state.iteration());
            log.info("[InventoryNode] attempt={} done.", state.inventoryAttempt() + 1);
            return Map.of(
                "agentOutput", output,
                "history",     "[InventoryAgent attempt " + (state.inventoryAttempt() + 1) + "]:\n" + output
            );
        });

        AsyncNodeAction<WorkflowState> criticInventoryNode = node_async(state -> {
            CriticResult r = criticAgent.evaluate(
                    InventoryAgent.NAME, state.agentOutput(), state.task(),
                    state.sessionId(), state.iteration());
            int newAttempt = state.inventoryAttempt() + 1;
            log.info("[CriticInventory] score={} newAttempt={}", r.score(), newAttempt);
            return Map.of(
                "criticScore",      r.score(),
                "criticFeedback",   r.feedback(),
                "inventoryAttempt", newAttempt,
                "iteration",        state.iteration() + 1
            );
        });

        AsyncEdgeAction<WorkflowState> afterCriticInventory = edge_async(state -> {
            boolean proceed = CriticAgent.passes(state.criticScore())
                           || state.inventoryAttempt() >= MAX_ATTEMPTS;
            log.info("[route] inventory → {} (score={} attempts={})",
                     proceed ? "analytics" : "retry", state.criticScore(), state.inventoryAttempt());
            return proceed ? "analytics" : "inventory";
        });

        // ── Analytics ─────────────────────────────────────────────────────────

        AsyncNodeAction<WorkflowState> analyticsNode = node_async(state -> {
            String prompt = analyticsPrompt(state);
            String output = analyticsAgent.process(prompt, state.sessionId(), state.iteration());
            log.info("[AnalyticsNode] attempt={} done.", state.analyticsAttempt() + 1);
            return Map.of(
                "agentOutput", output,
                "history",     "[AnalyticsAgent attempt " + (state.analyticsAttempt() + 1) + "]:\n" + output
            );
        });

        AsyncNodeAction<WorkflowState> criticAnalyticsNode = node_async(state -> {
            CriticResult r = criticAgent.evaluate(
                    AnalyticsAgent.NAME, state.agentOutput(), state.task(),
                    state.sessionId(), state.iteration());
            int newAttempt = state.analyticsAttempt() + 1;
            log.info("[CriticAnalytics] score={} newAttempt={}", r.score(), newAttempt);
            return Map.of(
                "criticScore",      r.score(),
                "criticFeedback",   r.feedback(),
                "analyticsAttempt", newAttempt,
                "iteration",        state.iteration() + 1
            );
        });

        AsyncEdgeAction<WorkflowState> afterCriticAnalytics = edge_async(state -> {
            boolean proceed = CriticAgent.passes(state.criticScore())
                           || state.analyticsAttempt() >= MAX_ATTEMPTS;
            log.info("[route] analytics → {} (score={} attempts={})",
                     proceed ? "report" : "retry", state.criticScore(), state.analyticsAttempt());
            return proceed ? "report" : "analytics";
        });

        // ── Report ────────────────────────────────────────────────────────────

        AsyncNodeAction<WorkflowState> reportNode = node_async(state -> {
            String prompt = reportPrompt(state);
            String output = reportAgent.process(prompt, state.sessionId(), state.iteration());
            log.info("[ReportNode] attempt={} done.", state.reportAttempt() + 1);
            return Map.of(
                "agentOutput", output,
                "history",     "[ReportAgent attempt " + (state.reportAttempt() + 1) + "]:\n" + output
            );
        });

        AsyncNodeAction<WorkflowState> criticReportNode = node_async(state -> {
            CriticResult r = criticAgent.evaluate(
                    ReportAgent.NAME, state.agentOutput(), state.task(),
                    state.sessionId(), state.iteration());
            int newAttempt = state.reportAttempt() + 1;
            log.info("[CriticReport] score={} newAttempt={}", r.score(), newAttempt);
            return Map.of(
                "criticScore",    r.score(),
                "criticFeedback", r.feedback(),
                "reportAttempt",  newAttempt,
                "iteration",      state.iteration() + 1
            );
        });

        AsyncEdgeAction<WorkflowState> afterCriticReport = edge_async(state -> {
            boolean proceed = CriticAgent.passes(state.criticScore())
                           || state.reportAttempt() >= MAX_ATTEMPTS;
            log.info("[route] report → {} (score={} attempts={})",
                     proceed ? "END" : "retry", state.criticScore(), state.reportAttempt());
            return proceed ? END : "report";
        });

        // ── Assemble graph ────────────────────────────────────────────────────

        this.compiled = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
            .addNode("inventory",        inventoryNode)
            .addNode("critic-inventory", criticInventoryNode)
            .addNode("analytics",        analyticsNode)
            .addNode("critic-analytics", criticAnalyticsNode)
            .addNode("report",           reportNode)
            .addNode("critic-report",    criticReportNode)
            .addEdge(START, "inventory")
            .addEdge("inventory",        "critic-inventory")
            .addConditionalEdges("critic-inventory", afterCriticInventory,
                    Map.of("inventory", "inventory", "analytics", "analytics"))
            .addEdge("analytics",        "critic-analytics")
            .addConditionalEdges("critic-analytics", afterCriticAnalytics,
                    Map.of("analytics", "analytics", "report", "report"))
            .addEdge("report",           "critic-report")
            .addConditionalEdges("critic-report", afterCriticReport,
                    Map.of("report", "report", END, END))
            .compile();

        log.info("MultiAgentGraph (sequential + critic) compiled successfully.");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run the sequential critic workflow for the given task.
     * A fresh UUID is generated per call so all executions are grouped in the DB.
     */
    public WorkflowState run(String task) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        log.info("=== Workflow start | session={} ===", sessionId);

        Optional<WorkflowState> result = compiled.invoke(
                Map.of("task", task, "sessionId", sessionId));

        WorkflowState finalState = result.orElseThrow(
                () -> new RuntimeException("Graph produced no output state"));

        log.info("=== Workflow complete | session={} | totalIterations={} ===",
                 sessionId, finalState.iteration());
        return finalState;
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    /**
     * Prompt for the Inventory Agent.
     * - First attempt: raw task (no prior context).
     * - Retry: task + critic feedback.
     */
    private String inventoryPrompt(WorkflowState state) {
        if (state.inventoryAttempt() == 0) {
            return state.task();
        }
        return """
                Original task:
                %s

                Your previous attempt was scored %d/10. Critic feedback:
                %s

                Please address the feedback and provide an improved response using your tools.
                """.formatted(state.task(), state.criticScore(), state.criticFeedback());
    }

    /**
     * Prompt for the Analytics Agent.
     * - First attempt ({@code analyticsAttempt == 0}): task + latest Inventory output from DB.
     *   (criticFeedback at this point is still from the inventory critic — intentionally ignored)
     * - Retry: task + DB context + analytics critic's feedback.
     */
    private String analyticsPrompt(WorkflowState state) {
        String inventoryOutput = executionService.getLatestOutput(
                state.sessionId(), InventoryAgent.NAME);
        String ctx = inventoryOutput != null
                ? "[InventoryAgent output]:\n" + inventoryOutput
                : "(no inventory data yet)";

        if (state.analyticsAttempt() == 0) {
            return """
                    Original task:
                    %s

                    Data from InventoryAgent:
                    %s

                    Now perform your analytics work using your tools.
                    """.formatted(state.task(), ctx);
        }
        return """
                Original task:
                %s

                Data from InventoryAgent:
                %s

                Your previous analytics attempt was scored %d/10. Critic feedback:
                %s

                Please address the feedback and provide an improved analysis using your tools.
                """.formatted(state.task(), ctx, state.criticScore(), state.criticFeedback());
    }

    /**
     * Prompt for the Report Agent.
     * Built from DB-logged outputs of both prior agents (not the in-memory appender)
     * so only actual specialist outputs are included — critic evaluation rows are excluded.
     * - First attempt: task + structured prior-agent context.
     * - Retry: same + report critic's feedback.
     */
    private String reportPrompt(WorkflowState state) {
        String inventoryOutput = executionService.getLatestOutput(
                state.sessionId(), InventoryAgent.NAME);
        String analyticsOutput = executionService.getLatestOutput(
                state.sessionId(), AnalyticsAgent.NAME);

        StringBuilder ctx = new StringBuilder();
        if (inventoryOutput != null) ctx.append("[InventoryAgent]:\n").append(inventoryOutput).append("\n\n");
        if (analyticsOutput != null) ctx.append("[AnalyticsAgent]:\n").append(analyticsOutput);
        if (ctx.isEmpty())           ctx.append("(no prior agent data)");

        String base = """
                Original task:
                %s

                Data collected by specialist agents:
                %s

                Compose a professional inventory management report:
                  1. Executive Summary
                  2. Key Findings
                  3. Recommendations

                Save it using the saveReport tool and confirm the saved report ID.
                """.formatted(state.task(), ctx);

        if (state.reportAttempt() == 0) return base;

        return base + """

                Your previous report attempt was scored %d/10. Critic feedback:
                %s

                Please revise the report addressing the feedback, then save again.
                """.formatted(state.criticScore(), state.criticFeedback());
    }
}
