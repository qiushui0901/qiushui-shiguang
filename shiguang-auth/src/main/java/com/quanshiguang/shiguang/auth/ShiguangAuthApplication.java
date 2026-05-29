package com.quanshiguang.shiguang.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangAuthApplication.class, args);
    }

}
