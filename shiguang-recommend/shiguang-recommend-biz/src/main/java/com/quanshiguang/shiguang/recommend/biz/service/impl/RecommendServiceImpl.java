package com.quanshiguang.shiguang.recommend.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import com.quanshiguang.framework.biz.context.holder.LoginUserContextHolder;
import com.quanshiguang.framework.common.constant.DateConstants;
import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.framework.common.util.DateUtils;
import com.quanshiguang.framework.common.util.NumberUtils;
import com.quanshiguang.shiguang.recommend.biz.constant.NoteIndexFields;
import com.quanshiguang.shiguang.recommend.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.recommend.biz.model.vo.RecommendNoteReqVO;
import com.quanshiguang.shiguang.recommend.biz.model.vo.RecommendNoteRspVO;
import com.quanshiguang.shiguang.recommend.biz.service.RecommendService;
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
import org.elasticsearch.index.query.functionscore.GaussDecayFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化推荐服务实现
 * 三路召回：热度召回 + 关注召回 + 话题召回，最终合并去重排序
 */
@Service
@Slf4j
public class RecommendServiceImpl implements RecommendService {

    private static final int PAGE_SIZE = 10;

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public PageResponse<RecommendNoteRspVO> recommend(RecommendNoteReqVO req) {
        Long userId = LoginUserContextHolder.getUserId();
        Integer type = req.getType();
        Integer pageNo = req.getPageNo();

        List<RecommendNoteRspVO> result = Lists.newArrayList();

        if (type == null || type == 0) {
            // 综合推荐：三路召回合并
            List<RecommendNoteRspVO> trending = recallTrending(pageNo, userId);
            List<RecommendNoteRspVO> following = recallFromFollowing(userId);
            List<RecommendNoteRspVO> topic = recallByTopic(userId);

            // 合并：关注 > 话题 > 热度，去重
            Set<Long> seen = new LinkedHashSet<>();
            for (RecommendNoteRspVO vo : following) {
                if (seen.add(vo.getNoteId())) result.add(vo);
            }
            for (RecommendNoteRspVO vo : topic) {
                if (seen.add(vo.getNoteId())) result.add(vo);
            }
            for (RecommendNoteRspVO vo : trending) {
                if (seen.add(vo.getNoteId())) result.add(vo);
            }

            // 分页截取
            int from = (pageNo - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, result.size());
            result = from >= result.size() ? Lists.newArrayList() : result.subList(from, to);
        } else if (type == 1) {
            result = recallTrending(pageNo, userId);
        } else if (type == 2) {
            result = recallFromFollowing(userId);
        } else if (type == 3) {
            result = recallByTopic(userId);
        }

        return PageResponse.success(result, pageNo, (long) result.size());
    }

    // ==================== 召回1：热度推荐（ES function_score + 时间衰减）====================

