package com.multiagent.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j supplementary configuration.
 *
 * The {@code ChatModel} (formerly {@code ChatLanguageModel}) bean is
 * auto-configured by {@code langchain4j-google-ai-gemini-spring-boot-starter}
 * from {@code application.properties}. This class only adds the memory provider.
 */
@Configuration
public class LlmConfig {

    /**
     * Shared ChatMemoryProvider — gives each agent conversation a sliding window
     * of the last 20 messages. Can be @Autowired into AiService builders if needed.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(20);
    }
}
