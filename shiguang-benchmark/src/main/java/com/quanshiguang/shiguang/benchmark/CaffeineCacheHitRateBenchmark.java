package com.quanshiguang.shiguang.benchmark;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class CaffeineCacheHitRateBenchmark {

    static final int TOTAL_KEYS = 100_000;
    static final int HOT_KEYS = 5_000;
    static final int WARM_KEYS = 15_000;
    static final int QUERY_COUNT = 1_000_000;

    public static void main(String[] args) {
        System.out.println("=== Redis + Caffeine 二级缓存命中率压测 ===");
        System.out.printf("总 key 数: %,d (热: %,d, 温: %,d, 冷: %,d)\n",
                TOTAL_KEYS, HOT_KEYS, WARM_KEYS, TOTAL_KEYS - HOT_KEYS - WARM_KEYS);
        System.out.printf("查询次数: %,d\n\n", QUERY_COUNT);

        Random random = new Random(42);

        // === 生成查询序列 (Zipf 分布, 热点集中) ===
        int[] queries = generateZipfQueries(random);

        // === L1 Caffeine 本地缓存 ===
        Cache<Integer, String> l1Cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

        // === L2 模拟 Redis (HashMap) ===
        Map<Integer, String> l2Cache = new HashMap<>();
        for (int i = 0; i < TOTAL_KEYS; i++) {
            l2Cache.put(i, "value_" + i);
        }

        // === 无缓存 ===
        long start = System.nanoTime();
        long dbHits = 0;
        for (int key : queries) {
            dbHits++;
        }
        long noCacheNanos = System.nanoTime() - start;

        // === 仅 L2 (Redis) ===
        long l2Hits = 0, l2Misses = 0;
        start = System.nanoTime();
        for (int key : queries) {
            if (l2Cache.containsKey(key)) {
                l2Hits++;
            } else {
                l2Misses++;
            }
        }
        long l2OnlyNanos = System.nanoTime() - start;

        // === L1 + L2 二级缓存 ===
        long l1Hit = 0, l2Hit = 0, dbHit = 0;
        start = System.nanoTime();
        for (int key : queries) {
            String value = l1Cache.getIfPresent(key);
            if (value != null) {
                l1Hit++;
                continue;
            }
            value = l2Cache.get(key);
            if (value != null) {
                l2Hit++;
                l1Cache.put(key, value);
            } else {
                dbHit++;
                value = "value_" + key;
                l2Cache.put(key, value);
                l1Cache.put(key, value);
            }
        }
        long twoLevelNanos = System.nanoTime() - start;

        // === 结果 ===
        double l1HitRate = (double) l1Hit / QUERY_COUNT * 100;
        double l2HitRate = (double) l2Hit / QUERY_COUNT * 100;
        double dbHitRate = (double) dbHit / QUERY_COUNT * 100;
        double totalCacheHitRate = (double) (l1Hit + l2Hit) / QUERY_COUNT * 100;

        System.out.println("=== 二级缓存命中率 ===");
        System.out.printf("L1 (Caffeine) 命中: %,d (%.1f%%)\n", l1Hit, l1HitRate);
        System.out.printf("L2 (Redis)     命中: %,d (%.1f%%)\n", l2Hit, l2HitRate);
        System.out.printf("DB 回源        次数: %,d (%.1f%%)\n", dbHit, dbHitRate);
        System.out.printf("总缓存命中率: %.1f%%\n", totalCacheHitRate);
        System.out.printf("DB 查询量减少: %.0fx\n", (double) QUERY_COUNT / Math.max(dbHit, 1));

        System.out.println("\n=== RT 对比 ===");
        System.out.printf("[无缓存]     耗时: %,d ms\n", noCacheNanos / 1_000_000);
        System.out.printf("[仅L2]       耗时: %,d ms\n", l2OnlyNanos / 1_000_000);
        System.out.printf("[L1+L2]      耗时: %,d ms\n", twoLevelNanos / 1_000_000);

        // === 不同缓存大小下的命中率 ===
        System.out.println("\n=== L1 缓存大小 vs 命中率 ===");
        System.out.printf("%-15s %-10s %-10s %-10s %-10s\n",
                "L1 maxSize", "L1命中(%)", "L2命中(%)", "DB回源(%)", "总命中(%)");
        System.out.println("-".repeat(60));

        int[] cacheSizes = {1_000, 5_000, 10_000, 20_000, 50_000};
        for (int maxSize : cacheSizes) {
            Cache<Integer, String> c = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .build();

            long l1h = 0, l2h = 0, dbh = 0;
            for (int key : queries) {
                String v = c.getIfPresent(key);
                if (v != null) { l1h++; continue; }
                v = l2Cache.get(key);
                if (v != null) { l2h++; c.put(key, v); }
                else { dbh++; }
            }
            System.out.printf("%-,15d %-10.1f %-10.1f %-10.1f %-10.1f\n",
                    maxSize,
                    (double) l1h / QUERY_COUNT * 100,
                    (double) l2h / QUERY_COUNT * 100,
                    (double) dbh / QUERY_COUNT * 100,
                    (double) (l1h + l2h) / QUERY_COUNT * 100);
        }
    }

    static int[] generateZipfQueries(Random random) {
        int[] queries = new int[QUERY_COUNT];
        for (int i = 0; i < QUERY_COUNT; i++) {
            double u = random.nextDouble();
            int key;
            if (u < 0.80) {
                key = random.nextInt(HOT_KEYS);
            } else if (u < 0.95) {
                key = HOT_KEYS + random.nextInt(WARM_KEYS);
            } else {
                key = HOT_KEYS + WARM_KEYS + random.nextInt(TOTAL_KEYS - HOT_KEYS - WARM_KEYS);
            }
            queries[i] = key;
        }
        return queries;
    }
}
