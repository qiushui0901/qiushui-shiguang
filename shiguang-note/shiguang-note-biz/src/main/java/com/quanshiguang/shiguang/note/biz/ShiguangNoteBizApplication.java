package com.quanshiguang.shiguang.note.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.quanshiguang.shiguang.note.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.quanshiguang.shiguang")
public class ShiguangNoteBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiguangNoteBizApplication.class, args);
    }

}
