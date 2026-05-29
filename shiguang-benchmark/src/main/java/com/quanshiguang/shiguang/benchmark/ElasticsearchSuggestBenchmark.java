package com.quanshiguang.shiguang.benchmark;

import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;

import java.util.*;

public class ElasticsearchSuggestBenchmark {

    static final String ES_HOST = "localhost";
    static final int ES_PORT = 9200;
    static final String INDEX_NAME = "bench_suggest";
    static final int DOC_COUNT = 100_000;
    static final int QUERY_COUNT = 10_000;
    static final int WARMUP = 3;
    static final int RUNS = 10;

    public static void main(String[] args) {
        RestHighLevelClient client;
        try {
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(ES_HOST, ES_PORT, "http")));
            client.ping(RequestOptions.DEFAULT);
        } catch (Exception e) {
            System.out.println("=== Elasticsearch 未启动，跳过压测 ===");
            System.out.println("启动方式: docker run -d -p 9200:9200 -e discovery.type=single-node elasticsearch:7.3.0");
            return;
        }

        System.out.println("=== ES Completion Suggester vs MySQL LIKE 延迟压测 ===");
        System.out.printf("ES: %s:%d\n", ES_HOST, ES_PORT);
        System.out.printf("文档数: %,d, 查询次数: %,d\n\n", DOC_COUNT, QUERY_COUNT);

        try {
            prepareIndex(client);
            benchmarkSuggest(client);
            benchmarkMatchQuery(client);
        } catch (Exception e) {
            System.err.println("压测异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                client.indices().delete(new DeleteIndexRequest(INDEX_NAME), RequestOptions.DEFAULT);
            } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    static void prepareIndex(RestHighLevelClient client) throws Exception {
        try {
            client.indices().delete(new DeleteIndexRequest(INDEX_NAME), RequestOptions.DEFAULT);
        } catch (Exception ignored) {}

        String mapping = "{\n" +
                "  \"mappings\": {\n" +
                "    \"properties\": {\n" +
                "      \"title\": { \"type\": \"keyword\" },\n" +
                "      \"title_suggest\": { \"type\": \"completion\" }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        client.indices().create(new CreateIndexRequest(INDEX_NAME).source(mapping, XContentType.JSON), RequestOptions.DEFAULT);

        String[] prefixes = {"Spring", "Java", "Python", "React", "Vue", "Go", "Rust", "Docker", "Kubernetes", "Redis",
                "MySQL", "MongoDB", "Elasticsearch", "Kafka", "RabbitMQ", "Nginx", "Git", "Linux", "AWS", "微服务"};
        String[] suffixes = {"入门教程", "实战项目", "源码解析", "性能优化", "最佳实践", "面试指南", "原理剖析", "架构设计", "踩坑记录", "进阶指南"};

        Random random = new Random(42);
        for (int i = 0; i < DOC_COUNT; i++) {
            String title = prefixes[random.nextInt(prefixes.length)] + " " + suffixes[random.nextInt(suffixes.length)] + " " + i;
            String doc = String.format("{\"title\":\"%s\",\"title_suggest\":{\"input\":[\"%s\"]}}", title, title);
            client.index(new IndexRequest(INDEX_NAME).source(doc, XContentType.JSON), RequestOptions.DEFAULT);
        }

        System.out.printf("已写入 %,d 条文档\n", DOC_COUNT);
        Thread.sleep(2000);
    }

    static void benchmarkSuggest(RestHighLevelClient client) throws Exception {
        System.out.println("\n=== Completion Suggester 延迟 ===");

        String[] prefixes = {"Sp", "Ja", "Py", "Re", "Vu", "Go", "Ru", "Do", "Ku", "Re"};
        Random random = new Random(42);

        List<Long> latencies = new ArrayList<>();
        for (int r = 0; r < WARMUP + RUNS; r++) {
            for (int i = 0; i < QUERY_COUNT; i++) {
                String prefix = prefixes[random.nextInt(prefixes.length)];

                long start = System.nanoTime();
                SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                sourceBuilder.size(0);
                SuggestBuilder suggestBuilder = new SuggestBuilder();
                suggestBuilder.addSuggestion("title-suggest",
                        SuggestBuilders.completionSuggestion("title_suggest")
                                .prefix(prefix)
                                .size(10));
                sourceBuilder.suggest(suggestBuilder);
                searchRequest.source(sourceBuilder);
                client.search(searchRequest, RequestOptions.DEFAULT);
                long elapsed = System.nanoTime() - start;

                if (r >= WARMUP) latencies.add(elapsed / 1_000_000);
            }
        }

        printLatencyStats("Completion Suggester", latencies);
    }

    static void benchmarkMatchQuery(RestHighLevelClient client) throws Exception {
        System.out.println("\n=== Match Query 延迟 (对比) ===");

        String[] keywords = {"Spring", "Java", "Python", "React", "Vue"};
        Random random = new Random(42);

        List<Long> latencies = new ArrayList<>();
        for (int r = 0; r < WARMUP + RUNS; r++) {
            for (int i = 0; i < QUERY_COUNT; i++) {
                String keyword = keywords[random.nextInt(keywords.length)];

                long start = System.nanoTime();
                SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                sourceBuilder.query(QueryBuilders.matchQuery("title", keyword));
                sourceBuilder.size(10);
                searchRequest.source(sourceBuilder);
                client.search(searchRequest, RequestOptions.DEFAULT);
                long elapsed = System.nanoTime() - start;

                if (r >= WARMUP) latencies.add(elapsed / 1_000_000);
            }
        }

        printLatencyStats("Match Query", latencies);
    }

    static void printLatencyStats(String name, List<Long> latencies) {
        Collections.sort(latencies);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double avg = latencies.stream().mapToLong(l -> l).average().orElse(0);
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p90 = latencies.get((int) (latencies.size() * 0.90));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        System.out.printf("[%s]\n", name);
        System.out.printf("  Min: %dms, Avg: %.1fms, Max: %dms\n", min, avg, max);
        System.out.printf("  P50: %dms, P90: %dms, P99: %dms\n", p50, p90, p99);
    }
}
