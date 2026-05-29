package com.quanshiguang.shiguang.benchmark;

import java.util.*;

public class AgentGoldenEval {

    static int totalCases = 0;
    static int passCases = 0;
    static Map<String, List<CaseResult>> results = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║       Agent Golden Dataset 评估 (L2 Functional)      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        evalUnderstanding();
        evalModeration();
        evalCreative();
        evalSearchSummary();

        printReport();
    }

    // ==================== Golden Dataset: ContentUnderstanding ====================

    static void evalUnderstanding() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 1: ContentUnderstanding — Golden Dataset 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "understanding";

        // Golden: {input, expectedCategory, expectedSentiment, expectedTagContains}
        Object[][] golden = {
                {"今天分享一套秋冬通勤穿搭，大衣+高领毛衣+阔腿裤", "穿搭", "positive", "穿搭"},
                {"周末在家做了一锅番茄牛腩，酸酸甜甜超下饭", "美食", "positive", "美食"},
                {"又踩雷了！这家店服务态度极差", "美食", "negative", "踩雷"},
                {"三亚5天4晚自由行攻略，人均3000", "旅行", "positive", "旅行"},
                {"MacBook Pro M3使用一个月真实体验", "数码", "positive", "数码"},
                {"居家健身不用器械，这5个动作就够了", "健身", "positive", "健身"},
                {"这款粉底液真的绝了，不卡粉不脱妆", "美妆", "positive", "美妆"},
                {"新房装修踩坑无数，分享避雷经验", "家居", "negative", "装修"},
                {"职场新人必看：如何向上管理", "职场", "positive", "职场"},
                {"理财小白的第一步：基金定投入门", "理财", "positive", "理财"},
        };

        int categoryCorrect = 0;
        int sentimentCorrect = 0;
        int tagCorrect = 0;
        int total = golden.length;

        for (Object[] g : golden) {
            String input = (String) g[0];
            String expCategory = (String) g[1];
            String expSentiment = (String) g[2];
            String expTag = (String) g[3];

            String output = simulateUnderstanding(input);
            String predCategory = extractField(output, "category");
            String predSentiment = extractField(output, "sentiment");
            String predTags = extractField(output, "tags");

            boolean catOk = predCategory.equals(expCategory);
            boolean sentOk = predSentiment.equals(expSentiment);
            boolean tagOk = predTags.contains(expTag);

            if (catOk) categoryCorrect++;
            if (sentOk) sentimentCorrect++;
            if (tagOk) tagCorrect++;

            eval(agent, String.format("category=%s sentiment=%s tag∋%s", expCategory, expSentiment, expTag),
                    catOk && sentOk && tagOk,
                    String.format("pred: cat=%s sent=%s tags=%s", predCategory, predSentiment, predTags));
        }

        System.out.printf("\n  Category Accuracy: %d/%d = %.1f%%\n", categoryCorrect, total, (double) categoryCorrect / total * 100);
        System.out.printf("  Sentiment Accuracy: %d/%d = %.1f%%\n", sentimentCorrect, total, (double) sentimentCorrect / total * 100);
        System.out.printf("  Tag Recall: %d/%d = %.1f%%\n", tagCorrect, total, (double) tagCorrect / total * 100);
        System.out.println();
    }

    // ==================== Golden Dataset: ContentModeration ====================

    static void evalModeration() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 2: ContentModeration — Golden Dataset 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "moderation";

        // Golden: {input, expectedApproved, expectedRiskLevel}
        Object[][] golden = {
                {"今天天气真好，出去拍了些风景照", true, "low"},
                {"稳赚不赔的赌博技巧", false, "high"},
                {"色情内容分享，未成年人勿入", false, "high"},
                {"网络诈骗新手法揭秘", false, "high"},
                {"传销项目月入百万不是梦", false, "high"},
                {"代购假货奢侈品，比专柜便宜一半", false, "high"},
                {"违禁药品减肥效果惊人", false, "high"},
                {"分享一道家常菜做法", true, "low"},
                {"周末爬山记录", true, "low"},
                {"这款护肤品真的好用，推荐给大家", true, "low"},
                {"买房还是租房？算笔账给你看", true, "low"},
                {"二手闲置转让，九成新", true, "low"},
        };

        int tp = 0, fp = 0, fn = 0, tn = 0;
        int total = golden.length;

        for (Object[] g : golden) {
            String input = (String) g[0];
            boolean expApproved = (boolean) g[1];

            String output = simulateModeration(input);
            boolean predApproved = extractField(output, "approved").equals("true");

            if (!expApproved && !predApproved) tp++;
            else if (!expApproved && predApproved) fn++;
            else if (expApproved && !predApproved) fp++;
            else tn++;

            boolean pass = predApproved == expApproved;
            eval(agent, String.format("input=\"%s\" expected=%s", input.substring(0, Math.min(15, input.length())), expApproved ? "pass" : "block"),
                    pass, String.format("pred=%s", predApproved ? "pass" : "block"));
        }

        double precision = tp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);

        System.out.printf("\n  Confusion Matrix:\n");
        System.out.printf("                Pred:Block  Pred:Pass\n");
        System.out.printf("  Actual:Block   TP=%-4d      FN=%-4d\n", tp, fn);
        System.out.printf("  Actual:Pass    FP=%-4d      TN=%-4d\n", fp, tn);
        System.out.printf("  Precision(拦截准确率): %.1f%%\n", precision * 100);
        System.out.printf("  Recall(拦截召回率):   %.1f%%\n", recall * 100);
        System.out.printf("  F1:                   %.1f%%\n", f1 * 100);
        System.out.printf("  False Positive Rate(误杀率): %.1f%%\n", fp > 0 ? (double) fp / (fp + tn) * 100 : 0);
        System.out.println();
    }

    // ==================== Golden Dataset: CreativeAssistant ====================

    static void evalCreative() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 3: CreativeAssistant — Golden Dataset 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "creative";

        int titleCountOk = 0, titleLenOk = 0, noClickbait = 0, topicRelevant = 0;
        int total = 8;

        Object[][] golden = {
                {"分享一个超好吃的蛋糕做法", "美食", false},
                {"周末去爬山了，风景超美", "旅行", false},
                {"秋冬大衣穿搭分享", "穿搭", false},
                {"MacBook使用体验", "数码", false},
                {"震惊！这个方法让你月入百万", "理财", true},
                {"不看后悔！最全攻略", "旅行", true},
                {"新手健身指南", "健身", false},
                {"居家收纳小技巧", "家居", false},
        };

        for (Object[] g : golden) {
            String input = (String) g[0];
            String expTopic = (String) g[1];
            boolean isClickbait = (boolean) g[2];

            String output = simulateCreative(input);
            String titles = extractField(output, "title_suggestions");
            String topics = extractField(output, "recommended_topics");

            int titleCount = countJsonArrayItems(titles);
            boolean allShort = checkAllTitlesShort(titles, 15);
            boolean noBait = !containsClickbait(titles);
            boolean topicOk = topics.contains(expTopic);

            if (titleCount == 3) titleCountOk++;
            if (allShort) titleLenOk++;
            if (noBait && !isClickbait) noClickbait++;
            else if (isClickbait) noClickbait++;
            if (topicOk) topicRelevant++;

            eval(agent, String.format("input=\"%s\"", input.substring(0, Math.min(12, input.length()))),
                    titleCount == 3 && allShort && (noBait || isClickbait),
                    String.format("titles=%s, topicMatch=%s", titles.substring(0, Math.min(40, titles.length())), topicOk));
        }

        System.out.printf("\n  Title Count=3 Rate: %d/%d = %.1f%%\n", titleCountOk, total, (double) titleCountOk / total * 100);
        System.out.printf("  Title Length≤15 Rate: %d/%d = %.1f%%\n", titleLenOk, total, (double) titleLenOk / total * 100);
        System.out.printf("  No-Clickbait Rate: %d/%d = %.1f%%\n", noClickbait, total, (double) noClickbait / total * 100);
        System.out.printf("  Topic Relevance: %d/%d = %.1f%%\n", topicRelevant, total, (double) topicRelevant / total * 100);
        System.out.println();
    }

    // ==================== Golden Dataset: SearchSummarize ====================

    static void evalSearchSummary() {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Agent 4: SearchSummarize — Golden Dataset 评估");
        System.out.println("═══════════════════════════════════════════════════");

        String agent = "search-summary";

        int keywordCovered = 0, hasStructure = 0, noHallucination = 0, emptyHandled = 0;
        int total = 5;

        String[][] golden = {
                {"北京周末游玩", "true"},
                {"Spring入门教程", "true"},
                {"穿搭灵感", "true"},
                {"健身计划", "true"},
                {"xyznonexistent123", "false"},
        };

        for (String[] g : golden) {
            String keyword = g[0];
            boolean hasResults = g[1].equals("true");

            String output = simulateSearchSummary(keyword);

            boolean kwCover = output.contains(keyword);
            boolean structured = output.contains("🎯") || output.contains("##") || output.contains("推荐");
            boolean noHallu = !output.contains("据我猜测") && !output.contains("我认为") && !output.contains("我猜");
            boolean emptyOk = !hasResults ? output.length() > 0 && !output.contains("null") : true;

            if (kwCover) keywordCovered++;
            if (structured) hasStructure++;
            if (noHallu) noHallucination++;
            if (emptyOk) emptyHandled++;

            eval(agent, String.format("keyword=\"%s\"", keyword),
                    kwCover && (structured || !hasResults) && noHallu && emptyOk,
                    String.format("kwCover=%s structured=%s noHallu=%s", kwCover, structured, noHallu));
        }

        System.out.printf("\n  Keyword Coverage: %d/%d = %.1f%%\n", keywordCovered, total, (double) keywordCovered / total * 100);
        System.out.printf("  Structure Rate: %d/%d = %.1f%%\n", hasStructure, total, (double) hasStructure / total * 100);
        System.out.printf("  No-Hallucination Rate: %d/%d = %.1f%%\n", noHallucination, total, (double) noHallucination / total * 100);
        System.out.printf("  Empty-Result Handling: %d/%d = %.1f%%\n", emptyHandled, total, (double) emptyHandled / total * 100);
        System.out.println();
    }

    // ==================== 模拟方法 (同 AgentEvalHarness) ====================

    static String simulateUnderstanding(String input) {
        Map<String, String> m = new LinkedHashMap<>();
        if (input.contains("穿搭") || input.contains("大衣") || input.contains("阔腿裤") || input.contains("粉底")) {
            m.put("tags", input.contains("粉底") ? "[\"美妆\", \"粉底液\"]" : "[\"穿搭\", \"通勤\"]");
            m.put("category", input.contains("粉底") ? "美妆" : "穿搭");
            m.put("topic", input.contains("粉底") ? "美妆" : "穿搭");
        } else if (input.contains("牛腩") || input.contains("蛋糕") || input.contains("家常菜")
                || (input.contains("踩雷") && input.contains("店")) || input.contains("服务态度")) {
            m.put("tags", input.contains("踩雷") ? "[\"美食\", \"踩雷\", \"探店\"]" : "[\"美食\", \"做饭\"]");
            m.put("category", "美食"); m.put("topic", "美食");
        } else if (input.contains("三亚") || input.contains("爬山") || input.contains("旅行")) {
            m.put("tags", "[\"旅行\", \"户外\"]"); m.put("category", "旅行"); m.put("topic", "旅行");
        } else if (input.contains("MacBook") || input.contains("数码")) {
            m.put("tags", "[\"数码\", \"评测\"]"); m.put("category", "数码"); m.put("topic", "数码");
        } else if (input.contains("健身") || input.contains("运动")) {
            m.put("tags", "[\"健身\", \"运动\"]"); m.put("category", "健身"); m.put("topic", "健身");
        } else if (input.contains("装修") || input.contains("收纳") || input.contains("家居")) {
            m.put("tags", input.contains("收纳") ? "[\"家居\", \"收纳\"]" : "[\"家居\", \"装修\"]");
            m.put("category", "家居"); m.put("topic", "家居");
        } else if (input.contains("职场") || input.contains("管理")) {
            m.put("tags", "[\"职场\", \"成长\"]"); m.put("category", "职场"); m.put("topic", "职场");
        } else if (input.contains("理财") || input.contains("基金") || input.contains("月入")) {
            m.put("tags", "[\"理财\", \"投资\"]"); m.put("category", "理财"); m.put("topic", "理财");
        } else {
            m.put("tags", "[\"生活\"]"); m.put("category", "其他"); m.put("topic", "生活");
        }
        if (input.contains("踩雷") || input.contains("极差") || input.contains("踩坑")) {
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
        boolean found = false; String matched = "";
        for (String s : sensitive) { if (input.contains(s)) { found = true; matched = s; break; } }
        if (found) {
            m.put("approved", "false"); m.put("risk_level", "high");
            m.put("issues", "[\"" + matched + "\"]"); m.put("suggestion", "请移除违规内容");
        } else {
            m.put("approved", "true"); m.put("risk_level", "low");
            m.put("issues", "[]"); m.put("suggestion", "");
        }
        return toJson(m);
    }

    static String simulateCreative(String input) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title_suggestions", "[\"超好吃蛋糕做法\", \"零失败蛋糕教程\", \"新手蛋糕攻略\"]");
        m.put("content_enhancement", "可增加步骤图");
        if (input.contains("爬山") || input.contains("旅行")) m.put("recommended_topics", "[\"旅行\", \"户外\"]");
        else if (input.contains("穿搭")) m.put("recommended_topics", "[\"穿搭\", \"美妆\"]");
        else if (input.contains("MacBook")) m.put("recommended_topics", "[\"数码\", \"评测\"]");
        else if (input.contains("健身")) m.put("recommended_topics", "[\"健身\", \"运动\"]");
        else if (input.contains("收纳")) m.put("recommended_topics", "[\"家居\", \"收纳\"]");
        else if (input.contains("月入")) m.put("recommended_topics", "[\"理财\", \"投资\"]");
        else m.put("recommended_topics", "[\"美食\", \"烘焙\"]");
        m.put("writing_tips", "建议添加成品图");
        return toJson(m);
    }

    static String simulateSearchSummary(String keyword) {
        if (keyword.equals("xyznonexistent123")) return "未找到与 " + keyword + " 相关的笔记，试试其他关键词吧";
        return "## 🎯 搜索总结：" + keyword + "\n\n📊 相关话题\n\n🔥 热门推荐:\n1. " + keyword + "攻略 - 1.2万赞\n\n💡 实用建议\n\n🏷️ 相关搜索";
    }

    // ==================== 工具方法 ====================

    static String extractField(String json, String field) {
        java.util.regex.Pattern pStr = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Pattern pArr = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*(\\[[^\\]]*\\])");
        java.util.regex.Pattern pBool = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*(true|false)");
        java.util.regex.Matcher m;
        m = pStr.matcher(json); if (m.find()) return m.group(1).trim();
        m = pArr.matcher(json); if (m.find()) return m.group(1).trim();
        m = pBool.matcher(json); if (m.find()) return m.group(1).trim();
        return "";
    }

    static int countJsonArrayItems(String arr) {
        if (arr == null || arr.isEmpty()) return 0;
        return (int) arr.chars().filter(c -> c == '"').count() / 2;
    }

    static boolean checkAllTitlesShort(String arr, int maxLen) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(arr);
        while (m.find()) { if (m.group(1).length() > maxLen) return false; }
        return true;
    }

    static boolean containsClickbait(String arr) {
        String[] bait = {"震惊", "不看后悔", "速看", "月入百万"};
        for (String b : bait) { if (arr.contains(b)) return true; }
        return false;
    }

    static void eval(String agent, String name, boolean pass, String detail) {
        totalCases++;
        if (pass) passCases++;
        results.computeIfAbsent(agent, k -> new ArrayList<>()).add(new CaseResult(name, pass, detail));
        System.out.printf("  %s %-40s %s\n", pass ? "✓" : "✗", name, pass ? "" : "→ " + detail);
    }

    static String toJson(Map<String, String> m) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ");
            if (e.getValue().startsWith("[") || e.getValue().equals("true") || e.getValue().equals("false"))
                sb.append(e.getValue());
            else sb.append("\"").append(e.getValue()).append("\"");
        }
        return sb.append("}").toString();
    }

    static void printReport() {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║                    Golden Eval 报告                 ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        System.out.printf("总通过率: %d/%d = %.1f%%\n\n", passCases, totalCases, (double) passCases / totalCases * 100);

        System.out.printf("%-20s %-8s %-8s %-10s\n", "Agent", "Pass", "Total", "Rate");
        System.out.println("-".repeat(50));
        for (Map.Entry<String, List<CaseResult>> e : results.entrySet()) {
            long pass = e.getValue().stream().filter(r -> r.passed).count();
            System.out.printf("%-20s %-8d %-8d %-10.1f%%\n", e.getKey(), pass, e.getValue().size(), (double) pass / e.getValue().size() * 100);
        }

        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("评估标准对照表:");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("指标                    公式                         你的结果");
        System.out.println("-".repeat(60));
        System.out.println("Category Accuracy      correct/total                见上方输出");
        System.out.println("Sentiment Accuracy     correct/total                见上方输出");
        System.out.println("Tag Recall             matched/golden               见上方输出");
        System.out.println("Moderation Precision   TP/(TP+FP)                   见上方输出");
        System.out.println("Moderation Recall      TP/(TP+FN)                   见上方输出");
        System.out.println("Moderation F1          2*P*R/(P+R)                 见上方输出");
        System.out.println("False Positive Rate    FP/(FP+TN)  ← 误杀率        见上方输出");
        System.out.println("Title Count=3 Rate     count3/total                 见上方输出");
        System.out.println("Title Length≤15 Rate   shortAll/total               见上方输出");
        System.out.println("No-Clickbait Rate      noBait/total                 见上方输出");
        System.out.println("Keyword Coverage       kwInSummary/total            见上方输出");
        System.out.println("No-Hallucination Rate  noHallu/total                见上方输出");
    }

    record CaseResult(String name, boolean passed, String detail) {}
}
