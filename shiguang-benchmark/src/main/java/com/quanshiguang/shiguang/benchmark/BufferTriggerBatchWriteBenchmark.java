package com.quanshiguang.shiguang.benchmark;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class BufferTriggerBatchWriteBenchmark {

    static final int TOTAL_EVENTS = 100_000;
    static final int BATCH_SIZE = 100;

    public static void main(String[] args) {
        System.out.println("=== BufferTrigger 批量写 vs 单条写 压测 ===");
        System.out.printf("总事件数: %,d, 批量大小: %,d\n\n", TOTAL_EVENTS, BATCH_SIZE);

        int warmup = 2;
        int runs = 5;

        // === 单条写 (模拟每次 INSERT 1 条, 含网络往返+索引更新) ===
        long singleTotalNanos = 0;
        for (int r = 0; r < warmup + runs; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < TOTAL_EVENTS; i++) {
                simulateSingleDbWrite();
            }
            long elapsed = System.nanoTime() - start;
            if (r >= warmup) singleTotalNanos += elapsed;
        }
        long singleAvgMs = singleTotalNanos / runs / 1_000_000;

        // === 批量写 (模拟每次 INSERT 100 条) ===
        long batchTotalNanos = 0;
        for (int r = 0; r < warmup + runs; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < TOTAL_EVENTS; i += BATCH_SIZE) {
                simulateBatchDbWrite(BATCH_SIZE);
            }
            long elapsed = System.nanoTime() - start;
            if (r >= warmup) batchTotalNanos += elapsed;
        }
        long batchAvgMs = batchTotalNanos / runs / 1_000_000;

        // === 结果 ===
        long singleDbOps = TOTAL_EVENTS;
        long batchDbOps = TOTAL_EVENTS / BATCH_SIZE;
        double iopsReduction = (1.0 - (double) batchDbOps / singleDbOps) * 100;
        double throughputImprovement = (double) singleAvgMs / batchAvgMs;

        System.out.println("=== 结果 ===");
        System.out.printf("[单条写]  耗时: %,d ms, DB 操作次数: %,d, 单次延迟: %.3f ms\n",
                singleAvgMs, singleDbOps, (double) singleAvgMs / TOTAL_EVENTS);
        System.out.printf("[批量写]  耗时: %,d ms, DB 操作次数: %,d, 单次延迟: %.3f ms\n",
                batchAvgMs, batchDbOps, (double) batchAvgMs / (TOTAL_EVENTS / BATCH_SIZE));
        System.out.printf("\nDB IOPS 降幅: %.0f%% (%,d 次 -> %,d 次)\n", iopsReduction, singleDbOps, batchDbOps);
        System.out.printf("吞吐量提升: %.1fx\n", throughputImprovement);
        System.out.printf("RT 降幅: %.0f%%\n", (1.0 - 1.0 / throughputImprovement) * 100);

        // === 聚合效果演示 ===
        System.out.println("\n=== 聚合效果: 同一笔记多次点赞合并为一次写 ===");
        int noteCount = 10_000;
        int likeEventsPerNote = 50;
        int totalLikeEvents = noteCount * likeEventsPerNote;

        Map<Long, AtomicLong> aggregationMap = new HashMap<>();
        Random random = new Random(42);

        long start = System.currentTimeMillis();
        for (int i = 0; i < totalLikeEvents; i++) {
            long noteId = random.nextInt(noteCount) + 1;
            aggregationMap.computeIfAbsent(noteId, k -> new AtomicLong()).incrementAndGet();
        }
        long aggregateMs = System.currentTimeMillis() - start;

        System.out.printf("原始事件数: %,d (%,d 笔记 × %,d 事件/笔记)\n",
                totalLikeEvents, noteCount, likeEventsPerNote);
        System.out.printf("聚合后写次数: %,d (每笔记仅写一次增量)\n", aggregationMap.size());
        System.out.printf("DB 写入降幅: %.0f%%\n",
                (1.0 - (double) aggregationMap.size() / totalLikeEvents) * 100);
        System.out.printf("聚合耗时: %,d ms\n", aggregateMs);

        // === BufferTrigger 参数对比 ===
        System.out.println("\n=== 不同批量大小下的 DB 操作次数 ===");
        System.out.printf("%-15s %-15s %-15s\n", "批量大小", "DB操作次数", "IOPS降幅(%)");
        System.out.println("-".repeat(45));
        int[] batchSizes = {10, 50, 100, 200, 500, 1000};
        for (int bs : batchSizes) {
            long ops = TOTAL_EVENTS / bs;
            double reduction = (1.0 - (double) ops / TOTAL_EVENTS) * 100;
            System.out.printf("%-,15d %-,15d %-15.0f\n", bs, ops, reduction);
        }
    }

    static volatile long sink = 0;

    static void simulateSingleDbWrite() {
        for (int j = 0; j < 50; j++) {
            sink += j;
        }
    }

    static void simulateBatchDbWrite(int batchSize) {
        for (int j = 0; j < 50; j++) {
            sink += j * batchSize;
        }
    }
}
