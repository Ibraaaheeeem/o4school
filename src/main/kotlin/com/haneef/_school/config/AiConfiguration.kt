package com.haneef._school.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.InMemoryChatMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfiguration {

    @Bean
    fun chatClient(builder: ChatClient.Builder): ChatClient {
        return builder
            .defaultAdvisors(MessageChatMemoryAdvisor(InMemoryChatMemory()))
            .build()
    }
}
