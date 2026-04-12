package com.multiagent.agents;

import com.multiagent.model.AgentExecution;
import com.multiagent.service.AgentExecutionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent-3: Report Agent
 *
 * Tools: saveReport (T6), listReports (T7)
 *
 * The LLM interaction is handled by {@link ReportAssistant} — a declarative
 * {@code @AiService} interface wired with ReportTools only.
 *
 * Typically the final agent in the workflow. The graph passes a prompt that
 * already contains the shared context from all prior agents (sourced from DB
 * via {@link AgentExecutionService#getSharedContext(String)}), so this agent
 * synthesises all findings into a structured report.
 */
@Service
public class ReportAgent {

    private static final Logger log  = LoggerFactory.getLogger(ReportAgent.class);
    public  static final String NAME = "ReportAgent";

    private final ReportAssistant       assistant;
    private final AgentExecutionService executionService;

    public ReportAgent(ReportAssistant assistant,
                       AgentExecutionService executionService) {
        this.assistant        = assistant;
        this.executionService = executionService;
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
    @Retry(name = "gemini")
    public String process(String task, String sessionId, int iteration) {
        log.info("[ReportAgent] session={} iter={} input={} chars",
                 sessionId, iteration, task.length());

        String output = assistant.process(task);

        AgentExecution record = executionService.log(sessionId, NAME, iteration, task, output);
        log.info("[ReportAgent] saved execution id={}", record.id());

        return output;
    }

    @SuppressWarnings("unused")
    public String fallback(String task, String sessionId, int iteration, Throwable t) {
        log.error("[ReportAgent] Resilience4j fallback: {}", t.getMessage());
        String msg = "ReportAgent unavailable: " + t.getMessage();
        executionService.log(sessionId, NAME, iteration, task, msg);
        return msg;
    }
}
