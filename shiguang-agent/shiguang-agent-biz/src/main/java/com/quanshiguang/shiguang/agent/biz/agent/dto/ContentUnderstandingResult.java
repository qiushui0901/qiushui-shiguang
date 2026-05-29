package com.quanshiguang.shiguang.agent.biz.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContentUnderstandingResult {

    private List<String> tags;

    private String summary;

    private String topic;

    private String category;

    private String sentiment;
}
