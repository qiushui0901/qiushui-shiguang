package com.quanshiguang.shiguang.recommend.biz.consumer;

import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.shiguang.recommend.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.recommend.biz.model.dto.CollectUnCollectNoteMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 监听收藏/取消收藏事件，更新用户兴趣话题 ZSet
 * 收藏信号比点赞更强，权重 +2
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "shiguang_recommend_group_CollectUnCollectTopic",
        topic = "CollectUnCollectTopic"
)
@Slf4j
public class UserInterestByCollectConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public void onMessage(String body) {
        CollectUnCollectNoteMqDTO dto = JsonUtils.parseObject(body, CollectUnCollectNoteMqDTO.class);
        if (dto == null || dto.getUserId() == null || dto.getNoteId() == null) return;

        String topic = getNoteTopicFromEs(dto.getNoteId());
        if (topic == null) return;

        String topicKey = RedisKeyConstants.buildUserInterestTopicKey(dto.getUserId());
        // 收藏权重是点赞的 2 倍
        double delta = (dto.getType() != null && dto.getType() == 1) ? 2.0 : -2.0;

        redisTemplate.opsForZSet().incrementScore(topicKey, topic, delta);
        redisTemplate.expire(topicKey, 7, TimeUnit.DAYS);

        log.info("==> [用户兴趣] userId={}, topic={}, delta={}", dto.getUserId(), topic, delta);
    }

    private String getNoteTopicFromEs(Long noteId) {
        try {
            GetRequest req = new GetRequest("note", String.valueOf(noteId));
            req.fetchSourceContext(new FetchSourceContext(true, new String[]{"topic"}, null));
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
