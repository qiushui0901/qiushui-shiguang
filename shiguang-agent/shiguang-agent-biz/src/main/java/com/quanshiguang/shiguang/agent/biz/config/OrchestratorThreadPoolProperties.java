package com.quanshiguang.shiguang.agent.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.orchestrator.thread-pool")
public class OrchestratorThreadPoolProperties {

    private int coreSize = 4;

    private int maxSize = 8;

    private int queueCapacity = 100;

    private int keepAliveSeconds = 60;

    private String rejectedHandler = "caller-runs";
}
