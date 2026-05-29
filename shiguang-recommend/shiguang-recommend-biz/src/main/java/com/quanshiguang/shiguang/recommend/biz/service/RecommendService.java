package com.quanshiguang.shiguang.recommend.biz.service;

import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.shiguang.recommend.biz.model.vo.RecommendNoteReqVO;
import com.quanshiguang.shiguang.recommend.biz.model.vo.RecommendNoteRspVO;

public interface RecommendService {

    /**
     * 个性化推荐信息流
     */
    PageResponse<RecommendNoteRspVO> recommend(RecommendNoteReqVO req);
}
