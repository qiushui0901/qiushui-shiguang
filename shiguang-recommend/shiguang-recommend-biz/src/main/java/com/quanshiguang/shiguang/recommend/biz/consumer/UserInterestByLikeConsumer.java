package com.quanshiguang.shiguang.recommend.biz.consumer;

import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.shiguang.recommend.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.recommend.biz.model.dto.LikeUnlikeNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 监听点赞/取消点赞事件，更新用户兴趣话题 ZSet
 * 点赞 -> 对应笔记的 topic 权重 +1；取消点赞 -> -1
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "shiguang_recommend_group_LikeUnlikeTopic",
        topic = "LikeUnlikeTopic"
)
@Slf4j
public class UserInterestByLikeConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public void onMessage(String body) {
        LikeUnlikeNoteMqDTO dto = JsonUtils.parseObject(body, LikeUnlikeNoteMqDTO.class);
        if (dto == null || dto.getUserId() == null || dto.getNoteId() == null) return;

        // 从 ES 获取笔记的 topic
        String topic = getNoteTopicFromEs(dto.getNoteId());
        if (topic == null) return;

        String topicKey = RedisKeyConstants.buildUserInterestTopicKey(dto.getUserId());
        double delta = (dto.getType() != null && dto.getType() == 1) ? 1.0 : -1.0;

        // ZSet: key=用户兴趣话题, member=topic, score=兴趣权重
        redisTemplate.opsForZSet().incrementScore(topicKey, topic, delta);
        // 设置过期时间（7天），防止冷用户数据堆积
        redisTemplate.expire(topicKey, 7, TimeUnit.DAYS);

        log.info("==> [用户兴趣] userId={}, topic={}, delta={}", dto.getUserId(), topic, delta);
    }

    private String getNoteTopicFromEs(Long noteId) {
        try {
            GetRequest req = new GetRequest("note", String.valueOf(noteId));
            req.fetchSourceContext(
                    new org.elasticsearch.search.fetch.subphase.FetchSourceContext(true, new String[]{"topic"}, null));
            GetResponse resp = restHighLevelClient.get(req, RequestOptions.DEFAULT);
            if (!resp.isExists()) return null;
            Map<String, Object> source = resp.getSourceAsMap();
            return source != null ? (String) source.get("topic") : null;
        } catch (IOException e) {
            log.warn("==> [用户兴趣] 获取笔记 topic 失败, noteId={}", noteId, e);
            return null;
        }
    }
}
