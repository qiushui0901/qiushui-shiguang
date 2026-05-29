package com.quanshiguang.shiguang.user.biz.constant;

/**
 * @author: 犬小哈
 * @date: 2024/5/21 15:04
 * @version: v1.0.0
 * @description: TODO
 **/
public interface MQConstants {


    /**
     * Topic 主题：延迟双删 Redis 用户缓存
     */
    String TOPIC_DELAY_DELETE_USER_REDIS_CACHE = "DelayDeleteUserRedisCacheTopic";

}
