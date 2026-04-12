package com.multiagent.graph;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Supervisor Node — uses the Gemini LLM to decide which specialist agent
 * to invoke next, based on the original task and work completed so far.
 *
 * <p>Routing keys (returned by the LLM, mapped to graph nodes):
 * <ul>
 *   <li>{@code inventory} — product CRUD, low-stock checks</li>
 *   <li>{@code analytics} — value calculations, category breakdowns</li>
 *   <li>{@code report}    — report writing and persistence</li>
 *   <li>{@code END}       — all work complete, terminate the graph</li>
 * </ul>
 *
 * <p>The {@code apply()} method is annotated with Resilience4j
 * {@code @CircuitBreaker} + {@code @Retry} which are applied by Spring AOP
 * when the bean is called through the Spring proxy.
 */
@Component
public class SupervisorNode implements NodeAction<WorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(SupervisorNode.class);
    private static final int MAX_ITERATIONS = 8;

    private static final PromptTemplate ROUTER_TEMPLATE = PromptTemplate.from("""
        You are a supervisor orchestrating a multi-agent inventory management system.

        Available specialist agents:
          inventory — add products, search catalog, identify low-stock items
          analytics — calculate total inventory value, produce per-category breakdowns
          report    — compose and save professional inventory analysis reports

        Original task:
        {{task}}

        Work completed so far (most recent last):
        {{history}}

        What should happen NEXT to make progress on the task?
        Reply with EXACTLY one word (no punctuation, no spaces, no explanation):
          inventory | analytics | report | END

        Use END only when ALL parts of the original task are genuinely complete.
        """);

    private final ChatModel llm;

    public SupervisorNode(ChatModel llm) {
        this.llm = llm;
    }

    /**
     * Resilience4j annotations work because SupervisorNode is a Spring @Component
     * and this method is invoked via the Spring CGLIB proxy in MultiAgentGraph.
     */
    @Override
    @CircuitBreaker(name = "gemini", fallbackMethod = "fallbackDecision")
    @Retry(name = "gemini")
    public Map<String, Object> apply(WorkflowState state) {
        int iteration = state.iteration();

        if (iteration >= MAX_ITERATIONS) {
            log.warn("[Supervisor] Max iterations ({}) reached — forcing END.", MAX_ITERATIONS);
            return Map.of("route", WorkflowState.ROUTE_END,
                          "done",  true,
                          "iteration", iteration + 1);
        }

        List<String> history = state.history();
        String historyText   = history.isEmpty()
                ? "(none yet)"
                : String.join("\n---\n", history);

        Prompt prompt = ROUTER_TEMPLATE.apply(Map.of(
                "task",    state.task(),
                "history", historyText));

        // ChatModel.chat(String) → String  (renamed from ChatLanguageModel.generate in 1.x)
        String raw      = llm.chat(prompt.text()).trim().toLowerCase();
        String decision = raw.replaceAll("[^a-z]", "");   // strip stray punctuation

        log.info("[Supervisor] iteration={} raw='{}' cleaned='{}'", iteration, raw, decision);

        String route = switch (decision) {
            case "inventory" -> WorkflowState.ROUTE_INVENTORY;
            case "analytics" -> WorkflowState.ROUTE_ANALYTICS;
            case "report"    -> WorkflowState.ROUTE_REPORT;
            default          -> WorkflowState.ROUTE_END;
        };

        return Map.of(
            "route",     route,
            "done",      route.equals(WorkflowState.ROUTE_END),
            "iteration", iteration + 1
        );
    }

    @SuppressWarnings("unused")
    public Map<String, Object> fallbackDecision(WorkflowState state, Throwable t) {
        log.error("[Supervisor] Fallback triggered: {}", t.getMessage());
        return Map.of("route", WorkflowState.ROUTE_END, "done", true,
                      "iteration", state.iteration() + 1);
    }
}
