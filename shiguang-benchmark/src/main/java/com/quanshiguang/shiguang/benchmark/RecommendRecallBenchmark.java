package com.quanshiguang.shiguang.benchmark;

import java.util.*;
import java.util.stream.Collectors;

public class RecommendRecallBenchmark {

    static final int TOTAL_NOTES = 50_000;
    static final int TOTAL_USERS = 10_000;
    static final int TOTAL_TOPICS = 20;
    static final int FOLLOWING_PER_USER = 100;
    static final int FEED_SIZE = 50;
    static final int SAMPLE_USERS = 200;
    static final int CTR_SAMPLE_USERS = 1_000;

    static final String[] TOPIC_NAMES = {
            "穿搭", "美食", "旅行", "数码", "家居", "健身", "美妆", "摄影",
            "读书", "职场", "萌宠", "手工", "音乐", "影视", "游戏",
            "育儿", "养生", "汽车", "学习", "理财"
    };

    static Random random = new Random(42);

    public static void main(String[] args) {
        System.out.println("=== 个性化推荐系统 召回指标压测 ===");
        System.out.printf("笔记池: %,d, 用户: %,d, 话题: %d, Feed: %d\n\n",
                TOTAL_NOTES, TOTAL_USERS, TOTAL_TOPICS, FEED_SIZE);

        Note[] notes = generateNotes();
        User[] users = generateUsers();
        Note[] notesSortedByLike = Arrays.stream(notes)
                .sorted(Comparator.comparingInt((Note n) -> n.likeTotal).reversed())
                .toArray(Note[]::new);

        benchmarkRecallCoverage(notes, users, notesSortedByLike);
        benchmarkDiversity(notes, users, notesSortedByLike);
        benchmarkFreshness(notes, notesSortedByLike);
        benchmarkPersonalization(notes, users, notesSortedByLike);
        benchmarkSimulatedCtr(notes, users, notesSortedByLike);
        benchmarkDecayImpact(notes, notesSortedByLike);
    }

    static void benchmarkRecallCoverage(Note[] notes, User[] users, Note[] sorted) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("1. 召回覆盖率 (多路召回触达的笔记总量)");
        System.out.println("═══════════════════════════════════════════════════");

        Set<Long> trendingAll = new HashSet<>();
        Set<Long> followingAll = new HashSet<>();
        Set<Long> topicAll = new HashSet<>();

        for (int u = 0; u < SAMPLE_USERS; u++) {
            User user = users[random.nextInt(TOTAL_USERS)];
            trendingAll.addAll(recallTrending(sorted, FEED_SIZE));
            followingAll.addAll(recallFollowing(notes, user, FEED_SIZE));
            topicAll.addAll(recallTopic(notes, user, FEED_SIZE));
        }

        Set<Long> merged = new HashSet<>();
        merged.addAll(trendingAll); merged.addAll(followingAll); merged.addAll(topicAll);

        double tCov = (double) trendingAll.size() / TOTAL_NOTES * 100;
        double fCov = (double) followingAll.size() / TOTAL_NOTES * 100;
        double tpCov = (double) topicAll.size() / TOTAL_NOTES * 100;
        double mCov = (double) merged.size() / TOTAL_NOTES * 100;

        System.out.printf("[热度召回]  覆盖: %,d 笔记 (%.1f%%)\n", trendingAll.size(), tCov);
        System.out.printf("[关注召回]  覆盖: %,d 笔记 (%.1f%%)\n", followingAll.size(), fCov);
        System.out.printf("[话题召回]  覆盖: %,d 笔记 (%.1f%%)\n", topicAll.size(), tpCov);
        System.out.printf("[三路合并]  覆盖: %,d 笔记 (%.1f%%)\n", merged.size(), mCov);
        System.out.printf("覆盖率提升: %.1fx (相比单路热度)\n", mCov / Math.max(tCov, 0.01));

