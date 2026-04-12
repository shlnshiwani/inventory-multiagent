package com.multiagent.agents;

import com.multiagent.model.AgentExecution;
import com.multiagent.service.AgentExecutionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent-2: Analytics Agent
 *
 * Tools: calculateInventoryValue (T4), getCategoryBreakdown (T5), getLowStockProducts (T3)
 *
 * The LLM interaction is handled by {@link AnalyticsAssistant} — a declarative
 * {@code @AiService} interface wired with AnalyticsTools + InventoryTools.
 *
 * Can read prior agent outputs from DB via {@link AgentExecutionService#getSharedContext(String)}
 * to build on Inventory Agent's results without re-querying the LLM.
 */
@Service
public class AnalyticsAgent {

    private static final Logger log  = LoggerFactory.getLogger(AnalyticsAgent.class);
    public  static final String NAME = "AnalyticsAgent";

    private final AnalyticsAssistant    assistant;
    private final AgentExecutionService executionService;

    public AnalyticsAgent(AnalyticsAssistant assistant,
                          AgentExecutionService executionService) {
        this.assistant        = assistant;
        this.executionService = executionService;
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
    @Retry(name = "gemini")
    public String process(String task, String sessionId, int iteration) {
        log.info("[AnalyticsAgent] session={} iter={} input={} chars",
                 sessionId, iteration, task.length());

        String output = assistant.process(task);

        AgentExecution record = executionService.log(sessionId, NAME, iteration, task, output);
        log.info("[AnalyticsAgent] saved execution id={}", record.id());

        return output;
    }

    @SuppressWarnings("unused")
    public String fallback(String task, String sessionId, int iteration, Throwable t) {
        log.error("[AnalyticsAgent] Resilience4j fallback: {}", t.getMessage());
        String msg = "AnalyticsAgent unavailable: " + t.getMessage();
        executionService.log(sessionId, NAME, iteration, task, msg);
        return msg;
    }
}
