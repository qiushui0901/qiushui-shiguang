package com.quanshiguang.shiguang.benchmark;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.*;

public class RedisLuaVsMultiStepBenchmark {

    static final String REDIS_HOST = "localhost";
    static final int REDIS_PORT = 6379;
    static final int WARMUP = 3;
    static final int RUNS = 10;
    static final int OPS_PER_ROUND = 10_000;

    static final String FOLLOW_CHECK_AND_ADD_LUA =
            "local userId = KEYS[1]\n" +
            "local targetUserId = KEYS[2]\n" +
            "local followingKey = ARGV[1]\n" +
            "local fansKey = ARGV[2]\n" +
            "local score = ARGV[3]\n" +
            "local exists = redis.call('ZSCORE', followingKey, targetUserId)\n" +
            "if exists then\n" +
            "    return 0\n" +
            "end\n" +
            "redis.call('ZADD', followingKey, score, targetUserId)\n" +
            "redis.call('ZADD', fansKey, score, userId)\n" +
            "return 1\n";

    public static void main(String[] args) {
        Jedis jedis;
        try {
            jedis = new Jedis(REDIS_HOST, REDIS_PORT);
            jedis.ping();
        } catch (Exception e) {
            System.out.println("=== Redis 未启动，跳过压测 ===");
            System.out.println("启动方式: redis-server");
            System.out.println("或使用 Docker: docker run -d -p 6379:6379 redis");
            return;
        }

        System.out.println("=== Redis Lua 脚本 vs 多步操作 压测 ===");
        System.out.printf("Redis: %s:%d\n", REDIS_HOST, REDIS_PORT);
        System.out.printf("每轮操作数: %,d\n\n", OPS_PER_ROUND);

        try {
            // === 1. 关注操作: Lua 原子 vs 多步 ===
            benchmarkFollow(jedis);

            // === 2. Pipeline 批量 vs 逐条 ===
            benchmarkPipeline(jedis);

            // === 3. ZSET 操作: Lua vs 多步 ===
            benchmarkZsetOps(jedis);

        } finally {
            // 清理测试数据
            try {
                Set<String> keys = jedis.keys("bench:*");
                if (!keys.isEmpty()) jedis.del(keys.toArray(new String[0]));
            } catch (Exception ignored) {}
            jedis.close();
        }
    }

    static void benchmarkFollow(Jedis jedis) {
        System.out.println("=== 关注操作: Lua 原子脚本 vs 应用层多步操作 ===");

        String luaSha = jedis.scriptLoad(FOLLOW_CHECK_AND_ADD_LUA);

        // Lua 脚本方式
        long luaTotal = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPS_PER_ROUND; i++) {
                long userId = 1000L + i;
                long targetId = 2000L + i;
                jedis.evalsha(luaSha, 2,
                        String.valueOf(userId), String.valueOf(targetId),
                        "bench:following:" + userId, "bench:fans:" + targetId,
                        String.valueOf(System.currentTimeMillis()));
            }
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) luaTotal += elapsed;
        }
        long luaAvgMs = luaTotal / RUNS / 1_000_000;

        // 清理
        jedis.del(jedis.keys("bench:following:*").toArray(new String[0]));
        jedis.del(jedis.keys("bench:fans:*").toArray(new String[0]));

        // 多步方式: ZSCORE + ZADD + ZADD
        long multiTotal = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPS_PER_ROUND; i++) {
                long userId = 1000L + i;
                long targetId = 2000L + i;
                String followingKey = "bench:following:" + userId;
                String fansKey = "bench:fans:" + targetId;
                Double exists = jedis.zscore(followingKey, String.valueOf(targetId));
                if (exists == null) {
                    jedis.zadd(followingKey, System.currentTimeMillis(), String.valueOf(targetId));
                    jedis.zadd(fansKey, System.currentTimeMillis(), String.valueOf(userId));
                }
            }
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) multiTotal += elapsed;
        }
        long multiAvgMs = multiTotal / RUNS / 1_000_000;

        double reduction = (1.0 - (double) luaAvgMs / multiAvgMs) * 100;
        double speedup = (double) multiAvgMs / luaAvgMs;

        System.out.printf("[多步操作]  3次往返(ZSCORE+ZADD+ZADD)  RT: %d ms  单次: %.2f ms\n",
                multiAvgMs, (double) multiAvgMs / OPS_PER_ROUND);
        System.out.printf("[Lua脚本]   1次往返(原子执行)        RT: %d ms  单次: %.2f ms\n",
                luaAvgMs, (double) luaAvgMs / OPS_PER_ROUND);
        System.out.printf("RT 降幅: %.0f%%, 加速比: %.1fx\n\n", reduction, speedup);

        // 清理
        jedis.del(jedis.keys("bench:following:*").toArray(new String[0]));
        jedis.del(jedis.keys("bench:fans:*").toArray(new String[0]));
    }

    static void benchmarkPipeline(Jedis jedis) {
        System.out.println("=== Pipeline 批量 vs 逐条操作 ===");

        int batchSize = 100;

        // 逐条 SET
        long singleTotal = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPS_PER_ROUND; i++) {
                jedis.set("bench:single:" + i, "value_" + i);
            }
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) singleTotal += elapsed;
        }
        long singleAvgMs = singleTotal / RUNS / 1_000_000;

        // Pipeline 批量 SET
        long pipeTotal = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            Pipeline pipeline = jedis.pipelined();
            for (int i = 0; i < OPS_PER_ROUND; i++) {
                pipeline.set("bench:pipe:" + i, "value_" + i);
                if ((i + 1) % batchSize == 0) {
                    pipeline.sync();
                    pipeline = jedis.pipelined();
                }
            }
            pipeline.sync();
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) pipeTotal += elapsed;
        }
        long pipeAvgMs = pipeTotal / RUNS / 1_000_000;

        double speedup = (double) singleAvgMs / pipeAvgMs;

        System.out.printf("[逐条]   RT: %d ms  单次: %.3f ms\n",
                singleAvgMs, (double) singleAvgMs / OPS_PER_ROUND);
        System.out.printf("[Pipeline] RT: %d ms  单次: %.3f ms  (batch=%d)\n",
                pipeAvgMs, (double) pipeAvgMs / OPS_PER_ROUND, batchSize);
        System.out.printf("加速比: %.1fx\n\n", speedup);
    }

    static void benchmarkZsetOps(Jedis jedis) {
        System.out.println("=== ZSET 读取: ZRANGEBYSCORE 单次延迟 ===");

        String zsetKey = "bench:zset:test";
        for (int i = 0; i < 10_000; i++) {
            jedis.zadd(zsetKey, i, "member_" + i);
        }

        int queryCount = 50_000;
        Random random = new Random(42);

        long total = 0;
        for (int r = 0; r < WARMUP + RUNS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < queryCount; i++) {
                long min = random.nextInt(9000);
                jedis.zrangeByScore(zsetKey, min, min + 1000);
            }
            long elapsed = System.nanoTime() - start;
            if (r >= WARMUP) total += elapsed;
        }

        double avgMs = (double) total / RUNS / 1_000_000;
        double singleMs = avgMs / queryCount;

        System.out.printf("%,d 次 ZRANGEBYSCORE 总耗时: %.0f ms\n", queryCount, avgMs);
        System.out.printf("单次延迟: %.3f ms\n", singleMs);

        jedis.del(zsetKey);
    }
}
