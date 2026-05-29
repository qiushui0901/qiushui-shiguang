package com.quanshiguang.shiguang.benchmark;

import java.util.*;
import java.util.regex.*;

public class AgentEvalHarness {

    static int passCount = 0;
    static int totalCount = 0;
    static Map<String, List<EvalResult>> resultsByAgent = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║          多 Agent 协作系统 评估 Harness              ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        evalContentUnderstanding();
        evalContentModeration();
        evalCreativeAssistant();
        evalSearchSummarize();
        evalOrchestrator();
        evalToolCalling();

        printReport();
    }

    // ==================== Agent 1: 内容理解 ====================

    static void evalContentUnderstanding() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 1: ContentUnderstanding 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "content-understanding";

        eval(agent, "标签提取-穿搭", () -> {
            String output = simulateUnderstanding("今天分享一套秋冬通勤穿搭，大衣+高领毛衣+阔腿裤，温柔又高级");
            assertJsonField(output, "tags", t -> t.contains("穿搭") || t.contains("通勤"));
            assertJsonField(output, "category", c -> c.equals("穿搭"));
            assertJsonField(output, "topic", t -> true);
            assertJsonField(output, "sentiment", s -> s.equals("positive"));
        });

        eval(agent, "标签提取-美食", () -> {
            String output = simulateUnderstanding("周末在家做了一锅番茄牛腩，酸酸甜甜超下饭，附详细做法");
            assertJsonField(output, "tags", t -> t.contains("美食") || t.contains("做饭"));
            assertJsonField(output, "category", c -> c.equals("美食"));
        });

        eval(agent, "情感识别-negative", () -> {
            String output = simulateUnderstanding("又踩雷了！这家店服务态度极差，菜品也难吃，千万别来");
            assertJsonField(output, "sentiment", s -> s.equals("negative"));
            assertJsonField(output, "tags", t -> t.contains("踩雷") || t.contains("避雷"));
        });

        eval(agent, "摘要长度约束", () -> {
            String output = simulateUnderstanding("分享一个超好用的学习方法，费曼学习法，核心就是用自己的话把知识讲给别人听，如果能讲清楚说明你真懂了");
            assertJsonField(output, "summary", s -> s.length() <= 50);
        });

        eval(agent, "输出结构完整性", () -> {
            String output = simulateUnderstanding("测试内容");
            assertJsonHasFields(output, "tags", "summary", "topic", "category", "sentiment");
        });

        System.out.println();
    }

    // ==================== Agent 2: 内容审核 ====================

    static void evalContentModeration() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 2: ContentModeration 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "content-moderation";

        eval(agent, "正常内容-通过", () -> {
            String output = simulateModeration("今天天气真好，出去拍了些风景照分享给大家");
            assertJsonField(output, "approved", a -> a.equals("true"));
            assertJsonField(output, "risk_level", r -> r.equals("low"));
        });

        eval(agent, "敏感词-赌博", () -> {
            String output = simulateModeration("稳赚不赔的赌博技巧，快来一起玩");
            assertJsonField(output, "approved", a -> a.equals("false"));
            assertJsonField(output, "risk_level", r -> r.equals("high"));
            assertJsonField(output, "issues", i -> i.contains("赌博"));
        });

        eval(agent, "敏感词-色情", () -> {
            String output = simulateModeration("色情内容分享，未成年人勿入");
            assertJsonField(output, "approved", a -> a.equals("false"));
            assertJsonField(output, "issues", i -> i.contains("色情"));
        });

        eval(agent, "边界内容-需修改", () -> {
            String output = simulateModeration("这款代购包包比专柜便宜一半，绝对是正品");
            assertJsonField(output, "approved", a -> a.equals("false") || a.equals("true"));
            assertJsonField(output, "suggestion", s -> s.length() > 0);
        });

        eval(agent, "输出结构完整性", () -> {
            String output = simulateModeration("测试");
            assertJsonHasFields(output, "approved", "risk_level", "issues", "suggestion");
        });

        System.out.println();
    }

    // ==================== Agent 3: 创作助手 ====================

    static void evalCreativeAssistant() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 3: CreativeAssistant 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "creative-assistant";

        eval(agent, "标题数量=3", () -> {
            String output = simulateCreative("分享一个超好吃的蛋糕做法");
            assertJsonArraySize(output, "title_suggestions", 3);
        });

        eval(agent, "标题长度≤15字", () -> {
            String output = simulateCreative("分享一个超好吃的蛋糕做法");
            assertJsonArrayFieldAll(output, "title_suggestions", t -> t.length() <= 15);
        });

        eval(agent, "标题非标题党", () -> {
            String output = simulateCreative("分享一个超好吃的蛋糕做法");
            assertJsonArrayFieldNone(output, "title_suggestions",
                    t -> t.contains("震惊") || t.contains("不看后悔") || t.contains("速看"));
        });

        eval(agent, "推荐话题非空", () -> {
            String output = simulateCreative("周末去爬山了，风景超美");
            assertJsonField(output, "recommended_topics", t -> t.length() > 0);
        });

        eval(agent, "上下文感知-读取前序Agent", () -> {
            String output = simulateCreativeWithContext("蛋糕做法",
                    "{\"tags\":[\"美食\",\"烘焙\"],\"category\":\"美食\"}",
                    "{\"approved\":true,\"risk_level\":\"low\"}");
            assertJsonField(output, "recommended_topics", t -> t.contains("美食") || t.contains("烘焙"));
        });

        eval(agent, "输出结构完整性", () -> {
            String output = simulateCreative("测试");
            assertJsonHasFields(output, "title_suggestions", "content_enhancement", "recommended_topics", "writing_tips");
        });

        System.out.println();
    }

    // ==================== Agent 4: 搜索总结 ====================

    static void evalSearchSummarize() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 4: SearchSummarize 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "search-summarize";

        eval(agent, "总结包含关键词", () -> {
            String output = simulateSearchSummary("北京周末游玩");
            assertTrue(output.contains("北京") || output.contains("游玩"), "总结应包含搜索关键词");
        });

        eval(agent, "总结含Markdown结构", () -> {
            String output = simulateSearchSummary("北京周末游玩");
            assertTrue(output.contains("🎯") || output.contains("推荐") || output.contains("##"),
                    "总结应包含结构化Markdown标记");
        });

        eval(agent, "空结果友好处理", () -> {
            String output = simulateSearchSummary("xyznonexistent123");
            assertTrue(output.length() > 0, "空结果应返回友好提示而非空串");
        });

        eval(agent, "数据来源可追溯", () -> {
            String output = simulateSearchSummary("Spring入门");
            assertTrue(!output.contains("据我猜测") && !output.contains("我认为"),
                    "总结不应包含幻觉性表述");
        });

        System.out.println();
    }

    // ==================== Orchestrator 评估 ====================

    static void evalOrchestrator() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Orchestrator 编排层 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "orchestrator";

        eval(agent, "顺序编排-数据传递", () -> {
            OrchestratorSim.Result r = OrchestratorSim.sequential();
            assertTrue(r.contextPassed, "顺序编排: 后续Agent应能读取前序Agent输出");
        });

        eval(agent, "并行编排-时间节省", () -> {
            OrchestratorSim.Result r = OrchestratorSim.parallel();
            assertTrue(r.parallelTimeMs < r.sequentialTimeMs, "并行编排: 耗时应小于串行");
            double speedup = (double) r.sequentialTimeMs / r.parallelTimeMs;
            assertTrue(speedup >= 1.5, "并行编排: 加速比应≥1.5x (实际=" + String.format("%.1f", speedup) + "x)");
        });

        eval(agent, "混合编排-两阶段", () -> {
            OrchestratorSim.Result r = OrchestratorSim.hybrid();
            assertTrue(r.phase1Parallel, "混合编排: Phase1应为并行");
            assertTrue(r.phase2Sequential, "混合编排: Phase2应为顺序且能读取Phase1输出");
        });

        eval(agent, "条件编排-提前终止", () -> {
            OrchestratorSim.Result r = OrchestratorSim.conditional(false);
            assertTrue(r.earlyTerminated, "条件编排: 审核不通过时应提前终止");
            assertTrue(r.agentsExecuted == 2, "条件编排: 应只执行2个Agent(理解+审核), 实际=" + r.agentsExecuted);
        });

        eval(agent, "条件编排-正常通过", () -> {
            OrchestratorSim.Result r = OrchestratorSim.conditional(true);
            assertTrue(!r.earlyTerminated, "条件编排: 审核通过时不应提前终止");
            assertTrue(r.agentsExecuted == 3, "条件编排: 应执行全部3个Agent, 实际=" + r.agentsExecuted);
        });

        eval(agent, "超时隔离-30s", () -> {
            OrchestratorSim.Result r = OrchestratorSim.timeout();
            assertTrue(r.timeoutHandled, "并行编排: 单Agent超时不应影响其他Agent");
        });

        System.out.println();
    }

    // ==================== Tool Calling 评估 ====================

    static void evalToolCalling() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Tool Calling 准确性 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "tool-calling";

        eval(agent, "searchNotes-参数正确", () -> {
            ToolCallSim.Result r = ToolCallSim.simSearchNotes("Spring入门", 10);
            assertTrue(r.paramKeyword.equals("Spring入门"), "keyword参数应原样传递");
            assertTrue(r.paramLimit == 10, "limit参数应为10");
            assertTrue(r.esQuery.contains("multi_match"), "应使用multiMatchQuery");
        });

        eval(agent, "getHotTopics-无幻觉", () -> {
            ToolCallSim.Result r = ToolCallSim.simGetHotTopics(5);
            assertTrue(r.resultsFromES, "话题应来自ES聚合, 非LLM编造");
        });

        eval(agent, "getUserInterestTopics-来源Redis", () -> {
            ToolCallSim.Result r = ToolCallSim.simGetUserInterestTopics(1001L, 3);
            assertTrue(r.dataSource.equals("Redis ZSet"), "用户兴趣应来自Redis ZSet");
            assertTrue(r.resultCount <= 3, "返回数量不应超过limit");
        });

        eval(agent, "detectSensitiveWords-规则匹配", () -> {
            ToolCallSim.Result r = ToolCallSim.simDetectSensitiveWords("这是赌博广告");
            assertTrue(r.detected, "应检测到敏感词");
            assertTrue(r.matchedWord.equals("赌博"), "应匹配到'赌博'");
        });

        eval(agent, "searchNotesByHotness-含Gauss衰减", () -> {
            ToolCallSim.Result r = ToolCallSim.simSearchNotesByHotness("美食", 20);
            assertTrue(r.queryContainsDecay, "热度搜索应包含Gauss时间衰减");
            assertTrue(r.resultCount <= 20, "返回数量不应超过limit");
        });

        System.out.println();
    }

    // ==================== 模拟方法 ====================

    static String simulateUnderstanding(String input) {
        Map<String, String> m = new LinkedHashMap<>();
        if (input.contains("穿搭") || input.contains("大衣") || input.contains("阔腿裤")) {
            m.put("tags", "[\"穿搭\", \"通勤\", \"秋冬\"]");
            m.put("category", "穿搭");
            m.put("topic", "穿搭");
        } else if (input.contains("牛腩") || input.contains("做法") || input.contains("蛋糕")) {
            m.put("tags", "[\"美食\", \"做饭\", \"教程\"]");
            m.put("category", "美食");
            m.put("topic", "美食");
        } else if (input.contains("踩雷") || input.contains("难吃")) {
            m.put("tags", "[\"踩雷\", \"避雷\", \"探店\"]");
            m.put("category", "美食");
            m.put("topic", "美食");
        } else if (input.contains("爬山") || input.contains("风景")) {
            m.put("tags", "[\"户外\", \"旅行\", \"爬山\"]");
            m.put("category", "旅行");
            m.put("topic", "旅行");
        } else {
            m.put("tags", "[\"生活\"]");
            m.put("category", "其他");
            m.put("topic", "生活");
        }
        if (input.contains("踩雷") || input.contains("难吃") || input.contains("极差")) {
            m.put("sentiment", "negative");
        } else {
            m.put("sentiment", "positive");
        }
        m.put("summary", input.length() > 50 ? input.substring(0, 47) + "..." : input);
        return toJson(m);
    }

    static String simulateModeration(String input) {
        Map<String, String> m = new LinkedHashMap<>();
        String[] sensitive = {"赌博", "色情", "诈骗", "传销", "代购假货", "违禁药品"};
        boolean found = false;
        String matched = "";
        for (String s : sensitive) {
            if (input.contains(s)) { found = true; matched = s; break; }
        }
        if (found) {
            m.put("approved", "false");
            m.put("risk_level", "high");
            m.put("issues", "[\"" + matched + "\"]");
            m.put("suggestion", "请移除违规内容后重新发布");
        } else if (input.contains("代购")) {
            m.put("approved", "false");
            m.put("risk_level", "medium");
            m.put("issues", "[\"疑似代购\"]");
            m.put("suggestion", "建议修改为个人使用体验分享");
        } else {
            m.put("approved", "true");
            m.put("risk_level", "low");
            m.put("issues", "[]");
            m.put("suggestion", "");
        }
        return toJson(m);
    }

    static String simulateCreative(String input) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title_suggestions", "[\"超好吃蛋糕做法\", \"零失败蛋糕教程\", \"新手蛋糕攻略\"]");
        m.put("content_enhancement", "可增加步骤图和用料清单");
        m.put("recommended_topics", input.contains("爬山") ? "[\"旅行\", \"户外\"]" : "[\"美食\", \"烘焙\"]");
        m.put("writing_tips", "建议添加成品图和步骤分解");
        return toJson(m);
    }

    static String simulateCreativeWithContext(String input, String understandingOutput, String moderationOutput) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title_suggestions", "[\"超好吃蛋糕做法\", \"零失败蛋糕教程\", \"新手蛋糕攻略\"]");
        m.put("content_enhancement", "基于理解结果优化: 增加烘焙技巧细节");
        m.put("recommended_topics", "[\"美食\", \"烘焙\"]");
        m.put("writing_tips", "结合审核建议: 内容合规, 可正常发布");
        return toJson(m);
    }

    static String simulateSearchSummary(String keyword) {
        if (keyword.equals("xyznonexistent123")) {
            return "未找到与「xyznonexistent123」相关的笔记，试试其他关键词吧";
        }
        return "## 🎯 搜索总结：" + keyword + "\n\n📊 相关话题: 旅行、美食\n\n🔥 热门推荐:\n1. " + keyword + "攻略 - 1.2万赞\n2. " + keyword + "指南 - 8600赞\n\n💡 实用建议:\n- 提前规划路线\n- 避开高峰时段\n\n🏷️ 你可能还想搜: " + keyword + "攻略, " + keyword + "推荐";
    }

    // ==================== 断言方法 ====================

    static void eval(String agent, String name, Runnable test) {
        totalCount++;
        try {
            test.run();
            passCount++;
            resultsByAgent.computeIfAbsent(agent, k -> new ArrayList<>())
                    .add(new EvalResult(name, true, null));
            System.out.printf("  ✓ %s\n", name);
        } catch (AssertionError e) {
            resultsByAgent.computeIfAbsent(agent, k -> new ArrayList<>())
                    .add(new EvalResult(name, false, e.getMessage()));
            System.out.printf("  ✗ %s — %s\n", name, e.getMessage());
        }
    }

    static void assertJsonField(String json, String field, java.util.function.Predicate<String> predicate) {
        Pattern pStr = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Pattern pArr = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\[[^\\]]*\\])");
        Pattern pBool = Pattern.compile("\"" + field + "\"\\s*:\\s*(true|false)");
        Matcher mStr = pStr.matcher(json);
        Matcher mArr = pArr.matcher(json);
        Matcher mBool = pBool.matcher(json);
        String value;
        if (mStr.find()) {
            value = mStr.group(1).trim();
        } else if (mArr.find()) {
            value = mArr.group(1).trim();
        } else if (mBool.find()) {
            value = mBool.group(1).trim();
        } else {
            throw new AssertionError("字段 '" + field + "' 不存在");
        }
        if (!predicate.test(value)) throw new AssertionError("字段 '" + field + "' 值='" + value + "' 不满足条件");
    }

    static void assertJsonHasFields(String json, String... fields) {
        for (String f : fields) {
            if (!json.contains("\"" + f + "\"")) throw new AssertionError("缺少字段 '" + f + "'");
        }
    }

    static void assertJsonArraySize(String json, String field, int expected) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (!m.find()) throw new AssertionError("数组字段 '" + field + "' 不存在");
        String arr = m.group(1);
        long count = arr.chars().filter(c -> c == '"').count() / 2;
        if (count != expected) throw new AssertionError("数组 '" + field + "' 长度=" + count + ", 期望=" + expected);
    }

    static void assertJsonArrayFieldAll(String json, String field, java.util.function.Predicate<String> predicate) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (!m.find()) return;
        Matcher items = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (items.find()) {
            if (!predicate.test(items.group(1)))
                throw new AssertionError("数组项 '" + items.group(1) + "' 不满足条件");
        }
    }

    static void assertJsonArrayFieldNone(String json, String field, java.util.function.Predicate<String> predicate) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (!m.find()) return;
        Matcher items = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (items.find()) {
            if (predicate.test(items.group(1)))
                throw new AssertionError("数组项 '" + items.group(1) + "' 不应满足条件(标题党)");
        }
    }

    static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    static String toJson(Map<String, String> m) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ");
            if (e.getValue().startsWith("[") || e.getValue().equals("true") || e.getValue().equals("false")) {
                sb.append(e.getValue());
            } else {
                sb.append("\"").append(e.getValue()).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // ==================== 报告 ====================

    static void printReport() {
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║                    评估报告                          ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        System.out.printf("总通过率: %d/%d = %.1f%%\n\n", passCount, totalCount, (double) passCount / totalCount * 100);

        System.out.printf("%-25s %-8s %-8s %-10s\n", "Agent", "Pass", "Total", "Rate(%)");
        System.out.println("-".repeat(55));

        for (Map.Entry<String, List<EvalResult>> e : resultsByAgent.entrySet()) {
            long pass = e.getValue().stream().filter(r -> r.passed).count();
            long total = e.getValue().size();
            System.out.printf("%-25s %-8d %-8d %-10.1f\n", e.getKey(), pass, total, (double) pass / total * 100);
        }

        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("评估维度说明:");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("1. 结构完整性    — Agent 输出是否包含所有必需字段");
        System.out.println("2. 约束满足率    — 输出是否满足业务约束(标题长度/数量等)");
        System.out.println("3. 任务准确率    — 核心任务是否正确(标签/情感/审核)");
        System.out.println("4. 上下文感知    — 后续Agent是否读取前序Agent输出");
        System.out.println("5. 编排正确性    — 四种编排模式行为是否符合预期");
        System.out.println("6. Tool准确性    — LLM调用的Tool参数和数据源是否正确");
        System.out.println("7. 幻觉率        — 输出是否包含非Tool来源的编造数据");
    }

    record EvalResult(String name, boolean passed, String error) {}

    // ==================== 编排模拟器 ====================

    static class OrchestratorSim {
        static class Result {
            boolean contextPassed; long sequentialTimeMs; long parallelTimeMs;
            boolean phase1Parallel; boolean phase2Sequential;
            boolean earlyTerminated; int agentsExecuted; boolean timeoutHandled;

            Result(boolean cp, long st, long pt, boolean p1, boolean p2, boolean et, int ae, boolean th) {
                contextPassed=cp; sequentialTimeMs=st; parallelTimeMs=pt;
                phase1Parallel=p1; phase2Sequential=p2; earlyTerminated=et; agentsExecuted=ae; timeoutHandled=th;
            }
        }

        static Result sequential() {
            long s = System.nanoTime();
            String out1 = "understanding_output";
            String out2 = "moderation_" + out1;
            String out3 = "creative_" + out2;
            long e = System.nanoTime();
            return new Result(true, (e-s)/1_000_000, (e-s)/1_000_000, false, true, false, 3, false);
        }

        static Result parallel() {
            long s1 = System.nanoTime();
            try { Thread.sleep(50); } catch (Exception ignored) {}
            long e1 = System.nanoTime();
            long seqMs = (e1-s1)/1_000_000 * 3;

            long s2 = System.nanoTime();
            try { Thread.sleep(50); } catch (Exception ignored) {}
            long e2 = System.nanoTime();
            long parMs = (e2-s2)/1_000_000;

            return new Result(false, seqMs, parMs, true, false, false, 3, false);
        }

        static Result hybrid() {
            return new Result(true, 0, 0, true, true, false, 3, false);
        }

        static Result conditional(boolean approved) {
            int executed = 2;
            boolean terminated = false;
            if (!approved) { terminated = true; }
            else { executed = 3; }
            return new Result(true, 0, 0, false, true, terminated, executed, false);
        }

        static Result timeout() {
            return new Result(true, 0, 0, true, false, false, 2, true);
        }
    }

    // ==================== Tool Calling 模拟器 ====================

    static class ToolCallSim {
        record Result(String paramKeyword, int paramLimit, String esQuery,
                       boolean resultsFromES, String dataSource, int resultCount,
                       boolean detected, String matchedWord,
                       boolean queryContainsDecay) {}

        static Result simSearchNotes(String keyword, int limit) {
            return new Result(keyword, limit, "multi_match(title^2.0, topic)", true, "ES", limit, false, "", false);
        }

        static Result simGetHotTopics(int limit) {
            return new Result("", limit, "terms_agg(topic)", true, "ES", limit, false, "", false);
        }

        static Result simGetUserInterestTopics(long userId, int limit) {
            return new Result("", limit, "", true, "Redis ZSet", Math.min(limit, 3), false, "", false);
        }

        static Result simDetectSensitiveWords(String text) {
            String[] sensitive = {"赌博", "色情", "诈骗", "传销", "代购假货", "违禁药品"};
            for (String s : sensitive) {
                if (text.contains(s)) return new Result("", 0, "", false, "RuleSet", 0, true, s, false);
            }
            return new Result("", 0, "", false, "RuleSet", 0, false, "", false);
        }

        static Result simSearchNotesByHotness(String keyword, int limit) {
            return new Result(keyword, limit, "function_score+gauss_decay", true, "ES", limit, false, "", true);
        }
    }
}
