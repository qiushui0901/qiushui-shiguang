package com.quanshiguang.shiguang.agent.biz.tool;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.quanshiguang.framework.biz.context.holder.LoginUserContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RecommendServiceTools {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Tool(description = "Get the current user's interested topics based on likes and collections.")
    @SentinelResource(value = "getUserInterestTopics",
            blockHandler = "getUserInterestTopicsFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "getUserInterestTopicsFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String getUserInterestTopics(
            @ToolParam(description = "Maximum topic count, default 5") int limit
    ) {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            return "login required";
        }

        String topicKey = "recommend:interest:topic:" + userId;
        Set<Object> topics = redisTemplate.opsForZSet()
                .reverseRangeByScore(topicKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, sanitizeLimit(limit));

        if (topics == null || topics.isEmpty()) {
            return "no interest topics";
        }

        return topics.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    @Tool(description = "Get the current user's following list for social-interest context.")
    @SentinelResource(value = "getUserFollowingList",
            blockHandler = "getUserFollowingListFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "getUserFollowingListFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String getUserFollowingList(
            @ToolParam(description = "Maximum following count, default 10") int limit
    ) {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            return "login required";
        }

        String followingKey = "following:" + userId;
        Set<Object> followingSet = redisTemplate.opsForZSet()
                .reverseRangeByScore(followingKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, sanitizeLimit(limit));

        if (followingSet == null || followingSet.isEmpty()) {
            return "no following data";
        }

        return followingSet.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    private long sanitizeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }
}
