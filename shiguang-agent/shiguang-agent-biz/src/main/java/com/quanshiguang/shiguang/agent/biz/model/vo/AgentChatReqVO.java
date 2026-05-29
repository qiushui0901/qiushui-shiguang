package com.quanshiguang.shiguang.agent.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentChatReqVO {

    @NotBlank(message = "输入内容不能为空")
    private String input;

    /**
     * 工作流类型：sequential=顺序 / parallel=并行 / hybrid=混合 / conditional=条件
     */
    private String workflow = "hybrid";

    /**
     * 会话 ID（多轮对话时传入，为空则新建）
     */
    private String sessionId;
}
