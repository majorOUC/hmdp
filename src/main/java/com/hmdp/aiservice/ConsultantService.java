package com.hmdp.aiservice;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ConsultantService {

    private final ChatClient chatClient;

    public ConsultantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Flux<String> chat(String memoryId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", memoryId))
                .stream()
                .content();
    }
}
