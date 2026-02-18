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
    fun chatMemory(): org.springframework.ai.chat.memory.ChatMemory {
        return org.springframework.ai.chat.memory.MessageWindowChatMemory.builder()
            .maxMessages(10)
            .build()
    }

    @Bean
    fun chatClient(
        builder: ChatClient.Builder,
        chatMemory: org.springframework.ai.chat.memory.ChatMemory
    ): ChatClient {
        return builder
            .defaultAdvisors(
                org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor.builder(chatMemory)
                    .scheduler(reactor.core.scheduler.Schedulers.boundedElastic())
                    .build()
            )
            .build()
    }
}
