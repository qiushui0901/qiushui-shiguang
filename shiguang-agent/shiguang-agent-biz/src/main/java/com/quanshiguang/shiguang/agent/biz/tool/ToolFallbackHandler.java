package com.quanshiguang.shiguang.agent.biz.tool;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ToolFallbackHandler {

    public static String searchNotesFallback(String keyword, int limit, BlockException ex) {
        log.warn("==> [SearchTool] searchNotes blocked, keyword={}", keyword);
        return "{\"fallback\": true, \"message\": \"search service is temporarily unavailable\"}";
    }

    public static String getHotTopicsFallback(int limit, BlockException ex) {
        log.warn("==> [SearchTool] getHotTopics blocked");
        return "{\"fallback\": true, \"message\": \"hot topic service is temporarily unavailable\"}";
    }

    public static String detectSensitiveWordsFallback(String text, BlockException ex) {
        log.warn("==> [ModerationTool] detectSensitiveWords blocked");
        return "sensitive-word detection is temporarily unavailable; manual review is required";
    }

    public static String reviewImageContentFallback(String imageDescription, BlockException ex) {
        log.warn("==> [ModerationTool] reviewImageContent blocked");
        return "image moderation is temporarily unavailable; manual review is required";
    }

    public static String getUserInterestTopicsFallback(int limit, BlockException ex) {
        log.warn("==> [RecommendTool] getUserInterestTopics blocked");
        return "user interest service is temporarily unavailable";
    }

    public static String getUserFollowingListFallback(int limit, BlockException ex) {
        log.warn("==> [RecommendTool] getUserFollowingList blocked");
        return "user following service is temporarily unavailable";
    }

    public static String searchNotesByHotnessFallback(String keyword, int limit, BlockException ex) {
        log.warn("==> [SearchSummaryTool] searchNotesByHotness blocked, keyword={}", keyword);
        return "{\"fallback\": true, \"message\": \"search service is temporarily unavailable\"}";
    }

    public static String searchRelatedTopicsFallback(String keyword, BlockException ex) {
        log.warn("==> [SearchSummaryTool] searchRelatedTopics blocked, keyword={}", keyword);
        return "{\"fallback\": true, \"message\": \"topic aggregation service is temporarily unavailable\"}";
    }
}
