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
public class AgentChatRspVO {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 各 Agent 的执行结果
     */
    private List<AgentOutput> agentOutputs;

    /**
     * 最终合并结果（由 Orchestrator 汇总）
     */
    private String finalResult;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AgentOutput {
        private String agentName;
        private String output;
        private boolean success;
        private String errorMessage;
    }
}
