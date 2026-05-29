package com.quanshiguang.shiguang.agent.biz.service;

import com.quanshiguang.shiguang.agent.biz.model.vo.AgentChatReqVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.AgentChatRspVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.SearchSummaryReqVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.SearchSummaryRspVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

public interface AgentService {

    AgentChatRspVO chat(AgentChatReqVO req);

    void chatStream(AgentChatReqVO req, SseEmitter emitter);

    SearchSummaryRspVO searchSummary(SearchSummaryReqVO req);
}
