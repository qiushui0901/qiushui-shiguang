package com.quanshiguang.shiguang.agent.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangAgentBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangAgentBizApplication.class, args);
    }
}
