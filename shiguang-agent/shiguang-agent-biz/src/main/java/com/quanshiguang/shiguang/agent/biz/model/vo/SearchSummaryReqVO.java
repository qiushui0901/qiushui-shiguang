package com.quanshiguang.shiguang.agent.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchSummaryReqVO {

    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    /**
     * 笔记类型：null=综合 / 0=图文 / 1=视频
     */
    private Integer type;

    /**
     * 是否需要详细总结：true=LLM生成详细推荐 / false=仅简单摘要
     */
    private Boolean detailed = true;
}
