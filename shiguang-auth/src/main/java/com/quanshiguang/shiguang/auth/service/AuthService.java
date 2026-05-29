package com.quanshiguang.shiguang.auth.service;

import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.auth.model.vo.user.UpdatePasswordReqVO;
import com.quanshiguang.shiguang.auth.model.vo.user.UserLoginReqVO;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: TODO
 **/
public interface AuthService {

    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    /**
     * 退出登录
     * @return
     */
    Response<?> logout();

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
