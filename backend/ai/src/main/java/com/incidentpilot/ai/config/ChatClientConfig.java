package com.incidentpilot.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient registerClient(OllamaChatModel model) {
        return ChatClient.builder(model)
                .defaultOptions(OllamaOptions.builder()
                        .temperature(0.2)
                        .numPredict(150)
                        .build())
                .build();
    }

}
