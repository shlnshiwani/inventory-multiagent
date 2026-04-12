package com.multiagent.agents;

import com.multiagent.model.AgentExecution;
import com.multiagent.service.AgentExecutionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent-1: Inventory Agent
 *
 * Tools: addProduct (T1), searchProducts (T2), getLowStockProducts (T3)
 *
 * The LLM interaction is handled by {@link InventoryAssistant} — a declarative
 * {@code @AiService} interface that Spring wires with the auto-configured
 * {@code ChatModel} and {@code ChatMemoryProvider} beans.
 *
 * This class owns the orchestration concerns:
 *   - Resilience4j circuit breaker + retry
 *   - DB logging of input/output via {@link AgentExecutionService}
 */
@Service
public class InventoryAgent {

    private static final Logger log  = LoggerFactory.getLogger(InventoryAgent.class);
    public  static final String NAME = "InventoryAgent";

    private final InventoryAssistant    assistant;
    private final AgentExecutionService executionService;

    public InventoryAgent(InventoryAssistant assistant,
                          AgentExecutionService executionService) {
        this.assistant        = assistant;
        this.executionService = executionService;
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
    @Retry(name = "gemini")
    public String process(String task, String sessionId, int iteration) {
        log.info("[InventoryAgent] session={} iter={} input={} chars",
                 sessionId, iteration, task.length());

        String output = assistant.process(task);

        AgentExecution record = executionService.log(sessionId, NAME, iteration, task, output);
        log.info("[InventoryAgent] saved execution id={}", record.id());

        return output;
    }

    @SuppressWarnings("unused")
    public String fallback(String task, String sessionId, int iteration, Throwable t) {
        log.error("[InventoryAgent] Resilience4j fallback: {}", t.getMessage());
        String msg = "InventoryAgent unavailable: " + t.getMessage();
        executionService.log(sessionId, NAME, iteration, task, msg);
        return msg;
    }
}
