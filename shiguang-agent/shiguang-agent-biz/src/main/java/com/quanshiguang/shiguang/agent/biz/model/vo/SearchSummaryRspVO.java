package com.quanshiguang.shiguang.agent.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SearchSummaryRspVO {

    /**
     * AI 生成的总结文本
     */
    private String summary;

    /**
     * 按热度排序的笔记列表
     */
    private List<NoteItem> notes;

    /**
     * 相关话题推荐
     */
    private List<String> relatedTopics;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class NoteItem {
        private Long noteId;
        private String title;
        private String cover;
        private String nickname;
        private String avatar;
        private Integer likeTotal;
        private Integer collectTotal;
        private Integer commentTotal;
        private String topic;
        private String createTime;
    }
}
