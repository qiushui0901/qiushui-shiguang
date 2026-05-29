package com.quanshiguang.shiguang.count.biz.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.quanshiguang.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.shiguang.count.biz.service.UserCountService;
import com.quanshiguang.shiguang.count.dto.FindUserCountsByIdReqDTO;
import com.quanshiguang.shiguang.count.dto.FindUserCountsByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author: 犬小哈
 * @date: 2024/4/4 13:22
 * @version: v1.0.0
 * @description: 用户维度计数
 **/
@RestController
@RequestMapping("/count")
@Slf4j
public class UserCountController {

    @Resource
    private UserCountService userCountService;

    @PostMapping(value = "/user/data")
    @ApiOperationLog(description = "获取用户计数数据")
    @SentinelResource(value = "findUserCountData4Controller", blockHandler = "blockHandler4findUserCountData")
    public Response<FindUserCountsByIdRspDTO> findUserCountData(@Validated @RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        // 模拟接口随机发生异常，抛出概率约为 50%
        if (Math.random() > 0.5) {
            throw new RuntimeException();
        }

        return userCountService.findUserCountData(findUserCountsByIdReqDTO);
    }

    /**
     * blockHandler 函数，原方法调用被限流/降级/系统保护的时候调用
     * 注意, 需要包含限流方法的所有参数，和 BlockException 参数
     * @param findUserCountsByIdReqDTO
     * @param blockException
     */
    public Response<FindUserCountsByIdRspDTO> blockHandler4findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO, BlockException blockException) {
        log.warn("## /count/user/count 接口被限流: {}", JsonUtils.toJsonString(findUserCountsByIdReqDTO));

        return Response.success(FindUserCountsByIdRspDTO.builder()
                .userId(findUserCountsByIdReqDTO.getUserId())
                .collectTotal(0L)
                .fansTotal(0L)
                .followingTotal(0L)
                .likeTotal(0L)
                .noteTotal(0L)
                .build());
    }

}
