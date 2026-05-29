package com.quanshiguang.shiguang.search.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuggestNoteReqVO {

    @NotBlank(message = "搜索建议关键词不能为空")
    private String keyword;

    /**
     * 返回建议条数，默认 10
     */
    private Integer size = 10;
}