        Set<Long> onlySocial = new HashSet<>(followingAll);
        onlySocial.addAll(topicAll);
        onlySocial.removeAll(trendingAll);
        System.out.printf("社交+兴趣通道独占贡献: %,d 笔记 (热度召回无法触达)\n\n", onlySocial.size());
    }

    static void benchmarkDiversity(Note[] notes, User[] users, Note[] sorted) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.printf("2. 多样性 (话题熵, 越高越多样, 最大=%.2f)\n", Math.log(TOTAL_TOPICS));
        System.out.println("═══════════════════════════════════════════════════");

        double trendingE = 0, followingE = 0, topicE = 0, mergedE = 0;
        int samples = 100;

        for (int u = 0; u < samples; u++) {
            User user = users[random.nextInt(TOTAL_USERS)];
            Set<Long> t = recallTrending(sorted, FEED_SIZE);
            Set<Long> f = recallFollowing(notes, user, FEED_SIZE);
            Set<Long> tp = recallTopic(notes, user, FEED_SIZE);
            LinkedHashSet<Long> m = mergeFeed(f, tp, t, FEED_SIZE);

            trendingE += entropy(notes, t);
            followingE += entropy(notes, f);
            topicE += entropy(notes, tp);
            mergedE += entropy(notes, m);
        }

        trendingE /= samples; followingE /= samples; topicE /= samples; mergedE /= samples;
        double maxE = Math.log(TOTAL_TOPICS);

        System.out.printf("[热度召回]  熵: %.3f (归一化: %.1f%%)\n", trendingE, trendingE / maxE * 100);
        System.out.printf("[关注召回]  熵: %.3f (归一化: %.1f%%)\n", followingE, followingE / maxE * 100);
        System.out.printf("[话题召回]  熵: %.3f (归一化: %.1f%%)\n", topicE, topicE / maxE * 100);
        System.out.printf("[三路合并]  熵: %.3f (归一化: %.1f%%)\n", mergedE, mergedE / maxE * 100);
        System.out.printf("多样性提升: %.1f%% (相比单路热度)\n\n", (mergedE / trendingE - 1) * 100);
    }

    static void benchmarkFreshness(Note[] notes, Note[] sorted) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("3. 新鲜度 (Feed 中笔记平均天数, 越小越新鲜)");
        System.out.println("═══════════════════════════════════════════════════");

        List<Note> noDecay = Arrays.stream(sorted).limit(FEED_SIZE).collect(Collectors.toList());
        List<Note> withDecay = Arrays.stream(notes)
                .sorted(Comparator.comparingDouble((Note n) ->
                        n.likeTotal * gaussDecay(n.daysAgo, 7, 0.5)).reversed())
                .limit(FEED_SIZE).collect(Collectors.toList());

        double avgNoDecay = noDecay.stream().mapToDouble(n -> n.daysAgo).average().orElse(0);
        double avgWithDecay = withDecay.stream().mapToDouble(n -> n.daysAgo).average().orElse(0);
        double recentNoDecay = (double) noDecay.stream().filter(n -> n.daysAgo <= 7).count() / FEED_SIZE * 100;
        double recentWithDecay = (double) withDecay.stream().filter(n -> n.daysAgo <= 7).count() / FEED_SIZE * 100;

        System.out.printf("[仅按热度排序]  平均天数: %.1f, 7天内占比: %.1f%%\n", avgNoDecay, recentNoDecay);
        System.out.printf("[Gauss衰减排序] 平均天数: %.1f, 7天内占比: %.1f%%\n", avgWithDecay, recentWithDecay);
        System.out.printf("新鲜度提升: %.0f%% (平均天数降低)\n\n",
                (1.0 - avgWithDecay / Math.max(avgNoDecay, 1)) * 100);
    }

    static void benchmarkPersonalization(Note[] notes, User[] users, Note[] sorted) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("4. 个性化程度 (不同用户 Feed 重叠率, 越低越个性化)");
        System.out.println("═══════════════════════════════════════════════════");

        int pairs = 100;
        double trendingOverlap = 0, mergedOverlap = 0;

        for (int p = 0; p < pairs; p++) {
            User u1 = users[random.nextInt(TOTAL_USERS)];
            User u2 = users[random.nextInt(TOTAL_USERS)];
            if (u1.id == u2.id) { p--; continue; }

            Set<Long> t1 = recallTrending(sorted, FEED_SIZE);
            Set<Long> t2 = recallTrending(sorted, FEED_SIZE);

            Set<Long> m1 = mergeFeed(recallFollowing(notes, u1, FEED_SIZE),
                    recallTopic(notes, u1, FEED_SIZE), recallTrending(sorted, FEED_SIZE), FEED_SIZE);
            Set<Long> m2 = mergeFeed(recallFollowing(notes, u2, FEED_SIZE),
                    recallTopic(notes, u2, FEED_SIZE), recallTrending(sorted, FEED_SIZE), FEED_SIZE);

            trendingOverlap += overlap(t1, t2);
            mergedOverlap += overlap(m1, m2);
        }

        trendingOverlap /= pairs; mergedOverlap /= pairs;

        System.out.printf("[仅热度召回]  用户间重叠率: %.1f%%\n", trendingOverlap * 100);
        System.out.printf("[三路合并]    用户间重叠率: %.1f%%\n", mergedOverlap * 100);
        System.out.printf("个性化提升: %.0f%% (重叠率降低)\n\n",
                (1.0 - mergedOverlap / Math.max(trendingOverlap, 0.001)) * 100);
    }

    static void benchmarkDecayImpact(Note[] notes, Note[] sorted) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("6. Gauss 衰减: 老笔记霸榜率 (Top50 中 >30天笔记占比)");
        System.out.println("═══════════════════════════════════════════════════");

        List<Note> noDecay = Arrays.stream(sorted).limit(FEED_SIZE).collect(Collectors.toList());
        List<Note> withDecay = Arrays.stream(notes)
                .sorted(Comparator.comparingDouble((Note n) ->
                        n.likeTotal * gaussDecay(n.daysAgo, 7, 0.5)).reversed())
                .limit(FEED_SIZE).collect(Collectors.toList());

        double oldNoDecay = (double) noDecay.stream().filter(n -> n.daysAgo > 30).count() / FEED_SIZE * 100;
        double oldWithDecay = (double) withDecay.stream().filter(n -> n.daysAgo > 30).count() / FEED_SIZE * 100;

        System.out.printf("[仅按热度排序]  >30天笔记占比: %.1f%%\n", oldNoDecay);
        System.out.printf("[Gauss衰减排序] >30天笔记占比: %.1f%%\n", oldWithDecay);
        System.out.printf("老笔记霸榜率降低: %.0f%%\n",
                (1.0 - oldWithDecay / Math.max(oldNoDecay, 1)) * 100);
    }

    static void benchmarkSimulatedCtr(Note[] notes, User[] users, Note[] sorted) {
        System.out.println("======================================================================");
        System.out.println("5. Simulated CTR (offline simulation, not real online CTR)");
        System.out.println("======================================================================");
        System.out.println("Click model: base + topic match + followed author + freshness + hotness.");
        System.out.println("This metric is only for comparing recall strategies under the same simulation assumptions.");

        Map<Long, Note> noteMap = Arrays.stream(notes).collect(Collectors.toMap(n -> n.id, n -> n));
        Random clickRandom = new Random(20260527);

        CtrStats trendingStats = new CtrStats();
        CtrStats mergedStats = new CtrStats();

        for (int i = 0; i < CTR_SAMPLE_USERS; i++) {
            User user = users[random.nextInt(TOTAL_USERS)];

            Set<Long> trendingFeed = recallTrending(sorted, FEED_SIZE);
            Set<Long> mergedFeed = mergeFeed(
                    recallFollowing(notes, user, FEED_SIZE),
                    recallTopic(notes, user, FEED_SIZE),
                    recallTrending(sorted, FEED_SIZE),
                    FEED_SIZE);

            simulateFeedClicks(user, trendingFeed, noteMap, trendingStats, clickRandom);
            simulateFeedClicks(user, mergedFeed, noteMap, mergedStats, clickRandom);
        }

        printCtrStats("[Trending baseline]", trendingStats);
        printCtrStats("[Three-way recall] ", mergedStats);
        System.out.printf("Expected CTR lift: %.1f%%\n",
                (mergedStats.expectedCtr() / Math.max(trendingStats.expectedCtr(), 0.0001) - 1) * 100);
        System.out.printf("Simulated CTR lift: %.1f%%\n\n",
                (mergedStats.simulatedCtr() / Math.max(trendingStats.simulatedCtr(), 0.0001) - 1) * 100);
    }

    // === 数据生成 ===

    static Note[] generateNotes() {
        Note[] notes = new Note[TOTAL_NOTES];
        for (int i = 0; i < TOTAL_NOTES; i++) {
            int daysAgo;
            double r = random.nextDouble();
            if (r < 0.3) daysAgo = random.nextInt(7);
            else if (r < 0.6) daysAgo = 7 + random.nextInt(23);
            else if (r < 0.85) daysAgo = 30 + random.nextInt(60);
            else daysAgo = 90 + random.nextInt(275);

            notes[i] = new Note(i + 1L, random.nextInt(TOTAL_USERS) + 1,
                    random.nextInt(TOTAL_TOPICS), random.nextInt(10000), daysAgo);
        }
        return notes;
    }

    static User[] generateUsers() {
        User[] users = new User[TOTAL_USERS];
        for (int i = 0; i < TOTAL_USERS; i++) {
            Set<Long> following = new HashSet<>();
            for (int f = 0; f < FOLLOWING_PER_USER; f++) following.add((long) (random.nextInt(TOTAL_USERS) + 1));
            Set<Integer> topics = new HashSet<>();
            for (int t = 0; t < 3; t++) topics.add(random.nextInt(TOTAL_TOPICS));
            users[i] = new User(i + 1L, following, topics);
        }
        return users;
    }

    // === 召回 ===

    static Set<Long> recallTrending(Note[] sorted, int limit) {
        return Arrays.stream(sorted).limit(limit).map(n -> n.id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<Long> recallFollowing(Note[] notes, User user, int limit) {
        return Arrays.stream(notes).filter(n -> user.following.contains(n.creatorId))
                .sorted(Comparator.comparingInt((Note n) -> n.likeTotal).reversed())
                .limit(limit).map(n -> n.id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<Long> recallTopic(Note[] notes, User user, int limit) {
        return Arrays.stream(notes).filter(n -> user.interestTopics.contains(n.topicId))
                .sorted(Comparator.comparingInt((Note n) -> n.likeTotal).reversed())
                .limit(limit).map(n -> n.id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static LinkedHashSet<Long> mergeFeed(Set<Long> following, Set<Long> topic, Set<Long> trending, int limit) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>();
        for (Long id : following) { if (merged.size() >= limit) break; merged.add(id); }
        for (Long id : topic) { if (merged.size() >= limit) break; merged.add(id); }
        for (Long id : trending) { if (merged.size() >= limit) break; merged.add(id); }
        return merged;
    }

    // === 工具 ===

    static double gaussDecay(double daysAgo, double scale, double decay) {
        double sigma = scale / Math.sqrt(-2.0 * Math.log(decay));
        return Math.exp(-0.5 * Math.pow(daysAgo / sigma, 2));
    }

    static double entropy(Note[] notes, Set<Long> ids) {
        Map<Integer, Long> tc = Arrays.stream(notes).filter(n -> ids.contains(n.id))
                .collect(Collectors.groupingBy(n -> n.topicId, Collectors.counting()));
        double total = tc.values().stream().mapToLong(l -> l).sum();
        if (total == 0) return 0;
        return -tc.values().stream().mapToDouble(c -> { double p = c / total; return p > 0 ? p * Math.log(p) : 0; }).sum();
    }

    static double overlap(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        return (double) a.stream().filter(b::contains).count() / Math.min(a.size(), b.size());
    }

    static void simulateFeedClicks(User user, Set<Long> feed, Map<Long, Note> noteMap,
                                   CtrStats stats, Random clickRandom) {
        for (Long noteId : feed) {
            Note note = noteMap.get(noteId);
            if (note == null) {
                continue;
            }

            double probability = clickProbability(user, note);
            stats.exposures++;
            stats.expectedClicks += probability;
            if (clickRandom.nextDouble() < probability) {
                stats.clicks++;
            }
        }
    }

    static double clickProbability(User user, Note note) {
        double probability = 0.015;
        if (user.interestTopics.contains(note.topicId)) {
            probability += 0.055;
        }
        if (user.following.contains(note.creatorId)) {
            probability += 0.035;
        }
        if (note.daysAgo <= 7) {
            probability += 0.010;
        }
        probability += 0.020 * Math.sqrt(note.likeTotal / 10_000.0);
        return Math.min(probability, 0.20);
    }

    static void printCtrStats(String name, CtrStats stats) {
        System.out.printf("%s exposure: %,d, simulated clicks: %,d, expected CTR: %.2f%%, simulated CTR: %.2f%%\n",
                name, stats.exposures, stats.clicks, stats.expectedCtr() * 100, stats.simulatedCtr() * 100);
    }

    static class Note {
        long id; long creatorId; int topicId; int likeTotal; int daysAgo;
        Note(long id, long c, int t, int l, int d) { this.id=id; creatorId=c; topicId=t; likeTotal=l; daysAgo=d; }
    }

    static class User {
        long id; Set<Long> following; Set<Integer> interestTopics;
        User(long id, Set<Long> f, Set<Integer> t) { this.id=id; following=f; interestTopics=t; }
    }

    static class CtrStats {
        long exposures;
        long clicks;
        double expectedClicks;

        double expectedCtr() {
            return exposures == 0 ? 0 : expectedClicks / exposures;
        }

        double simulatedCtr() {
            return exposures == 0 ? 0 : (double) clicks / exposures;
        }
    }
}
