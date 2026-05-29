package com.quanshiguang.shiguang.user.biz.consumer;

import com.quanshiguang.shiguang.user.biz.constant.MQConstants;
import com.quanshiguang.shiguang.user.biz.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author: 犬小哈
 * @url: www.quanshiguang.com
 * @date: 2024/8/30 11:27
 * @description: 延时删除 Redis 用户缓存
 **/
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "shiguang_group_" + MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE, // Group
        topic = MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE // 消费的主题 Topic
        )
public class DelayDeleteUserRedisCacheConsumer implements RocketMQListener<String>  {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        Long userId = Long.valueOf(body);
        log.info("## 延迟消息消费成功, userId: {}", userId);

        // 删除 Redis 用户缓存
        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);
        String userProfileRedisKey = RedisKeyConstants.buildUserProfileKey(userId);
        // 批量删除
        redisTemplate.delete(Arrays.asList(userInfoRedisKey, userProfileRedisKey));
    }
}
