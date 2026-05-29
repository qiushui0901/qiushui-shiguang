package com.quanshiguang.shiguang.agent.biz.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanshiguang.framework.biz.context.holder.LoginUserContextHolder;
import com.quanshiguang.framework.common.constant.DateConstants;
import com.quanshiguang.framework.common.util.NumberUtils;
import com.quanshiguang.shiguang.agent.biz.agent.*;
import com.quanshiguang.shiguang.agent.biz.model.vo.AgentChatReqVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.AgentChatRspVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.SearchSummaryReqVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.SearchSummaryRspVO;
import com.quanshiguang.shiguang.agent.biz.agent.dto.ContentModerationResult;
import com.quanshiguang.shiguang.agent.biz.memory.RedisChatMemory;
import com.quanshiguang.shiguang.agent.biz.orchestrator.AgentOrchestrator;
import com.quanshiguang.shiguang.agent.biz.service.AgentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    @Resource
    private AgentOrchestrator orchestrator;

    @Resource
    private ContentUnderstandingAgent contentUnderstandingAgent;

    @Resource
    private ContentModerationAgent contentModerationAgent;

    @Resource
    private CreativeAssistantAgent creativeAssistantAgent;

    @Resource
    private SearchSummarizeAgent searchSummarizeAgent;

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private RedisChatMemory redisChatMemory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AgentChatRspVO chat(AgentChatReqVO req) {
        String sessionId = req.getSessionId() != null ? req.getSessionId() : UUID.randomUUID().toString();
        Long userId = LoginUserContextHolder.getUserId();

        AgentContext context = AgentContext.create(sessionId, userId);
        context.setConversationSummary(redisChatMemory.buildContext(sessionId));
        String userInput = req.getInput();
        String workflow = req.getWorkflow() != null ? req.getWorkflow() : "guarded";

        List<AgentResult> results;

        switch (workflow) {
            case "sequential" -> {
                results = executeGuarded(userInput, context, false);
            }
            case "parallel" -> {
                results = executeGuarded(userInput, context, true);
            }
            case "conditional" -> {
                results = orchestrator.executeConditional(
                        List.of(contentUnderstandingAgent, contentModerationAgent, creativeAssistantAgent),
                        userInput, context,
                        agentResult -> {
                            if ("content-moderation".equals(agentResult.agentName()) && agentResult.success()) {
                                try {
                                    ContentModerationResult moderation = objectMapper.readValue(
                                            agentResult.output(), ContentModerationResult.class);
                                    return moderation.getApproved() != null && moderation.getApproved();
                                } catch (Exception e) {
                                    log.warn("==> [conditional] 解析审核结果失败，降级为字符串匹配: ", e);
                                    return agentResult.output() != null
                                            && !agentResult.output().contains("\"approved\": false");
                                }
                            }
                            return true;
                        });
            }
            default -> {
                results = executeGuarded(userInput, context, false);
            }
        }

        List<AgentChatRspVO.AgentOutput> outputs = results.stream()
                .map(r -> AgentChatRspVO.AgentOutput.builder()
                        .agentName(r.agentName())
                        .output(r.output())
                        .success(r.success())
                        .errorMessage(r.errorMessage())
                        .build())
                .toList();

        String finalResult = results.stream()
                .filter(AgentResult::success)
                .reduce((first, second) -> second)
                .map(AgentResult::output)
                .orElse("所有 Agent 执行失败");

        redisChatMemory.add(sessionId, "user", userInput);
        redisChatMemory.add(sessionId, "assistant", finalResult);

        return AgentChatRspVO.builder()
                .sessionId(sessionId)
                .agentOutputs(outputs)
                .finalResult(finalResult)
                .build();
    }

    @Override
    public void chatStream(AgentChatReqVO req, SseEmitter emitter) {
        String sessionId = req.getSessionId() != null ? req.getSessionId() : UUID.randomUUID().toString();
        Long userId = LoginUserContextHolder.getUserId();

        AgentContext context = AgentContext.create(sessionId, userId);
        context.setConversationSummary(redisChatMemory.buildContext(sessionId));
        String userInput = req.getInput();

        try {
            // Phase 1: 非面向用户的 Agent 同步执行
            List<AgentResult> preResults = orchestrator.executeSequential(
                    List.of(contentUnderstandingAgent, contentModerationAgent),
                    userInput, context);

            for (AgentResult r : preResults) {
                emitter.send(SseEmitter.event()
                        .name("agent-result")
                        .data(Map.of(
                                "agentName", r.agentName(),
                                "success", r.success(),
                                "output", r.output() != null ? r.output() : r.errorMessage()
                        )));
            }

            // Phase 2: CreativeAssistant 流式输出
            if (!context.isModerationApproved()) {
                String rejectedMessage = context.buildModerationRejectedMessage();
                emitter.send(SseEmitter.event().name("agent-result").data(Map.of(
                        "agentName", "moderation-gate",
                        "success", true,
                        "output", rejectedMessage
                )));
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                redisChatMemory.add(sessionId, "user", userInput);
                redisChatMemory.add(sessionId, "assistant", rejectedMessage);
                emitter.complete();
                return;
            }

            StringBuilder assistantOutput = new StringBuilder();
            creativeAssistantAgent.executeStream(userInput, context)
                    .subscribe(
                            chunk -> {
                                try {
                                    assistantOutput.append(chunk);
                                    emitter.send(SseEmitter.event()
                                            .name("chunk")
                                            .data(chunk));
                                } catch (Exception e) {
                                    log.error("==> [流式] SSE 发送异常: ", e);
                                }
                            },
                            error -> {
                                log.error("==> [流式] CreativeAgent 流式异常: ", error);
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("error")
                                            .data("创作助手执行异常: " + error.getMessage()));
                                    emitter.complete();
                                } catch (Exception ignored) {
                                }
                            },
                            () -> {
                                try {
                                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                    redisChatMemory.add(sessionId, "user", userInput);
                                    redisChatMemory.add(sessionId, "assistant", assistantOutput.toString());
                                    emitter.complete();
                                } catch (Exception ignored) {
                                }
                            }
                    );
        } catch (Exception e) {
            log.error("==> [流式] 执行异常: ", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }

    private List<AgentResult> executeGuarded(String userInput, AgentContext context, boolean parallelPreAgents) {
        List<AgentResult> results = new ArrayList<>();
        List<Agent> preAgents = List.of(contentUnderstandingAgent, contentModerationAgent);

        if (parallelPreAgents) {
            results.addAll(orchestrator.executeParallel(preAgents, userInput, context));
        } else {
            results.addAll(orchestrator.executeSequential(preAgents, userInput, context));
        }

        if (!context.isModerationApproved()) {
            results.add(AgentResult.success("moderation-gate", context.buildModerationRejectedMessage()));
            return results;
        }

        results.addAll(orchestrator.executeSequential(List.of(creativeAssistantAgent), userInput, context));
        return results;
    }

    @Override
    public SearchSummaryRspVO searchSummary(SearchSummaryReqVO req) {
        String keyword = req.getKeyword();
        Long userId = LoginUserContextHolder.getUserId();
        log.info("==> [搜索总结] keyword={}, userId={}", keyword, userId);

        List<SearchSummaryRspVO.NoteItem> noteItems = searchNotesByHotness(keyword, req.getType());
        List<String> relatedTopics = searchRelatedTopics(keyword);

        AgentContext context = AgentContext.create(UUID.randomUUID().toString(), userId);
        String summary = searchSummarizeAgent.execute(keyword, context);

        return SearchSummaryRspVO.builder()
                .summary(summary)
                .notes(noteItems)
                .relatedTopics(relatedTopics)
                .build();
    }

    private List<SearchSummaryRspVO.NoteItem> searchNotesByHotness(String keyword, Integer type) {
        try {
            SearchRequest request = new SearchRequest("note");
            SearchSourceBuilder source = new SearchSourceBuilder();

            var boolQuery = QueryBuilders.boolQuery().must(
                    QueryBuilders.multiMatchQuery(keyword).field("title", 2.0f).field("topic")
            );
            if (type != null) {
                boolQuery.filter(QueryBuilders.termQuery("type", type));
            }

            FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = {
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder("like_total")
                                    .factor(0.5f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder("collect_total")
                                    .factor(0.3f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder("comment_total")
                                    .factor(0.2f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            ScoreFunctionBuilders.gaussDecayFunction("create_time",
                                    LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S),
                                    "7d", "0d", 0.5))
            };

            source.query(QueryBuilders.functionScoreQuery(boolQuery, functions)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .boostMode(CombineFunction.SUM));
            source.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));
            source.size(20);
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            List<SearchSummaryRspVO.NoteItem> items = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                Map<String, Object> map = hit.getSourceAsMap();
                items.add(SearchSummaryRspVO.NoteItem.builder()
                        .noteId(map.get("id") != null ? Long.valueOf(map.get("id").toString()) : null)
                        .title((String) map.get("title"))
                        .cover((String) map.get("cover"))
                        .nickname((String) map.get("nickname"))
                        .avatar((String) map.get("avatar"))
                        .likeTotal((Integer) map.get("like_total"))
                        .collectTotal((Integer) map.get("collect_total"))
                        .commentTotal((Integer) map.get("comment_total"))
                        .topic((String) map.get("topic"))
                        .createTime((String) map.get("create_time"))
                        .build());
            }
            return items;
        } catch (Exception e) {
            log.error("==> [搜索总结] ES 查询异常: ", e);
            return Collections.emptyList();
        }
    }

    private List<String> searchRelatedTopics(String keyword) {
        try {
            SearchRequest request = new SearchRequest("note");
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.query(QueryBuilders.multiMatchQuery(keyword).field("title", 2.0f).field("topic"));
            source.aggregation(
                    org.elasticsearch.search.aggregations.AggregationBuilders.terms("topics").field("topic").size(10)
            );
            source.size(0);
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            org.elasticsearch.search.aggregations.bucket.terms.Terms terms =
                    response.getAggregations().get("topics");

            if (terms != null) {
                return terms.getBuckets().stream()
                        .map(b -> b.getKeyAsString() + "(" + b.getDocCount() + ")")
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("==> [搜索总结] 话题聚合异常: ", e);
            return Collections.emptyList();
        }
    }
}
