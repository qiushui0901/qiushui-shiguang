package com.quanshiguang.shiguang.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.quanshiguang.shiguang.user.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
@ComponentScan({"com.quanshiguang.shiguang.user.biz", "com.quanshiguang.shiguang.count"}) //  多模块扫描
public class ShiguangUserBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangUserBizApplication.class, args);
    }

}
