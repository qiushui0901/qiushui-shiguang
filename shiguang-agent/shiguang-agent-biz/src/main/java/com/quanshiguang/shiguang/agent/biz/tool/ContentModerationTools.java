package com.quanshiguang.shiguang.agent.biz.tool;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class ContentModerationTools {

    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "赌博", "色情", "诈骗", "传销", "代购假货", "违禁药品"
    );

    @Tool(description = "敏感词检测：检查文本中是否包含敏感词，返回检测结果")
    @SentinelResource(value = "detectSensitiveWords",
            blockHandler = "detectSensitiveWordsFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "detectSensitiveWordsFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String detectSensitiveWords(
            @ToolParam(description = "待检测的文本内容") String text
    ) {
        List<String> found = SENSITIVE_WORDS.stream()
                .filter(text::contains)
                .toList();

        if (found.isEmpty()) {
            return "未检测到敏感词，内容安全";
        }
        return "检测到敏感词: " + String.join(", ", found) + "，建议修改或拒绝发布";
    }

    @Tool(description = "图片内容描述审核：根据图片描述判断是否包含不当内容（如暴力、色情等）")
    @SentinelResource(value = "reviewImageContent",
            blockHandler = "reviewImageContentFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "reviewImageContentFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String reviewImageContent(
            @ToolParam(description = "图片的文字描述或AI识别出的内容标签") String imageDescription
    ) {
        List<String> violations = List.of();
        String lower = imageDescription.toLowerCase();
        if (lower.contains("暴力") || lower.contains("血腥") || lower.contains("色情") || lower.contains("裸露")) {
            violations = List.of("图片可能包含不当内容");
        }
        if (violations.isEmpty()) {
            return "图片内容审核通过";
        }
        return "图片审核不通过: " + String.join(", ", violations);
    }
}
