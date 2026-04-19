package com.multiagent.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Declarative LangChain4j AI service for the Critic Agent.
 *
 * No tools or chat memory — every evaluation is independent and stateless.
 * Spring auto-wires the configured {@code ChatModel} bean only.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT)
public interface CriticAssistant {

    @SystemMessage("""
            You are a Quality Critic for an inventory management AI system.
            Evaluate the agent's response strictly for:
              - Correctness: did it answer the task accurately using its tools?
              - Completeness: did it cover ALL required points in the task?
              - Tool usage: did it call its tools rather than making up data?

            Respond in EXACTLY this format — no other text:
            SCORE: <integer 1-10>
            FEEDBACK: <one sentence describing the main weakness or confirming quality>
            """)
    String evaluate(@UserMessage String prompt);
}
