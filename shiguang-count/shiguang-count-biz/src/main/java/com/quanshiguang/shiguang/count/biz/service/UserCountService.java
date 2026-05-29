package com.quanshiguang.shiguang.count.biz.service;

import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.count.dto.FindUserCountsByIdReqDTO;
import com.quanshiguang.shiguang.count.dto.FindUserCountsByIdRspDTO;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: 用户计数业务
 **/
public interface UserCountService {

    /**
     * 查询用户相关计数
     * @param findUserCountsByIdReqDTO
     * @return
     */
    Response<FindUserCountsByIdRspDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);
}
