package com.quanshiguang.shiguang.benchmark;

import java.util.concurrent.*;
import java.util.*;

public class CompletableFutureParallelBenchmark {

    static final int DOWNSTREAM_COUNT = 3;
    static final int DOWNSTREAM_LATENCY_MS = 50;
    static final int WARMUP = 5;
    static final int RUNS = 20;

    public static void main(String[] args) {
        System.out.println("=== CompletableFuture 串行 vs 并行 压测 ===");
        System.out.printf("下游服务数: %d, 单次延迟: %dms\n\n", DOWNSTREAM_COUNT, DOWNSTREAM_LATENCY_MS);

        ExecutorService executor = Executors.newFixedThreadPool(DOWNSTREAM_COUNT);

        // === 串行调用 ===
        long serialTotal = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            serialCall();
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) serialTotal += elapsed;
        }
        long serialAvgMs = serialTotal / RUNS / 1_000_000;

        // === 并行调用 ===
        long parallelTotal = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            parallelCall(executor);
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) parallelTotal += elapsed;
        }
        long parallelAvgMs = parallelTotal / RUNS / 1_000_000;

        // === 结果 ===
        double reduction = (1.0 - (double) parallelAvgMs / serialAvgMs) * 100;

        System.out.println("=== 结果 ===");
        System.out.printf("[串行]  RT: %dms (理论值: %dms = %d × %dms)\n",
                serialAvgMs, DOWNSTREAM_COUNT * DOWNSTREAM_LATENCY_MS, DOWNSTREAM_COUNT, DOWNSTREAM_LATENCY_MS);
        System.out.printf("[并行]  RT: %dms (理论值: %dms = max(%dms))\n",
                parallelAvgMs, DOWNSTREAM_LATENCY_MS, DOWNSTREAM_LATENCY_MS);
        System.out.printf("RT 降幅: %.0f%%\n", reduction);

        // === 不同下游延迟对比 ===
        System.out.println("\n=== 不同下游延迟下的串行 vs 并行 ===");
        System.out.printf("%-12s %-12s %-12s %-10s\n", "单次延迟(ms)", "串行RT(ms)", "并行RT(ms)", "降幅(%)");
        System.out.println("-".repeat(50));

        int[] latencies = {10, 20, 50, 100, 200};
        for (int latency : latencies) {
            long sTotal = 0, pTotal = 0;
            for (int r = 0; r < WARMUP + RUNS; r++) {
                long s1 = System.nanoTime();
                serialCallWithLatency(latency);
                sTotal += (r >= WARMUP) ? (System.nanoTime() - s1) : 0;

                long p1 = System.nanoTime();
                parallelCallWithLatency(executor, latency);
                pTotal += (r >= WARMUP) ? (System.nanoTime() - p1) : 0;
            }
            long sAvg = sTotal / RUNS / 1_000_000;
            long pAvg = pTotal / RUNS / 1_000_000;
            double rdc = (1.0 - (double) pAvg / sAvg) * 100;
            System.out.printf("%-12d %-12d %-12d %-10.0f\n", latency, sAvg, pAvg, rdc);
        }

        // === 下游延迟不均匀场景 ===
        System.out.println("\n=== 下游延迟不均匀场景 ===");
        int[] unevenLatencies = {30, 50, 80};
        System.out.printf("三个下游延迟: %dms, %dms, %dms\n", unevenLatencies[0], unevenLatencies[1], unevenLatencies[2]);
        System.out.printf("串行理论RT: %dms\n", unevenLatencies[0] + unevenLatencies[1] + unevenLatencies[2]);
        System.out.printf("并行理论RT: %dms (取 max)\n", Arrays.stream(unevenLatencies).max().getAsInt());
        System.out.printf("RT 降幅: %.0f%%\n",
                (1.0 - (double) Arrays.stream(unevenLatencies).max().getAsInt()
                        / Arrays.stream(unevenLatencies).sum()) * 100);

        executor.shutdown();
    }

    static void serialCall() {
        serialCallWithLatency(DOWNSTREAM_LATENCY_MS);
    }

    static void serialCallWithLatency(int latencyMs) {
        for (int i = 0; i < DOWNSTREAM_COUNT; i++) {
            mockDownstream(latencyMs);
        }
    }

    static void parallelCall(ExecutorService executor) {
        parallelCallWithLatency(executor, DOWNSTREAM_LATENCY_MS);
    }

    static void parallelCallWithLatency(ExecutorService executor, int latencyMs) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < DOWNSTREAM_COUNT; i++) {
            futures.add(CompletableFuture.runAsync(
                    () -> mockDownstream(latencyMs), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    static void mockDownstream(int latencyMs) {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException ignored) {}
    }
}
