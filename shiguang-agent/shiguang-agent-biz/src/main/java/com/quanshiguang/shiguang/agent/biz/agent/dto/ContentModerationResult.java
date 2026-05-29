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
public class ContentModerationResult {

    private Boolean approved;

    @JsonProperty("risk_level")
    private String riskLevel;

    private List<String> issues;

    private String suggestion;
}
