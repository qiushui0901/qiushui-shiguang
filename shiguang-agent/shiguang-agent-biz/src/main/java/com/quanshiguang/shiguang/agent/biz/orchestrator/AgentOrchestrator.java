package com.quanshiguang.shiguang.agent.biz.orchestrator;

import com.quanshiguang.shiguang.agent.biz.agent.Agent;
import com.quanshiguang.shiguang.agent.biz.agent.AgentContext;
import com.quanshiguang.shiguang.agent.biz.agent.AgentResult;
import com.quanshiguang.shiguang.agent.biz.config.OrchestratorThreadPoolProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class AgentOrchestrator {

    private final ExecutorService executor;

    public AgentOrchestrator(OrchestratorThreadPoolProperties props) {
        RejectedExecutionHandler handler = switch (props.getRejectedHandler()) {
            case "abort" -> new ThreadPoolExecutor.AbortPolicy();
            case "discard" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discard-oldest" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.CallerRunsPolicy();
        };

        this.executor = new ThreadPoolExecutor(
                props.getCoreSize(),
                props.getMaxSize(),
                props.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(props.getQueueCapacity()),
                handler
        );

        log.info("==> [Orchestrator] 线程池初始化: core={}, max={}, queue={}, rejectedHandler={}",
                props.getCoreSize(), props.getMaxSize(), props.getQueueCapacity(), props.getRejectedHandler());
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
        log.info("==> [Orchestrator] 线程池已关闭");
    }

    public List<AgentResult> executeSequential(List<Agent> agents, String userInput, AgentContext context) {
        List<AgentResult> results = new ArrayList<>();
        log.info("==> [Orchestrator] 顺序执行, agents={}", agents.stream().map(Agent::getName).toList());

        for (Agent agent : agents) {
            try {
                long start = System.currentTimeMillis();
                String output = agent.execute(userInput, context);
                long elapsed = System.currentTimeMillis() - start;

                AgentResult result = AgentResult.success(agent.getName(), output);
                results.add(result);
                context.addOutput(agent.getName(), output);

                log.info("==> [Orchestrator] Agent[{}] 完成, 耗时={}ms", agent.getName(), elapsed);
            } catch (Exception e) {
                log.error("==> [Orchestrator] Agent[{}] 执行异常: ", agent.getName(), e);
                results.add(AgentResult.fail(agent.getName(), e.getMessage()));
            }
        }
        return results;
    }

    public List<AgentResult> executeParallel(List<Agent> agents, String userInput, AgentContext context) {
        log.info("==> [Orchestrator] 并行执行, agents={}", agents.stream().map(Agent::getName).toList());

        List<Future<AgentResult>> futures = new ArrayList<>();
        for (Agent agent : agents) {
            futures.add(executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    String output = agent.execute(userInput, context);
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("==> [Orchestrator] Agent[{}] 完成, 耗时={}ms", agent.getName(), elapsed);
                    return AgentResult.success(agent.getName(), output);
                } catch (Exception e) {
                    log.error("==> [Orchestrator] Agent[{}] 执行异常: ", agent.getName(), e);
                    return AgentResult.fail(agent.getName(), e.getMessage());
                }
            }));
        }

        List<AgentResult> results = new ArrayList<>();
        for (Future<AgentResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.add(AgentResult.fail("unknown", "执行超时或异常: " + e.getMessage()));
            }
        }

        for (AgentResult r : results) {
            if (r.success()) {
                context.addOutput(r.agentName(), r.output());
            }
        }
        return results;
    }

    public List<AgentResult> executeHybrid(
            List<Agent> parallelAgents,
            List<Agent> sequentialAgents,
            String userInput,
            AgentContext context
    ) {
        log.info("==> [Orchestrator] 混合编排, parallel={}, sequential={}",
                parallelAgents.stream().map(Agent::getName).toList(),
                sequentialAgents.stream().map(Agent::getName).toList());

        List<AgentResult> results = new ArrayList<>(executeParallel(parallelAgents, userInput, context));
        results.addAll(executeSequential(sequentialAgents, userInput, context));

        return results;
    }

    public List<AgentResult> executeConditional(
            List<Agent> agents,
            String userInput,
            AgentContext context,
            java.util.function.Predicate<AgentResult> condition
    ) {
        List<AgentResult> results = new ArrayList<>();
        log.info("==> [Orchestrator] 条件编排, agents={}", agents.stream().map(Agent::getName).toList());

        for (Agent agent : agents) {
            try {
                String output = agent.execute(userInput, context);
                AgentResult result = AgentResult.success(agent.getName(), output);
                results.add(result);
                context.addOutput(agent.getName(), output);

                if (!condition.test(result)) {
                    log.info("==> [Orchestrator] 条件不满足，终止后续执行, lastAgent={}", agent.getName());
                    break;
                }
            } catch (Exception e) {
                results.add(AgentResult.fail(agent.getName(), e.getMessage()));
            }
        }
        return results;
    }
}
