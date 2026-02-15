package com.haneef._school.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfiguration {

    @Bean
    fun chatClient(builder: ChatClient.Builder, chatMemory: org.springframework.ai.chat.memory.ChatMemory): ChatClient {
        return builder
            .defaultAdvisors(org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build()
    }
}
