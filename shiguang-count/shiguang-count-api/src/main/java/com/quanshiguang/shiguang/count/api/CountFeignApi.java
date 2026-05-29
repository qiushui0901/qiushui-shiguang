package com.quanshiguang.shiguang.count.api;

import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.count.constant.ApiConstants;
import com.quanshiguang.shiguang.count.dto.FindNoteCountsByIdRspDTO;
import com.quanshiguang.shiguang.count.dto.FindNoteCountsByIdsReqDTO;
import com.quanshiguang.shiguang.count.dto.FindUserCountsByIdReqDTO;
import com.quanshiguang.shiguang.count.dto.FindUserCountsByIdRspDTO;
import com.quanshiguang.shiguang.count.fallback.CountFeignApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @author: 犬小哈
 * @date: 2024/4/13 22:56
 * @version: v1.0.0
 * @description: 计数服务 Feign 接口
 **/
@FeignClient(name = ApiConstants.SERVICE_NAME, fallback = CountFeignApiFallback.class)
//@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface CountFeignApi {

    String PREFIX = "/count";

    /**
     * 查询用户计数
     *
     * @param findUserCountsByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/user/data")
    Response<FindUserCountsByIdRspDTO> findUserCount(@RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);

    /**
     * 批量查询笔记计数
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/notes/data")
    Response<List<FindNoteCountsByIdRspDTO>> findNotesCount(@RequestBody FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);

}
