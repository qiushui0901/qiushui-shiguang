package com.quanshiguang.shiguang.recommend.biz.config;

import jakarta.annotation.Resource;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Resource
    private ElasticsearchProperties elasticsearchProperties;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        String[] parts = elasticsearchProperties.getAddress().split(":");
        return new RestHighLevelClient(RestClient.builder(new HttpHost(parts[0], Integer.parseInt(parts[1]), "http")));
    }
}
