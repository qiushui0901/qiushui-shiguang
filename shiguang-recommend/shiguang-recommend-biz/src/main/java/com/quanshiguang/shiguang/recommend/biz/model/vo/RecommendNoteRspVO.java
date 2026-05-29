package com.quanshiguang.shiguang.recommend.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecommendNoteRspVO {

    private Long noteId;
    private String cover;
    private String title;
    private String avatar;
    private String nickname;
    private String updateTime;
    private String likeTotal;
    private String collectTotal;
    private String commentTotal;
    /**
     * 推荐来源：trending=热度 / following=关注 / topic=话题 / cf=协同过滤
     */
    private String source;
}
