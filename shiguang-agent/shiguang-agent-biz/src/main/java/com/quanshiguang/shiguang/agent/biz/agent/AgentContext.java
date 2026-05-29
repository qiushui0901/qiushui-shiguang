package com.quanshiguang.shiguang.agent.biz.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentModerationResult;
import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentUnderstandingResult;
import com.quanshiguang.shiguang.agent.biz.agent.dto.CreativeAssistantResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
public class AgentContext {

    private String sessionId;

    private Long userId;

    private Map<String, String> agentOutputs = new ConcurrentHashMap<>();

    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    private ContentUnderstandingResult understandingResult;

    private ContentModerationResult moderationResult;

    private CreativeAssistantResult creativeAssistantResult;

    private String conversationSummary;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AgentContext addOutput(String agentName, String output) {
        agentOutputs.put(agentName, output);
        bindStructuredOutput(agentName, output);
        return this;
    }

    public String getOutput(String agentName) {
        return agentOutputs.get(agentName);
    }

    public <T> T getOutputAs(String agentName, Class<T> clazz) {
        String json = agentOutputs.get(agentName);
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(extractJsonObject(json), clazz);
        } catch (JsonProcessingException e) {
            log.error("==> [AgentContext] 解析Agent输出失败, agentName={}, targetClass={}: ", agentName, clazz.getSimpleName(), e);
            return null;
        }
    }

    public boolean isModerationApproved() {
        return moderationResult == null
                || moderationResult.getApproved() == null
                || moderationResult.getApproved();
    }

    public String buildModerationRejectedMessage() {
        if (moderationResult == null) {
            return "内容审核未通过，请根据审核建议修改后再继续创作。";
        }
        String suggestion = moderationResult.getSuggestion() == null ? "请调整后重新提交。" : moderationResult.getSuggestion();
        return "内容审核未通过，风险等级：" + moderationResult.getRiskLevel()
                + "，问题：" + moderationResult.getIssues()
                + "，建议：" + suggestion;
    }

    private void bindStructuredOutput(String agentName, String output) {
        if (output == null) {
            return;
        }
        if ("content-understanding".equals(agentName)) {
            understandingResult = parseOutput(output, ContentUnderstandingResult.class);
        } else if ("content-moderation".equals(agentName)) {
            moderationResult = parseOutput(output, ContentModerationResult.class);
        } else if ("creative-assistant".equals(agentName)) {
            creativeAssistantResult = parseOutput(output, CreativeAssistantResult.class);
        }
    }

    private <T> T parseOutput(String output, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(extractJsonObject(output), clazz);
        } catch (JsonProcessingException e) {
            log.warn("==> [AgentContext] 结构化解析失败, targetClass={}", clazz.getSimpleName());
            return null;
        }
    }

    private String extractJsonObject(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return output.substring(start, end + 1);
        }
        return output;
    }

    public static AgentContext create(String sessionId, Long userId) {
        AgentContext ctx = new AgentContext();
        ctx.setSessionId(sessionId);
        ctx.setUserId(userId);
        return ctx;
    }
}
