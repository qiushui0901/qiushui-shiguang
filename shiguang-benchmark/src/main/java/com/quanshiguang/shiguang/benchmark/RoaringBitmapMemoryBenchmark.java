package com.quanshiguang.shiguang.benchmark;

import org.roaringbitmap.RoaringBitmap;

import java.util.HashSet;
import java.util.Random;

public class RoaringBitmapMemoryBenchmark {

    public static void main(String[] args) {
        int userCount = 10_000_000;
        System.out.println("=== RoaringBitmap vs HashSet 内存对比压测 ===");
        System.out.printf("用户量: %,d\n\n", userCount);

        Runtime runtime = Runtime.getRuntime();

        // === HashSet ===
        runGC();
        long beforeHashSet = runtime.totalMemory() - runtime.freeMemory();

        HashSet<Long> hashSet = new HashSet<>(userCount);
        for (long i = 1; i <= userCount; i++) {
            hashSet.add(i);
        }

        long afterHashSet = runtime.totalMemory() - runtime.freeMemory();
        long hashSetMemoryMB = (afterHashSet - beforeHashSet) / 1024 / 1024;

        System.out.printf("[HashSet<Long>]  内存占用: %,d MB\n", hashSetMemoryMB);

        // === RoaringBitmap ===
        runGC();
        long beforeRoaring = runtime.totalMemory() - runtime.freeMemory();

        RoaringBitmap roaringBitmap = new RoaringBitmap();
        for (int i = 1; i <= userCount; i++) {
            roaringBitmap.add(i);
        }
        roaringBitmap.runOptimize();

        long afterRoaring = runtime.totalMemory() - runtime.freeMemory();
        long roaringMemoryMB = (afterRoaring - beforeRoaring) / 1024 / 1024;

        System.out.printf("[RoaringBitmap]  内存占用: %,d MB\n", roaringMemoryMB);

        // === 结果 ===
        double savingPct = (1.0 - (double) roaringMemoryMB / hashSetMemoryMB) * 100;
        System.out.println("\n=== 结论 ===");
        System.out.printf("内存节省: %.1f%% (%,d MB -> %,d MB)\n", savingPct, hashSetMemoryMB, roaringMemoryMB);
        System.out.printf("压缩比: 1:%.0f\n", (double) hashSetMemoryMB / roaringMemoryMB);

        // === 查询性能 ===
        System.out.println("\n=== 查询性能对比 ===");
        Random random = new Random(42);
        int queryCount = 10_000_000;
        int[] queryIds = new int[queryCount];
        for (int i = 0; i < queryCount; i++) {
            queryIds[i] = random.nextInt(userCount) + 1;
        }

        long start = System.nanoTime();
        int hashHit = 0;
        for (int id : queryIds) {
            if (hashSet.contains((long) id)) hashHit++;
        }
        long hashQueryNanos = System.nanoTime() - start;

        start = System.nanoTime();
        int roaringHit = 0;
        for (int id : queryIds) {
            if (roaringBitmap.contains(id)) roaringHit++;
        }
        long roaringQueryNanos = System.nanoTime() - start;

        System.out.printf("[HashSet]       %,d 次查询耗时: %,d ms (QPS: %,d)\n",
                queryCount, hashQueryNanos / 1_000_000, (long) queryCount * 1_000_000_000L / hashQueryNanos);
        System.out.printf("[RoaringBitmap] %,d 次查询耗时: %,d ms (QPS: %,d)\n",
                queryCount, roaringQueryNanos / 1_000_000, (long) queryCount * 1_000_000_000L / roaringQueryNanos);

        // 稀疏数据场景
        System.out.println("\n=== 稀疏数据场景 (1000万用户中仅10万点赞) ===");
        hashSet = null;
        roaringBitmap = null;
        runGC();

        int sparseCount = 100_000;
        beforeHashSet = runtime.totalMemory() - runtime.freeMemory();
        HashSet<Long> sparseSet = new HashSet<>();
        for (int i = 0; i < sparseCount; i++) {
            sparseSet.add((long) random.nextInt(userCount));
        }
        afterHashSet = runtime.totalMemory() - runtime.freeMemory();
        long sparseSetMB = (afterHashSet - beforeHashSet) / 1024 / 1024;

        runGC();
        beforeRoaring = runtime.totalMemory() - runtime.freeMemory();
        RoaringBitmap sparseRoaring = new RoaringBitmap();
        for (int i = 0; i < sparseCount; i++) {
            sparseRoaring.add(random.nextInt(userCount));
        }
        sparseRoaring.runOptimize();
        afterRoaring = runtime.totalMemory() - runtime.freeMemory();
        long sparseRoaringMB = (afterRoaring - beforeRoaring) / 1024 / 1024;

        System.out.printf("[HashSet]       内存: %,d MB\n", sparseSetMB);
        System.out.printf("[RoaringBitmap] 内存: %,d MB\n", sparseRoaringMB);
        System.out.printf("内存节省: %.1f%%\n", (1.0 - (double) sparseRoaringMB / sparseSetMB) * 100);
    }

    private static void runGC() {
        System.gc();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }
}
