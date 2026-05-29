package com.quanshiguang.shiguang.search.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.quanshiguang.framework.common.constant.DateConstants;
import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.framework.common.util.DateUtils;
import com.quanshiguang.framework.common.util.NumberUtils;
import com.quanshiguang.shiguang.dto.RebuildNoteDocumentReqDTO;
import com.quanshiguang.shiguang.search.biz.domain.mapper.SelectMapper;
import com.quanshiguang.shiguang.search.biz.enums.NotePublishTimeRangeEnum;
import com.quanshiguang.shiguang.search.biz.enums.NoteSortTypeEnum;
import com.quanshiguang.shiguang.search.biz.index.NoteIndex;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchNoteReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchNoteRspVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SuggestNoteReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.TrendingNoteReqVO;
import com.quanshiguang.shiguang.search.biz.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
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
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: 用户搜索业务
 **/
@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private RestHighLevelClient restHighLevelClient;
    @Resource
    private SelectMapper selectMapper;

    /**
     * 搜索笔记
     *
     * @param searchNoteReqVO
     * @return
     */
    @Override
    public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        // 查询关键词
        String keyword = searchNoteReqVO.getKeyword();
        // 当前页码
        Integer pageNo = searchNoteReqVO.getPageNo();
        // 笔记类型
        Integer type = searchNoteReqVO.getType();
        // 排序类型
        Integer sort = searchNoteReqVO.getSort();
        // 发布时间范围
        Integer publishTimeRange = searchNoteReqVO.getPublishTimeRange();

        // 构建 SearchRequest，指定要查询的索引
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);

        // 创建查询构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 创建查询条件
        //    "query": {
        //         "bool": {
        //           "must": [
        //             {
        //               "multi_match": {
        //                 "query": "壁纸",
        //                 "fields": [
        //                   "title^2.0",
        //                   "topic^1.0"
        //                 ]
        //               }
        //             }
        //           ],
        //           "filter": [
        //             {
        //               "term": {
        //                 "type": {
        //                   "value": 0
        //                 }
        //               }
        //             }
        //           ]
        //         }
        //       },
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(
                QueryBuilders.multiMatchQuery(keyword)
                        .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f) // 手动设置笔记标题的权重值为 2.0
                        .field(NoteIndex.FIELD_NOTE_TOPIC) // 不设置，权重默认为 1.0
        );

        // 若勾选了笔记类型，添加过滤条件
        if (Objects.nonNull(type)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        // 按发布时间范围过滤
        NotePublishTimeRangeEnum notePublishTimeRangeEnum = NotePublishTimeRangeEnum.valueOf(publishTimeRange);

        if (Objects.nonNull(notePublishTimeRangeEnum)) {
            // 结束时间
            String endTime = LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
            // 开始时间
            String startTime = null;

            switch (notePublishTimeRangeEnum) {
                case DAY ->
                    startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1)); // 一天之前的时间
                case WEEK ->
                    startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1)); // 一周之前的时间
                case HALF_YEAR ->
                    startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6)); // 半年之前的时间
            }
            // 设置时间范围
            if (StringUtils.isNoneBlank(startTime)) {
                boolQueryBuilder.filter(QueryBuilders.rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME)
                        .gte(startTime) // 大于等于
                        .lte(endTime) // 小于等于
                );
            }
        }

        // 排序
        NoteSortTypeEnum noteSortTypeEnum = NoteSortTypeEnum.valueOf(sort);

        // 设置排序
        // "sort": [
        //     {
        //       "_score": {
        //         "order": "desc"
        //       }
        //     }
        //   ]
        if (Objects.nonNull(noteSortTypeEnum)) {
            switch (noteSortTypeEnum) {
                // 按笔记发布时间降序
                case LATEST -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                // 按笔记点赞量降序
                case MOST_LIKE -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                // 按评论量降序
                case MOST_COMMENT -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                // 按收藏量降序
                case MOST_COLLECT -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            // 设置查询
            sourceBuilder.query(boolQueryBuilder);
        } else { // 综合排序
            // 综合排序，自定义评分，并按 _score 评分降序
            sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

            // 创建 FilterFunctionBuilder 数组
            // "functions": [
            //         {
            //           "field_value_factor": {
            //             "field": "like_total",
            //             "factor": 0.5,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "collect_total",
            //             "factor": 0.3,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "comment_total",
            //             "factor": 0.2,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         }
            //       ],
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                    .factor(0.5f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                    .factor(0.3f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                    .factor(0.2f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    // 时间衰减：7天前的笔记热度衰减到0.5，避免老笔记永远排第一
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            ScoreFunctionBuilders.gaussDecayFunction(NoteIndex.FIELD_NOTE_CREATE_TIME,
                                    LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S),
                                    "7d", "0d", 0.5))
            };

            // 构建 function_score 查询
            // "score_mode": "sum",
            // "boost_mode": "sum"
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder,
                            filterFunctionBuilders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM) // score_mode 为 sum
                    .boostMode(CombineFunction.SUM); // boost_mode 为 sum

            // 设置查询
            sourceBuilder.query(functionScoreQueryBuilder);
        }

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest.source().toString());
            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = Lists.newArrayList();

            // 获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
                String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
                String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
                String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
                String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
                // 获取更新时间
                String updateTimeStr = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
                LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
                Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);
                Integer commentTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COMMENT_TOTAL);
                Integer collectTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COLLECT_TOTAL);

                // 获取高亮字段
                String highlightedTitle = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                    highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
                }

                // 构建 VO 实体类
                SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                        .noteId(noteId)
                        .cover(cover)
                        .title(title)
                        .highlightTitle(highlightedTitle)
                        .avatar(avatar)
                        .nickname(nickname)
                        .updateTime(DateUtils.formatRelativeTime(updateTime))
                        .likeTotal(NumberUtils.formatNumberString(likeTotal))
                        .commentTotal(NumberUtils.formatNumberString(commentTotal))
                        .collectTotal(NumberUtils.formatNumberString(collectTotal))
                        .build();
                searchNoteRspVOS.add(searchNoteRspVO);
            }
        } catch (IOException e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }

    /**
     * 重建笔记文档
     *
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    @Override
    public Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO) {
        Long noteId = rebuildNoteDocumentReqDTO.getId();

        // 从数据库查询 Elasticsearch 索引数据
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId, null);

        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : result) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id((String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID))));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            // 将数据写入 Elasticsearch 索引
            try {
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                log.error("==> 重建笔记文档失败: ", e);
            }
        }
        return Response.success();
    }

    /**
     * 搜索建议（基于 ES Completion Suggester 前缀匹配）
     */
    @Override
    public Response<List<String>> suggest(SuggestNoteReqVO suggestNoteReqVO) {
        String keyword = suggestNoteReqVO.getKeyword();
        int size = suggestNoteReqVO.getSize() != null ? suggestNoteReqVO.getSize() : 10;

        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // Completion Suggester：基于 title 字段做前缀匹配
        SuggestBuilder suggestBuilder = new SuggestBuilder();
        suggestBuilder.addSuggestion(
                "note_suggest",
                SuggestBuilders.completionSuggestion("title.suggest")
                        .prefix(keyword)
                        .size(size)
                        .skipDuplicates(true)
        );
        sourceBuilder.suggest(suggestBuilder);
        // 不需要返回命中文档
        sourceBuilder.size(0);

        searchRequest.source(sourceBuilder);

        List<String> suggestions = Lists.newArrayList();
        try {
            log.info("==> [搜索建议] SuggestRequest: keyword={}, size={}", keyword, size);
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            Suggest suggest = searchResponse.getSuggest();
            if (suggest != null) {
                CompletionSuggestion completionSuggestion = suggest.getSuggestion("note_suggest");
                if (completionSuggestion != null) {
                    for (CompletionSuggestion.Entry entry : completionSuggestion.getEntries()) {
                        for (CompletionSuggestion.Entry.Option option : entry.getOptions()) {
                            suggestions.add(option.getText().string());
                        }
                    }
                }
            }
            log.info("==> [搜索建议] 返回条数: {}", suggestions.size());
        } catch (IOException e) {
            log.error("==> [搜索建议] ES 查询异常: ", e);
        }

        return Response.success(suggestions);
    }

    /**
     * 热度排行（function_score + Gauss 时间衰减，无需关键词）
     */
    @Override
    public PageResponse<SearchNoteRspVO> trending(TrendingNoteReqVO trendingNoteReqVO) {
        Integer pageNo = trendingNoteReqVO.getPageNo();
        Integer type = trendingNoteReqVO.getType();
        Integer publishTimeRange = trendingNoteReqVO.getPublishTimeRange();

        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 构建基础查询（match_all，可加过滤条件）
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        if (Objects.nonNull(type)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        NotePublishTimeRangeEnum notePublishTimeRangeEnum = NotePublishTimeRangeEnum.valueOf(publishTimeRange);
        if (Objects.nonNull(notePublishTimeRangeEnum)) {
            String endTime = LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
            String startTime = null;
            switch (notePublishTimeRangeEnum) {
                case DAY -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1));
                case WEEK -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1));
                case HALF_YEAR -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6));
            }
            if (StringUtils.isNoneBlank(startTime)) {
                boolQueryBuilder.filter(QueryBuilders.rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME).gte(startTime).lte(endTime));
            }
        }

        // function_score：热度 + 时间衰减
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                .factor(0.5f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                .factor(0.3f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                .factor(0.2f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.gaussDecayFunction(NoteIndex.FIELD_NOTE_CREATE_TIME,
                                LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S),
                                "7d", "0d", 0.5))
        };

        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(boolQueryBuilder, functions)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                .boostMode(CombineFunction.SUM);

        sourceBuilder.query(functionScoreQuery);
        sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

        int pageSize = 10;
        sourceBuilder.from((pageNo - 1) * pageSize);
        sourceBuilder.size(pageSize);

        searchRequest.source(sourceBuilder);

        List<SearchNoteRspVO> searchNoteRspVOS = null;
        long total = 0;
        try {
            log.info("==> [热度排行] SearchRequest: {}", sourceBuilder);
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            total = searchResponse.getHits().getTotalHits().value;
            searchNoteRspVOS = parseHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("==> [热度排行] ES 查询异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }

    /**
     * 解析 SearchHits 为 VO 列表（抽取公共逻辑）
     */
    private List<SearchNoteRspVO> parseHits(SearchHits hits) {
        List<SearchNoteRspVO> list = Lists.newArrayList();
        for (SearchHit hit : hits) {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
            String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
            String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
            String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
            String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
            String updateTimeStr = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
            LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
            Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);
            Integer commentTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COMMENT_TOTAL);
            Integer collectTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COLLECT_TOTAL);

            String highlightedTitle = null;
            if (CollUtil.isNotEmpty(hit.getHighlightFields())
                    && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
            }

            list.add(SearchNoteRspVO.builder()
                    .noteId(noteId)
                    .cover(cover)
                    .title(title)
                    .highlightTitle(highlightedTitle)
                    .avatar(avatar)
                    .nickname(nickname)
                    .updateTime(DateUtils.formatRelativeTime(updateTime))
                    .likeTotal(NumberUtils.formatNumberString(likeTotal))
                    .commentTotal(NumberUtils.formatNumberString(commentTotal))
                    .collectTotal(NumberUtils.formatNumberString(collectTotal))
                    .build());
        }
        return list;
    }
}
