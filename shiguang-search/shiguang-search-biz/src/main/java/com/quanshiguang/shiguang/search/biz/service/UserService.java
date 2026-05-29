package com.quanshiguang.shiguang.search.biz.service;

import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.dto.RebuildUserDocumentReqDTO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchUserReqVO;
import com.quanshiguang.shiguang.search.biz.model.vo.SearchUserRspVO;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: 用户搜索业务
 **/
public interface UserService {

    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);

    /**
     * 重建用户文档
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);
}
