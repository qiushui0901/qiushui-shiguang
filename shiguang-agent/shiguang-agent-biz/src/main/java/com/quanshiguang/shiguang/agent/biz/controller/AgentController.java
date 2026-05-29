package com.quanshiguang.shiguang.agent.biz.controller;

import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.agent.biz.model.vo.AgentChatReqVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.AgentChatRspVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.SearchSummaryReqVO;
import com.quanshiguang.shiguang.agent.biz.model.vo.SearchSummaryRspVO;
import com.quanshiguang.shiguang.agent.biz.service.AgentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentService agentService;

    @PostMapping("/chat")
    public Response<AgentChatRspVO> chat(@Valid @RequestBody AgentChatReqVO req) {
        return Response.success(agentService.chat(req));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AgentChatReqVO req) {
        SseEmitter emitter = new SseEmitter(60_000L);
        agentService.chatStream(req, emitter);
        return emitter;
    }

    @PostMapping("/search/summary")
    public Response<SearchSummaryRspVO> searchSummary(@Valid @RequestBody SearchSummaryReqVO req) {
        return Response.success(agentService.searchSummary(req));
    }
}
