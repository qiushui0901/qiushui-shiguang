package com.quanshiguang.shiguang.search.biz.model.vo;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TrendingNoteReqVO {

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;

    /**
     * 笔记类型：null=综合 / 0=图文 / 1=视频
     */
    private Integer type;

    /**
     * 时间范围：null=不限 / 0=一天内 / 1=一周内 / 2=半年内
     */
    private Integer publishTimeRange;
}
