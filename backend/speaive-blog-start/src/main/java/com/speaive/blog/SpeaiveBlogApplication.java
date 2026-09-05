package com.speaive.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端唯一 Spring Boot 启动入口，四个 Maven 模块在这里组成一个进程与一个部署单元。
 */
@SpringBootApplication
public class SpeaiveBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeaiveBlogApplication.class, args);
    }
}
