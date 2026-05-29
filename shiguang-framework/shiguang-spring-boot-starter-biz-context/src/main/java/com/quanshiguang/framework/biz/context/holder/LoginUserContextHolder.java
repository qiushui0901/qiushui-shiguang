package com.quanshiguang.framework.biz.context.holder;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * @author: 犬小哈
 * @date: 2024/4/9 18:19
 * @version: v1.0.0
 * @description: 登录用户上下文
 **/
public class LoginUserContextHolder {

    private static final ThreadLocal<Long> LOGIN_USER_CONTEXT_THREAD_LOCAL
            = new TransmittableThreadLocal<>();

    /**
     * 设置用户 ID
     *
     * @param userId
     */
    public static void setUserId(Long userId) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.set(userId);
    }

    /**
     * 获取用户 ID
     *
     * @return
     */
    public static Long getUserId() {
        return LOGIN_USER_CONTEXT_THREAD_LOCAL.get();
    }

    /**
     * 删除 ThreadLocal
     */
    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }

}
