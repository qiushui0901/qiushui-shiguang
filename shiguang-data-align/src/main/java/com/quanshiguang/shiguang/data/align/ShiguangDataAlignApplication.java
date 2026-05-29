package com.quanshiguang.shiguang.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.quanshiguang.shiguang.data.align.domain.mapper")
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangDataAlignApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangDataAlignApplication.class, args);
    }

}
