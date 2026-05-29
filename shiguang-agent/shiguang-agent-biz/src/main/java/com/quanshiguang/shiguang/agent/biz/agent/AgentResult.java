package com.quanshiguang.shiguang.agent.biz.agent;

/**
 * Agent 执行结果
 */
public record AgentResult(
        String agentName,
        String output,
        boolean success,
        String errorMessage
) {
    public static AgentResult success(String agentName, String output) {
        return new AgentResult(agentName, output, true, null);
    }

    public static AgentResult fail(String agentName, String errorMessage) {
        return new AgentResult(agentName, null, false, errorMessage);
    }
}
