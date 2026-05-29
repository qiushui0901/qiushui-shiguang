package com.quanshiguang.shiguang.recommend.biz.controller;

import com.quanshiguang.framework.common.response.PageResponse;
import com.quanshiguang.shiguang.recommend.biz.model.vo.RecommendNoteReqVO;
import com.quanshiguang.shiguang.recommend.biz.model.vo.RecommendNoteRspVO;
import com.quanshiguang.shiguang.recommend.biz.service.RecommendService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Resource
    private RecommendService recommendService;

    /**
     * 个性化推荐信息流
     * type: null/0=综合 / 1=热度 / 2=关注 / 3=话题
     */
    @PostMapping("/feed")
    public PageResponse<RecommendNoteRspVO> feed(@Valid @RequestBody RecommendNoteReqVO req) {
        return recommendService.recommend(req);
    }
}
