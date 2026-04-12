package com.multiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Inventory Multi-Agent System.
 *
 * <h2>Stack</h2>
 * <ul>
 *   <li>Spring Boot 3.5.13 — application framework</li>
 *   <li>LangChain4j 1.13.0 — LLM integration (@Tool, AiServices, ChatLanguageModel)</li>
 *   <li>LangGraph4j 1.8.11 — stateful agent orchestration (StateGraph, NodeAction)</li>
 *   <li>Gemini 2.0 Flash    — LLM via Google AI API</li>
 *   <li>H2                  — in-memory database (schema.sql + data.sql)</li>
 *   <li>Resilience4j        — @CircuitBreaker + @Retry on every LLM call</li>
 * </ul>
 *
 * <h2>Run</h2>
 * <pre>
 *   export GEMINI_API_KEY=your_key_here
 *   mvn spring-boot:run
 *   # or
 *   mvn package && java -jar target/inventory-multiagent-1.0.0.jar
 * </pre>
 */
@SpringBootApplication
public class MultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiAgentApplication.class, args);
    }
}
