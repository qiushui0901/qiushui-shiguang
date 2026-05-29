package com.quanshiguang.shiguang.agent.biz.config;

import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class AgentChatClientConfig {

    private final AgentModelProperties properties;

    public AgentChatClientConfig(AgentModelProperties properties) {
        this.properties = properties;
    }

    @Bean("understandingChatClient")
    public ChatClient understandingChatClient() {
        AgentModelProperties.ModelConfig cfg = properties.getUnderstanding();
        return ChatClient.builder(createModel(cfg)).build();
    }

    @Bean("moderationChatClient")
    public ChatClient moderationChatClient() {
        AgentModelProperties.ModelConfig cfg = properties.getModeration();
        return ChatClient.builder(createModel(cfg)).build();
    }

    @Bean("creativeChatClient")
    public ChatClient creativeChatClient() {
        AgentModelProperties.ModelConfig cfg = properties.getCreative();
        return ChatClient.builder(createModel(cfg)).build();
    }

    @Bean("searchChatClient")
    public ChatClient searchChatClient() {
        AgentModelProperties.ModelConfig cfg = properties.getSearch();
        return ChatClient.builder(createModel(cfg)).build();
    }

    private OpenAiChatModel createModel(AgentModelProperties.ModelConfig cfg) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(cfg.getModel())
                .temperature(cfg.getTemperature())
                .maxTokens(cfg.getMaxTokens())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "agent.model")
    public static class AgentModelProperties {

        private ModelConfig understanding = new ModelConfig();
        private ModelConfig moderation = new ModelConfig();
        private ModelConfig creative = new ModelConfig();
        private ModelConfig search = new ModelConfig();

        @Data
        public static class ModelConfig {
            private String baseUrl = "https://open.bigmodel.cn/api/paas";
            private String apiKey = "${ZHIPU_API_KEY:your-api-key-here}";
            private String model = "glm-4-flash";
            private double temperature = 0.7;
            private int maxTokens = 2048;
        }
    }
}
