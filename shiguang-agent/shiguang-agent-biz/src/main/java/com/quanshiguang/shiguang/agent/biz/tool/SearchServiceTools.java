package com.quanshiguang.shiguang.agent.biz.tool;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SearchServiceTools {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "搜索笔记：根据关键词在笔记标题和话题中搜索，返回匹配的笔记列表")
    @SentinelResource(value = "searchNotes",
            blockHandler = "searchNotesFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "searchNotesFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String searchNotes(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回条数，默认5") int limit
    ) {
        try {
            SearchRequest request = new SearchRequest("note");
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.query(QueryBuilders.multiMatchQuery(keyword).field("title", 2.0f).field("topic"));
            source.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));
            source.size(Math.min(limit, 10));
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                Map<String, Object> map = hit.getSourceAsMap();
                map.put("_score", hit.getScore());
                results.add(map);
            }
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.error("==> [SearchTool] 搜索异常: ", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取热门话题：查询当前最热门的笔记话题，用于推荐话题给用户")
    @SentinelResource(value = "getHotTopics",
            blockHandler = "getHotTopicsFallback",
            blockHandlerClass = ToolFallbackHandler.class,
            fallback = "getHotTopicsFallback",
            fallbackClass = ToolFallbackHandler.class)
    public String getHotTopics(
            @ToolParam(description = "返回条数，默认10") int limit
    ) {
        try {
            SearchRequest request = new SearchRequest("note");
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.aggregation(
                    org.elasticsearch.search.aggregations.AggregationBuilders.terms("hot_topics").field("topic").size(limit)
            );
            source.size(0);
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            org.elasticsearch.search.aggregations.bucket.terms.Terms terms =
                    response.getAggregations().get("hot_topics");

            List<String> topics = new ArrayList<>();
            if (terms != null) {
                for (org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket bucket : terms.getBuckets()) {
                    topics.add(bucket.getKeyAsString() + "(" + bucket.getDocCount() + ")");
                }
            }
            return objectMapper.writeValueAsString(topics);
        } catch (Exception e) {
            log.error("==> [SearchTool] 获取热门话题异常: ", e);
            return "获取热门话题失败: " + e.getMessage();
        }
    }
}
