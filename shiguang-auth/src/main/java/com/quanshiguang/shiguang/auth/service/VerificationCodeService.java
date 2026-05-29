package com.quanshiguang.shiguang.auth.service;

import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.auth.model.vo.verificationcode.SendVerificationCodeReqVO;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: TODO
 **/
public interface VerificationCodeService {

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}
