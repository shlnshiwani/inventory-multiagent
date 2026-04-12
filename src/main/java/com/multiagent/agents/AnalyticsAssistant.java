package com.multiagent.agents;

import com.multiagent.tools.AnalyticsTools;
import com.multiagent.tools.InventoryTools;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * LangChain4j declarative AI service for the Analytics Agent.
 *
 * Wired with both {@link AnalyticsTools} (T4, T5) and {@link InventoryTools} (T3)
 * so it can cross-reference low-stock data while running analytics.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
           tools = {AnalyticsTools.class, InventoryTools.class})
public interface AnalyticsAssistant {

    @SystemMessage("""
            You are the Analytics Agent — a data analyst specialising in inventory metrics.
            Use your tools to retrieve inventory statistics and identify trends.
            Provide clear, actionable insights backed by data.
            Highlight any stock risks or anomalies you discover.
            """)
    String process(@UserMessage String task);
}
