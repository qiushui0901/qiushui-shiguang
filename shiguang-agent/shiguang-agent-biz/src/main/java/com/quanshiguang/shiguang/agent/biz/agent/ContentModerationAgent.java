package com.quanshiguang.shiguang.agent.biz.agent;

import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentUnderstandingResult;
import com.quanshiguang.shiguang.agent.biz.tool.ContentModerationTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class ContentModerationAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是「小哈书」平台的内容审核员。
            
            你的任务是审核用户提交的笔记内容是否合规，返回 JSON 格式的审核结果：
            {
              "approved": true/false,               // 是否通过审核
              "risk_level": "low/medium/high",       // 风险等级
              "issues": ["问题1", "问题2"],          // 发现的问题列表
              "suggestion": "修改建议"               // 如果不通过，给出修改建议
            }
            
            审核维度：
            1. 文字内容：是否包含敏感词、违规信息、虚假宣传
            2. 图片内容：是否包含不当图片（暴力/色情/侵权）
            3. 整体判断：是否违反社区规范
            
            规则：
            - 先调用敏感词检测工具进行规则匹配
            - 再结合你的判断进行语义层面的审核
            - 如果前序Agent（内容理解Agent）已提取了标签和摘要，结合这些信息审核
            - 对于边界内容，给出修改建议而非直接拒绝
            """;

    @Resource
    @Qualifier("moderationChatClient")
    private ChatClient chatClient;

    @Resource
    private ContentModerationTools contentModerationTools;

    @Override
    public String getName() {
        return "content-moderation";
    }

    @Override
    public String getDescription() {
        return "审核笔记内容是否合规，检测敏感词和不当内容";
    }

    @Override
    public String execute(String userInput, AgentContext context) {
        log.info("==> [内容审核Agent] 开始执行, inputLength={}", userInput.length());

        String contextHint = buildContextHint(context);
        String result;
        if (contextHint.isEmpty()) {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userInput)
                    .tools(contentModerationTools)
                    .call()
                    .content();
        } else {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .system(contextHint)
                    .user(userInput)
                    .tools(contentModerationTools)
                    .call()
                    .content();
        }

        log.info("==> [内容审核Agent] 执行完成, result={}", result);
        return result;
    }

    @Override
    public Flux<String> executeStream(String userInput, AgentContext context) {
        log.info("==> [内容审核Agent] 流式执行（降级为同步）, inputLength={}", userInput.length());
        return Flux.just(execute(userInput, context));
    }

    @Override
    public ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient, String userInput, AgentContext context) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .tools(contentModerationTools);
    }

    private String buildContextHint(AgentContext context) {
        if (context == null) {
            return "";
        }
        ContentUnderstandingResult understanding = context.getOutputAs("content-understanding", ContentUnderstandingResult.class);
        if (understanding == null) {
            String raw = context.getOutput("content-understanding");
            if (raw != null) {
                return "[内容理解Agent的分析结果]\n" + raw;
            }
            return "";
        }
        return "[内容理解Agent的分析结果]\n" +
                "标签: " + understanding.getTags() + "\n" +
                "摘要: " + understanding.getSummary() + "\n" +
                "话题: " + understanding.getTopic() + "\n" +
                "分类: " + understanding.getCategory() + "\n" +
                "情感: " + understanding.getSentiment();
    }
}
