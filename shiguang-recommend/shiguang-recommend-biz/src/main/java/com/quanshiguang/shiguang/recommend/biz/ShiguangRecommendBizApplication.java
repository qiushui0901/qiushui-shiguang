package com.quanshiguang.shiguang.recommend.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author: 犬小哈
 * @version: v1.0.0
 * @description: 推荐服务启动类
 **/
@SpringBootApplication
@MapperScan("com.quanshiguang.shiguang.recommend.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangRecommendBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangRecommendBizApplication.class, args);
    }

}
