package com.quanshiguang.shiguang.benchmark;

public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║        「拾光」项目 性能压测基准 - 全量执行          ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        String[][] benchmarks = {
                {"1. RoaringBitmap 内存对比", "RoaringBitmapMemoryBenchmark"},
                {"2. BufferTrigger 批量写", "BufferTriggerBatchWriteBenchmark"},
                {"3. CompletableFuture 并行", "CompletableFutureParallelBenchmark"},
                {"4. Caffeine 二级缓存命中率", "CaffeineCacheHitRateBenchmark"},
                {"5. Gauss 时间衰减效果", "GaussDecayBenchmark"},
                {"6. Redis Lua vs 多步 (需Redis)", "RedisLuaVsMultiStepBenchmark"},
                {"7. ES Suggester 延迟 (需ES)", "ElasticsearchSuggestBenchmark"},
        };

        if (args.length > 0) {
            int idx = Integer.parseInt(args[0]) - 1;
            if (idx >= 0 && idx < benchmarks.length) {
                run(benchmarks[idx][0], benchmarks[idx][1]);
                return;
            }
        }

        for (String[] bm : benchmarks) {
            run(bm[0], bm[1]);
        }

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║                    压测全部完成                      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    static void run(String name, String className) {
        System.out.printf("\n>>>>>>> %s <<<<<<<\n", name);
        try {
            Class<?> clazz = Class.forName("com.quanshiguang.shiguang.benchmark." + className);
            clazz.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
        } catch (Exception e) {
            System.out.println("执行失败: " + e.getMessage());
        }
        System.out.println();
    }
}
