package com.quanshiguang.shiguang.agent.biz.agent;

import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentUnderstandingResult;
import com.quanshiguang.shiguang.agent.biz.tool.SearchServiceTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class ContentUnderstandingAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是「小哈书」平台的内容理解专家。
            
            你的任务是从用户提交的笔记内容中提取以下信息，以 JSON 格式返回：
            {
              "tags": ["标签1", "标签2", ...],      // 3-5个内容标签
              "summary": "一句话摘要",               // 50字以内
              "topic": "所属话题",                   // 从平台已有话题中选择最匹配的
              "category": "分类",                    // 如：穿搭/美食/旅行/数码/家居/健身/美妆/其他
              "sentiment": "positive/neutral/negative"  // 情感倾向
            }
            
            规则：
            1. 标签要具体，不要泛泛的词（如"生活"）
            2. 话题优先从平台热门话题中选择，如果没有匹配的再自行生成
            3. 摘要要抓住核心内容，不要重复标题
            4. 如果前序 Agent 已有输出，结合上下文理解
            """;

    @Resource
    @Qualifier("understandingChatClient")
    private ChatClient chatClient;

    @Resource
    private SearchServiceTools searchServiceTools;

    @Override
    public String getName() {
        return "content-understanding";
    }

    @Override
    public String getDescription() {
        return "从笔记内容中提取标签、摘要、话题分类和情感倾向";
    }

    @Override
    public String execute(String userInput, AgentContext context) {
        log.info("==> [内容理解Agent] 开始执行, inputLength={}", userInput.length());

        String contextHint = buildContextHint(context);
        String result;
        if (contextHint.isEmpty()) {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userInput)
                    .tools(searchServiceTools)
                    .call()
                    .content();
        } else {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .system(contextHint)
                    .user(userInput)
                    .tools(searchServiceTools)
                    .call()
                    .content();
        }

        log.info("==> [内容理解Agent] 执行完成, result={}", result);
        return result;
    }

    @Override
    public Flux<String> executeStream(String userInput, AgentContext context) {
        log.info("==> [内容理解Agent] 流式执行（降级为同步）, inputLength={}", userInput.length());
        return Flux.just(execute(userInput, context));
    }

    @Override
    public ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient, String userInput, AgentContext context) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .tools(searchServiceTools);
    }

    private String buildContextHint(AgentContext context) {
        if (context == null || context.getAgentOutputs().isEmpty()) {
            return "";
        }
        return "[前序Agent输出]\n" + context.getAgentOutputs();
    }
}
