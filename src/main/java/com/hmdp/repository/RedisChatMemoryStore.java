package com.hmdp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RedisChatMemoryStore implements ChatMemoryRepository {

    private static final Duration TTL = Duration.ofDays(1);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String json = redisTemplate.opsForValue().get(conversationId);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, String>> rawList = objectMapper.readValue(json, new TypeReference<>() {});
            List<Message> messages = new ArrayList<>();
            for (Map<String, String> raw : rawList) {
                MessageType type = MessageType.valueOf(raw.get("type"));
                String content = raw.get("content");
                messages.add(switch (type) {
                    case USER -> new UserMessage(content);
                    case ASSISTANT -> new AssistantMessage(content);
                    case SYSTEM -> new SystemMessage(content);
                    default -> throw new IllegalArgumentException("Unsupported message type: " + type);
                });
            }
            return messages;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize messages from Redis", e);
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            List<Map<String, String>> rawList = new ArrayList<>();
            for (Message message : messages) {
                Map<String, String> map = new HashMap<>();
                map.put("type", message.getMessageType().name());
                map.put("content", message.getText());
                rawList.add(map);
            }
            String json = objectMapper.writeValueAsString(rawList);
            redisTemplate.opsForValue().set(conversationId, json, TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize messages to Redis", e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(conversationId);
    }
}
