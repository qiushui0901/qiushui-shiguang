package com.quanshiguang.shiguang.agent.biz.tool;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.GaussDecayFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SearchSummaryTools {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "按热度搜索笔记：根据关键词搜索笔记，结果按热度（点赞+收藏+评论）和时间衰减综合排序，返回最热门的相关笔记")
    @SentinelResource(value = "searchNotesByHotness",
            blockHandler = "searchNotesByHotnessFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "searchNotesByHotnessFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String searchNotesByHotness(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回条数，默认10") int limit
    ) {
        try {
            SearchRequest request = new SearchRequest("note");
            SearchSourceBuilder source = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(
                    QueryBuilders.multiMatchQuery(keyword).field("title", 2.0f).field("topic")
            );

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
                                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                                    "7d", "0d", 0.5))
            };

            FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(boolQuery, functions)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .boostMode(CombineFunction.SUM);

            source.query(functionScoreQuery);
            source.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));
            source.size(Math.min(limit, 20));

            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                Map<String, Object> map = new HashMap<>(hit.getSourceAsMap());
                map.put("_score", hit.getScore());
                results.add(map);
            }

            log.info("==> [SearchSummaryTool] keyword={}, hits={}", keyword, results.size());
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.error("==> [SearchSummaryTool] 搜索异常: ", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool(description = "搜索相关话题：根据关键词聚合出最相关的话题分类及每个话题下的笔记数量，帮助理解搜索意图")
    @SentinelResource(value = "searchRelatedTopics",
            blockHandler = "searchRelatedTopicsFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "searchRelatedTopicsFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String searchRelatedTopics(
            @ToolParam(description = "搜索关键词") String keyword
    ) {
        try {
            SearchRequest request = new SearchRequest("note");
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.query(QueryBuilders.multiMatchQuery(keyword).field("title", 2.0f).field("topic"));
            source.aggregation(
                    org.elasticsearch.search.aggregations.AggregationBuilders.terms("related_topics").field("topic").size(10)
            );
            source.size(0);
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            org.elasticsearch.search.aggregations.bucket.terms.Terms terms =
                    response.getAggregations().get("related_topics");

            List<Map<String, Object>> result = new ArrayList<>();
            if (terms != null) {
                for (org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket bucket : terms.getBuckets()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("topic", bucket.getKeyAsString());
                    item.put("count", bucket.getDocCount());
                    result.add(item);
                }
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("==> [SearchSummaryTool] 话题聚合异常: ", e);
            return "获取相关话题失败: " + e.getMessage();
        }
    }
}
