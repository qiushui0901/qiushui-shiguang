package com.quanshiguang.shiguang.recommend.biz.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollectUnCollectNoteMqDTO {
    private Long userId;
    private Long noteId;
    private Integer type; // 0:取消收藏 1:收藏
    private Long noteCreatorId;
    private LocalDateTime createTime;
}
