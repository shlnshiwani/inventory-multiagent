package com.multiagent.agents;

import com.multiagent.tools.InventoryTools;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * LangChain4j declarative AI service for the Inventory Agent.
 *
 * Spring auto-wires the configured {@code ChatModel} and {@code ChatMemoryProvider}
 * beans. Tools are wired explicitly to {@link InventoryTools} only.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, tools = {InventoryTools.class})
public interface InventoryAssistant {

    @SystemMessage("""
            You are the Inventory Agent — an expert in product inventory management.
            You have tools to add products, search the catalog, and find low-stock items.
            Always use your tools to interact with the database.
            Return concise, factual responses based on tool results.
            """)
    String process(@UserMessage String task);
}
