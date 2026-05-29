package com.quanshiguang.shiguang.recommend.biz.constant;

/**
 * 推荐服务用到的 Redis Key 常量
 */
public class RedisKeyConstants {

    /**
     * 用户推荐结果缓存（已经过排序+去重的笔记 ID 列表）
     */
    private static final String RECOMMEND_FEED_KEY_PREFIX = "recommend:feed:";

    /**
     * 用户已曝光笔记 ID 集合（去重，避免重复推荐）
     */
    private static final String EXPOSURE_KEY_PREFIX = "recommend:exposure:";

    /**
     * 用户感兴趣的话题（统计自用户行为）
     */
    private static final String USER_INTEREST_TOPIC_KEY_PREFIX = "recommend:interest:topic:";

    /**
     * 复用 user-relation 服务的关注列表 ZSet（不能跨服务直接访问，仅用于参考）
     */
    public static final String USER_FOLLOWING_KEY_PREFIX = "following:";

    /**
     * 复用 note 服务的用户点赞 Bitmap（用于协同过滤）
     */
    public static final String RBITMAP_USER_LIKES_PREFIX = "rbitmap:note:likes:";

    public static String buildFeedKey(Long userId) {
        return RECOMMEND_FEED_KEY_PREFIX + userId;
    }

    public static String buildExposureKey(Long userId) {
        return EXPOSURE_KEY_PREFIX + userId;
    }

    public static String buildUserInterestTopicKey(Long userId) {
        return USER_INTEREST_TOPIC_KEY_PREFIX + userId;
    }

    public static String buildUserFollowingKey(Long userId) {
        return USER_FOLLOWING_KEY_PREFIX + userId;
    }

    public static String buildUserLikesBitmapKey(Long userId) {
        return RBITMAP_USER_LIKES_PREFIX + userId;
    }
}
