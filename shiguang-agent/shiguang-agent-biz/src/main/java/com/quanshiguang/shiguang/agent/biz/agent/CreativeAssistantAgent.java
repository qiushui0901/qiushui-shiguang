package com.quanshiguang.shiguang.agent.biz.agent;

import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentModerationResult;
import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentUnderstandingResult;
import com.quanshiguang.shiguang.agent.biz.tool.RecommendServiceTools;
import com.quanshiguang.shiguang.agent.biz.tool.SearchServiceTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class CreativeAssistantAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是「小哈书」平台的创作助手，帮助用户写出更好的笔记。
            
            你的任务是根据用户的需求，返回 JSON 格式的创作建议：
            {
              "title_suggestions": ["标题1", "标题2", "标题3"],  // 3个备选标题
              "content_enhancement": "正文优化建议",              // 如何让正文更吸引人
              "recommended_topics": ["话题1", "话题2"],          // 推荐的话题
              "writing_tips": "写作技巧建议"                     // 针对这类内容的写作技巧
            }
            
            规则：
            1. 标题要吸引人但不标题党，15字以内
            2. 正文优化建议要具体，如"可以加入价格信息"、"增加使用场景描述"
            3. 推荐话题要结合用户兴趣和平台热门话题
            4. 如果用户提供了草稿，在草稿基础上优化而非重写
            5. 写作风格要符合小红书/小哈书平台的调性：真实、分享、种草
            
            你可以调用工具获取：
            - 用户兴趣话题（了解用户偏好）
            - 平台热门话题（了解当前趋势）
            - 搜索相似笔记（参考同类优质内容）
            """;

    @Resource
    @Qualifier("creativeChatClient")
    private ChatClient chatClient;

    @Resource
    private SearchServiceTools searchServiceTools;

    @Resource
    private RecommendServiceTools recommendServiceTools;

    @Override
    public String getName() {
        return "creative-assistant";
    }

    @Override
    public String getDescription() {
        return "辅助用户创作笔记，生成标题、优化正文、推荐话题";
    }

    @Override
    public String execute(String userInput, AgentContext context) {
        log.info("==> [创作助手Agent] 开始执行, inputLength={}", userInput.length());

        String contextHint = buildContextHint(context);
        String result;
        if (contextHint.isEmpty()) {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userInput)
                    .tools(searchServiceTools, recommendServiceTools)
                    .call()
                    .content();
        } else {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .system(contextHint)
                    .user(userInput)
                    .tools(searchServiceTools, recommendServiceTools)
                    .call()
                    .content();
        }

        log.info("==> [创作助手Agent] 执行完成, result={}", result);
        return result;
    }

    @Override
    public Flux<String> executeStream(String userInput, AgentContext context) {
        log.info("==> [创作助手Agent] 流式执行, inputLength={}", userInput.length());

        String contextHint = buildContextHint(context);
        if (contextHint.isEmpty()) {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userInput)
                    .tools(searchServiceTools, recommendServiceTools)
                    .stream()
                    .content();
        }
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .system(contextHint)
                .user(userInput)
                .tools(searchServiceTools, recommendServiceTools)
                .stream()
                .content();
    }

    @Override
    public ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient, String userInput, AgentContext context) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .tools(searchServiceTools, recommendServiceTools);
    }

    private String buildContextHint(AgentContext context) {
        if (context == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        if (context.getConversationSummary() != null && !context.getConversationSummary().isBlank()) {
            sb.append(context.getConversationSummary()).append("\n\n");
        }

        ContentUnderstandingResult understanding = context.getOutputAs("content-understanding", ContentUnderstandingResult.class);
        if (understanding != null) {
            sb.append("[内容理解Agent的分析结果]\n")
                    .append("标签: ").append(understanding.getTags()).append("\n")
                    .append("摘要: ").append(understanding.getSummary()).append("\n")
                    .append("话题: ").append(understanding.getTopic()).append("\n")
                    .append("分类: ").append(understanding.getCategory()).append("\n")
                    .append("情感: ").append(understanding.getSentiment()).append("\n\n");
        } else {
            String raw = context.getOutput("content-understanding");
            if (raw != null) {
                sb.append("[内容理解Agent的分析结果]\n").append(raw).append("\n\n");
            }
        }

        ContentModerationResult moderation = context.getOutputAs("content-moderation", ContentModerationResult.class);
        if (moderation != null) {
            sb.append("[内容审核Agent的审核结果]\n")
                    .append("是否通过: ").append(moderation.getApproved()).append("\n")
                    .append("风险等级: ").append(moderation.getRiskLevel()).append("\n")
                    .append("问题: ").append(moderation.getIssues()).append("\n")
                    .append("建议: ").append(moderation.getSuggestion()).append("\n\n");
        } else {
            String raw = context.getOutput("content-moderation");
            if (raw != null) {
                sb.append("[内容审核Agent的审核结果]\n").append(raw).append("\n\n");
            }
        }

        if (context.getUserId() != null) {
            sb.append("[当前用户ID] ").append(context.getUserId());
        }
        return sb.toString().trim();
    }
}
