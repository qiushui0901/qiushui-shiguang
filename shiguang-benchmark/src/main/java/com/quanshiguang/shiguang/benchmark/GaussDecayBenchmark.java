package com.quanshiguang.shiguang.benchmark;

import java.util.*;

public class GaussDecayBenchmark {

    static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    static final double DECAY = 0.5;

    public static void main(String[] args) {
        System.out.println("=== Gauss 时间衰减函数效果演示 ===");
        System.out.printf("参数: scale=7天, decay=0.5\n\n");

        System.out.printf("%-20s %-15s %-15s %-15s\n", "距今天数", "衰减权重", "原始热度", "加权热度");
        System.out.println("-".repeat(65));

        double baseScore = 1000.0;
        int[] daysAgo = {0, 1, 2, 3, 5, 7, 10, 14, 21, 30, 60, 90, 180, 365};

        for (int days : daysAgo) {
            double weight = gaussDecay(days, 7, DECAY);
            double weightedScore = baseScore * weight;
            System.out.printf("%-20d %-15.4f %-15.0f %-15.1f\n", days, weight, baseScore, weightedScore);
        }

        // === 对比: 有衰减 vs 无衰减的排序差异 ===
        System.out.println("\n=== 排序差异演示: 同一话题下不同笔记 ===");

        Random random = new Random(42);
        List<String> notes = new ArrayList<>();
        List<Double> scoresNoDecay = new ArrayList<>();
        List<Double> scoresWithDecay = new ArrayList<>();

        String[] titles = {
                "Spring入门(3天前,500赞)", "Java面试(30天前,5000赞)", "Redis优化(1天前,200赞)",
                "MySQL索引(7天前,1000赞)", "Docker部署(14天前,3000赞)", "Go并发(2天前,800赞)",
                "K8s运维(60天前,8000赞)", "Vue3实战(5天前,600赞)"
        };
        int[] days = {3, 30, 1, 7, 14, 2, 60, 5};
        int[] likes = {500, 5000, 200, 1000, 3000, 800, 8000, 600};

        for (int i = 0; i < titles.length; i++) {
            notes.add(titles[i]);
            scoresNoDecay.add((double) likes[i]);
            scoresWithDecay.add(likes[i] * gaussDecay(days[i], 7, DECAY));
        }

        List<Integer> orderNoDecay = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) orderNoDecay.add(i);
        orderNoDecay.sort((a, b) -> Double.compare(scoresNoDecay.get(b), scoresNoDecay.get(a)));

        List<Integer> orderWithDecay = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) orderWithDecay.add(i);
        orderWithDecay.sort((a, b) -> Double.compare(scoresWithDecay.get(b), scoresWithDecay.get(a)));

        System.out.printf("\n%-5s %-30s %-15s\n", "排名", "[无衰减] 仅按热度", "加权热度");
        System.out.println("-".repeat(55));
        for (int rank = 0; rank < orderNoDecay.size(); rank++) {
            int idx = orderNoDecay.get(rank);
            System.out.printf("%-5d %-30s %-15.0f\n", rank + 1, notes.get(idx), scoresNoDecay.get(idx));
        }

        System.out.printf("\n%-5s %-30s %-15s\n", "排名", "[Gauss衰减] 热度×时间权重", "加权热度");
        System.out.println("-".repeat(55));
        for (int rank = 0; rank < orderWithDecay.size(); rank++) {
            int idx = orderWithDecay.get(rank);
            System.out.printf("%-5d %-30s %-15.1f\n", rank + 1, notes.get(idx), scoresWithDecay.get(idx));
        }

        System.out.println("\n=== 结论 ===");
        System.out.println("无衰减: 老笔记(K8s运维 8000赞)永远霸榜, 新内容难以露出");
        System.out.println("Gauss衰减: 新鲜内容获得加权, 7天外权重<0.5自然沉底, 兼顾热度与时效性");
    }

    static double gaussDecay(double daysAgo, double scale, double decay) {
        double sigma = scale / Math.sqrt(-2.0 * Math.log(decay));
        return Math.exp(-0.5 * Math.pow(daysAgo / sigma, 2));
    }
}
