package com.quanshiguang.shiguang.agent.biz.memory;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Short-lived creation memory.
 * Stores only a compact session history in Redis, not long-term full chat logs.
 */
@Component
public class RedisChatMemory {

    private static final String KEY_PREFIX = "agent:memory:";
    private static final long DEFAULT_TTL_HOURS = 2;
    private static final int MAX_MESSAGES = 10;
    private static final int MAX_CONTENT_LENGTH = 1200;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public void add(String sessionId, String role, String content) {
        if (sessionId == null || content == null || content.isBlank()) {
            return;
        }

        String key = KEY_PREFIX + sessionId;
        List<Map<String, String>> history = getHistory(sessionId);

        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", truncate(content));
        history.add(item);

        if (history.size() > MAX_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_MESSAGES, history.size()));
        }

        redisTemplate.opsForValue().set(key, history, DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }

    public String buildContext(String sessionId) {
        List<Map<String, String>> history = getHistory(sessionId);
        if (history.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("[Recent creation context]\n");
        for (Map<String, String> item : history) {
            sb.append(item.getOrDefault("role", "unknown"))
                    .append(": ")
                    .append(item.getOrDefault("content", ""))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getHistory(String sessionId) {
        if (sessionId == null) {
            return new ArrayList<>();
        }

        Object obj = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        if (obj instanceof List) {
            return new ArrayList<>((List<Map<String, String>>) obj);
        }
        return new ArrayList<>();
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        }
    }

    private String truncate(String content) {
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH) + "...";
    }
}
