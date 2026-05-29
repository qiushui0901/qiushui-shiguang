package com.quanshiguang.shiguang.agent.biz.agent;

import com.quanshiguang.shiguang.agent.biz.tool.SearchSummaryTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class SearchSummarizeAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是「小哈书」平台的智能搜索助手。用户会输入搜索关键词，你需要：
            
            1. 调用 searchNotesByHotness 工具搜索相关笔记（结果已按热度排序）
            2. 调用 searchRelatedTopics 工具获取相关话题分类
            3. 基于搜索结果，生成一份结构化的总结推荐
            
            总结格式要求（直接输出 Markdown）：
            
            ## 🎯 搜索总结：{关键词}
            
            ### 📊 相关话题
            列出搜索到的主要话题及笔记数量
            
            ### 🔥 热门推荐
            按热度从高到低，精选 Top5 笔记，每条包含：
            - **标题**（点赞数 | 收藏数 | 评论数）
            - 一句话亮点/推荐理由
            
            ### 💡 实用建议
            基于搜索结果，给出 2-3 条实用建议（如最佳时间、注意事项、推荐路线等）
            
            ### 🏷️ 你可能还想搜
            推荐 3-5 个相关搜索词
            
            规则：
            - 总结要简洁有力，不要堆砌信息
            - 推荐理由要具体，不要泛泛而谈
            - 如果搜索结果为空，给出友好的提示和替代搜索建议
            - 实用建议要基于搜索结果中的真实信息，不要编造
            - 数字要用人类可读格式（如 1.2万）
            """;

    @Resource
    @Qualifier("searchChatClient")
    private ChatClient chatClient;

    @Resource
    private SearchSummaryTools searchSummaryTools;

    @Override
    public String getName() {
        return "search-summarize";
    }

    @Override
    public String getDescription() {
        return "搜索笔记并AI总结：按热度排序搜索结果，生成结构化推荐总结";
    }

    @Override
    public String execute(String userInput, AgentContext context) {
        log.info("==> [搜索总结Agent] 开始执行, keyword={}", userInput);

        String contextHint = buildContextHint(context);
        String result;
        if (contextHint.isEmpty()) {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userInput)
                    .tools(searchSummaryTools)
                    .call()
                    .content();
        } else {
            result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .system(contextHint)
                    .user(userInput)
                    .tools(searchSummaryTools)
                    .call()
                    .content();
        }

        log.info("==> [搜索总结Agent] 执行完成");
        return result;
    }

    @Override
    public Flux<String> executeStream(String userInput, AgentContext context) {
        log.info("==> [搜索总结Agent] 流式执行（降级为同步）, keyword={}", userInput);
        return Flux.just(execute(userInput, context));
    }

    @Override
    public ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient, String userInput, AgentContext context) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .tools(searchSummaryTools);
    }

    private String buildContextHint(AgentContext context) {
        if (context == null || context.getUserId() == null) {
            return "";
        }
        return "[当前用户ID] " + context.getUserId() + "（可据此获取用户兴趣辅助推荐）";
    }
}
