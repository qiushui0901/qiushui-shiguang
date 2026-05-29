package com.quanshiguang.shiguang.recommend.biz.model.vo;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RecommendNoteReqVO {

    /**
     * 页码
     */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;

    /**
     * 推荐类型：null 或 0=综合（热度+关注+话题）/ 1=热度 / 2=关注 / 3=话题
     */
    private Integer type;
}
