package com.quanshiguang.shiguang.comment.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@MapperScan("com.quanshiguang.shiguang.comment.biz.domain.mapper")
@EnableRetry // 启用 Spring Retry
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangCommentBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangCommentBizApplication.class, args);
    }

}
