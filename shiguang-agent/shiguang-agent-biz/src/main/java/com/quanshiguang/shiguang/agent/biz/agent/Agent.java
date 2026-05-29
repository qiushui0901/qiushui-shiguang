package com.quanshiguang.shiguang.agent.biz.agent;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

public interface Agent {

    String getName();

    String getDescription();

    String execute(String userInput, AgentContext context);

    Flux<String> executeStream(String userInput, AgentContext context);

    ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient, String userInput, AgentContext context);
}
