package com.quanshiguang.shiguang.search.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.quanshiguang.shiguang.search.biz.domain.mapper")
public class ShiguangSearchBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangSearchBizApplication.class, args);
    }

}
