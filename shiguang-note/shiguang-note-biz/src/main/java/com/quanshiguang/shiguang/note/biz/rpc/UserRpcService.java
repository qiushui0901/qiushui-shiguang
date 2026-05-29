package com.quanshiguang.shiguang.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.user.api.UserFeignApi;
import com.quanshiguang.shiguang.user.dto.req.FindUserByIdReqDTO;
import com.quanshiguang.shiguang.user.dto.req.FindUsersByIdsReqDTO;
import com.quanshiguang.shiguang.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * @author: 犬小哈
 * @date: 2024/4/13 23:29
 * @version: v1.0.0
 * @description: 用户服务
 **/
@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 查询用户信息
     * @param userId
     * @return
     */
    public FindUserByIdRspDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }

        return response.getData();
    }



}
