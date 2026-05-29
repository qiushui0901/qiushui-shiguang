package com.quanshiguang.shiguang.agent.biz.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreativeAssistantResult {

    @JsonProperty("title_suggestions")
    private List<String> titleSuggestions;

    @JsonProperty("content_enhancement")
    private String contentEnhancement;

    @JsonProperty("recommended_topics")
    private List<String> recommendedTopics;

    @JsonProperty("writing_tips")
    private String writingTips;
}
