package com.multiagent.runner;

import com.multiagent.db.InventoryRepository;
import com.multiagent.graph.MultiAgentGraph;
import com.multiagent.graph.WorkflowState;
import com.multiagent.model.AgentExecution;
import com.multiagent.model.Report;
import com.multiagent.service.AgentExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the multi-agent demo workflow on startup and prints the full
 * execution log (per-agent input/output) from the H2 audit table.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final MultiAgentGraph       graph;
    private final InventoryRepository   inventoryRepo;
    private final AgentExecutionService executionService;

    public DemoRunner(MultiAgentGraph       graph,
                      InventoryRepository   inventoryRepo,
                      AgentExecutionService executionService) {
        this.graph            = graph;
        this.inventoryRepo    = inventoryRepo;
        this.executionService = executionService;
    }

    @Override
    public void run(String... args) throws Exception {

        printBanner();

        String task = """
            Perform a complete inventory health check:
            1. Search the inventory for 'Electronics' products.
            2. Identify any products with stock quantity at or below 5 (critical low-stock).
            3. Add a new product: name='Mechanical Keyboard', category='Electronics',
               price=129.99, quantity=25.
            4. Calculate the total inventory value and produce a category breakdown.
            5. Write and save a comprehensive inventory health report with findings
               and restocking recommendations.
            """;

        System.out.println("\n" + "=".repeat(72));
        System.out.println("TASK:\n" + task);
        System.out.println("=".repeat(72));

        WorkflowState finalState = graph.run(task);
        String sessionId = finalState.sessionId();

        // ── Agent execution log from DB ───────────────────────────────────────
        System.out.println("\n" + "=".repeat(72));
        System.out.println("AGENT EXECUTION LOG  (session: " + sessionId + ")");
        System.out.println("=".repeat(72));

        List<AgentExecution> executions = executionService.getExecutions(sessionId);
        for (AgentExecution e : executions) {
            System.out.println("\n┌─ [" + e.agentName() + "]"
                             + "  id=" + e.id()
                             + "  iter=" + e.iteration()
                             + "  at=" + e.executedAt());
            System.out.println("│ INPUT  (" + e.input().length() + " chars):");
            System.out.println(indent(truncate(e.input(), 400), "│   "));
            System.out.println("│ OUTPUT (" + e.output().length() + " chars):");
            System.out.println(indent(truncate(e.output(), 600), "│   "));
            System.out.println("└" + "─".repeat(70));
        }

        // ── Execution summary ─────────────────────────────────────────────────
        System.out.println("\n" + executionService.getSummary(sessionId));

        // ── Saved reports ─────────────────────────────────────────────────────
        System.out.println("\n" + "=".repeat(72));
        System.out.println("REPORTS SAVED TO H2:");
        List<Report> reports = inventoryRepo.getAllReports();
        if (reports.isEmpty()) {
            System.out.println("  (none)");
        } else {
            reports.forEach(r -> System.out.printf(
                    "  [%d] '%s'  (created: %s)%n", r.id(), r.title(), r.createdAt()));
        }

        System.out.println("\n" + "=".repeat(72));
        System.out.println("H2 console  → http://localhost:8080/h2-console");
        System.out.println("JDBC URL    → jdbc:h2:mem:inventorydb");
        System.out.println("Query tip   → SELECT agent_name, iteration, LENGTH(output) output_len, executed_at");
        System.out.println("              FROM agent_executions WHERE session_id='" + sessionId + "'");
        System.out.println("              ORDER BY executed_at;");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n… [truncated]";
    }

    private static String indent(String s, String prefix) {
        return s.lines().map(l -> prefix + l).reduce("", (a, b) -> a + "\n" + b);
    }

    private static void printBanner() {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════════════════╗
            ║         Inventory Multi-Agent System  —  v1.0.0                     ║
            ║  Spring Boot 3.5  ·  LangChain4j 1.13  ·  LangGraph4j 1.8.11      ║
            ║  Gemini 2.0 Flash  ·  H2  ·  Resilience4j  ·  Java 21  ·  Maven   ║
            ║  Agents: Inventory · Analytics · Report  ·  Tools: 7               ║
            ║  Execution tracking: agent_executions table (input/output per call) ║
            ╚══════════════════════════════════════════════════════════════════════╝
            """);
    }
}
