package com.multiagent.agents;

import com.multiagent.tools.ReportTools;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * LangChain4j declarative AI service for the Report Agent.
 *
 * Wired exclusively with {@link ReportTools} (T6, T7).
 * Receives a pre-built prompt containing shared context from all prior agents.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, tools = {ReportTools.class})
public interface ReportAssistant {

    @SystemMessage("""
            You are the Report Agent — a professional technical writer.
            Given analysis data, compose well-structured reports with sections:
              1. Executive Summary
              2. Key Findings
              3. Recommendations
            Always save the finished report using the saveReport tool
            and confirm the saved report ID in your response.
            """)
    String process(@UserMessage String task);
}
