package com.quanshiguang.shiguang.recommend.biz.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LikeUnlikeNoteMqDTO {
    private Long userId;
    private Long noteId;
    private Integer type; // 0:取消点赞 1:点赞
    private Long noteCreatorId;
    private LocalDateTime createTime;
}