    /**
     * 热度召回：综合点赞/收藏/评论数 + 时间衰减（越新越好）
     */
    private List<RecommendNoteRspVO> recallTrending(int pageNo, Long userId) {
        try {
            SearchRequest request = new SearchRequest(NoteIndexFields.NAME);
            SearchSourceBuilder source = new SearchSourceBuilder();

            // function_score：热度分 + 时间衰减
            // 总分 = sqrt(like)*0.5 + sqrt(collect)*0.3 + sqrt(comment)*0.2 + gauss时间衰减
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = {
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndexFields.LIKE_TOTAL)
                                    .factor(0.5f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndexFields.COLLECT_TOTAL)
                                    .factor(0.3f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndexFields.COMMENT_TOTAL)
                                    .factor(0.2f).modifier(FieldValueFactorFunction.Modifier.SQRT).missing(0)),
                    // 时间衰减：以当前时间为中心，7天内的笔记得分衰减到0.5
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            ScoreFunctionBuilders.gaussDecayFunction(NoteIndexFields.CREATE_TIME,
                                    LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S),
                                    "7d", "0d", 0.5))
            };

            FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
                            QueryBuilders.matchAllQuery(), functions)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .boostMode(CombineFunction.SUM);

            source.query(functionScoreQuery);
            source.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));
            source.from((pageNo - 1) * PAGE_SIZE);
            source.size(PAGE_SIZE);

            request.source(source);
            log.info("==> [热度召回] SearchRequest: {}", source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            return hits2VOs(response, "trending");
        } catch (IOException e) {
            log.error("==> [热度召回] ES 查询异常: ", e);
            return Lists.newArrayList();
        }
    }

    // ==================== 召回2：基于关注的推荐 ====================

    /**
     * 关注召回：从 Redis ZSet（following:{userId}）获取关注的人，查询他们最新发布的笔记
     */
    private List<RecommendNoteRspVO> recallFromFollowing(Long userId) {
        if (userId == null) return Lists.newArrayList();

        // 从 Redis 获取关注列表（最多取 50 个，避免 ES 查询过大）
        String followingKey = RedisKeyConstants.buildUserFollowingKey(userId);
        Set<Object> followingSet = redisTemplate.opsForZSet()
                .reverseRangeByScore(followingKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 50);

        if (CollUtil.isEmpty(followingSet)) return Lists.newArrayList();

        List<Long> followingIds = followingSet.stream()
                .map(o -> Long.valueOf(o.toString()))
                .collect(Collectors.toList());

        try {
            SearchRequest request = new SearchRequest(NoteIndexFields.NAME);
            SearchSourceBuilder source = new SearchSourceBuilder();

            // 查询关注用户发布的笔记，按时间降序
            source.query(QueryBuilders.termsQuery("creator_id", followingIds));
            source.sort(new FieldSortBuilder(NoteIndexFields.CREATE_TIME).order(SortOrder.DESC));
            source.size(PAGE_SIZE * 2); // 多取一些，合并时去重

            request.source(source);
            log.info("==> [关注召回] followingIds: {}", followingIds);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            return hits2VOs(response, "following");
        } catch (IOException e) {
            log.error("==> [关注召回] ES 查询异常: ", e);
            return Lists.newArrayList();
        }
    }

    // ==================== 召回3：基于话题的推荐 ====================

    /**
     * 话题召回：从 Redis Hash 获取用户感兴趣的话题（由行为事件写入），查询同话题热门笔记
     */
    private List<RecommendNoteRspVO> recallByTopic(Long userId) {
        if (userId == null) return Lists.newArrayList();

        // 获取用户感兴趣的话题（ZSet，score=兴趣权重，取 Top3）
        String topicKey = RedisKeyConstants.buildUserInterestTopicKey(userId);
        Set<Object> topTopics = redisTemplate.opsForZSet()
                .reverseRangeByScore(topicKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 3);

        if (CollUtil.isEmpty(topTopics)) return Lists.newArrayList();

        List<String> topics = topTopics.stream().map(Object::toString).collect(Collectors.toList());

        try {
            SearchRequest request = new SearchRequest(NoteIndexFields.NAME);
            SearchSourceBuilder source = new SearchSourceBuilder();

            // 查询感兴趣话题下的热门笔记
            source.query(QueryBuilders.termsQuery(NoteIndexFields.TOPIC, topics));
            source.sort(new FieldSortBuilder(NoteIndexFields.LIKE_TOTAL).order(SortOrder.DESC));
            source.size(PAGE_SIZE * 2);

            request.source(source);
            log.info("==> [话题召回] topics: ", topics);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            return hits2VOs(response, "topic");
        } catch (IOException e) {
            log.error("==> [话题召回] ES 查询异常: ", e);
            return Lists.newArrayList();
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 将 ES SearchHit 转换为 VO 列表
     */
    private List<RecommendNoteRspVO> hits2VOs(SearchResponse response, String source) {
        List<RecommendNoteRspVO> list = Lists.newArrayList();
        for (SearchHit hit : response.getHits()) {
            Map<String, Object> map = hit.getSourceAsMap();
            String updateTimeStr = (String) map.get(NoteIndexFields.UPDATE_TIME);
            LocalDateTime updateTime = updateTimeStr != null
                    ? LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S)
                    : LocalDateTime.now();

            RecommendNoteRspVO vo = RecommendNoteRspVO.builder()
                    .noteId(map.get(NoteIndexFields.ID) != null ? Long.valueOf(map.get(NoteIndexFields.ID).toString()) : null)
                    .cover((String) map.get(NoteIndexFields.COVER))
                    .title((String) map.get(NoteIndexFields.TITLE))
                    .avatar((String) map.get(NoteIndexFields.AVATAR))
                    .nickname((String) map.get(NoteIndexFields.NICKNAME))
                    .updateTime(DateUtils.formatRelativeTime(updateTime))
                    .likeTotal(NumberUtils.formatNumberString((Integer) map.get(NoteIndexFields.LIKE_TOTAL)))
                    .collectTotal(NumberUtils.formatNumberString((Integer) map.get(NoteIndexFields.COLLECT_TOTAL)))
                    .commentTotal(NumberUtils.formatNumberString((Integer) map.get(NoteIndexFields.COMMENT_TOTAL)))
                    .source(source)
                    .build();
            list.add(vo);
        }
        return list;
    }
}
