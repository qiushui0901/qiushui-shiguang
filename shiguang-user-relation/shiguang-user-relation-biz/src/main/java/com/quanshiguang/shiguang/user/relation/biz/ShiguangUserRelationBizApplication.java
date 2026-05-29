package com.quanshiguang.shiguang.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.quanshiguang.shiguang.user.relation.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangUserRelationBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangUserRelationBizApplication.class, args);
    }

}
