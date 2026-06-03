package com.credit.platform.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 信贷决策引擎服务 — Spring Boot 启动类。
 * <p>
 * 集成 engine-core 核心引擎，提供 REST API 决策服务。
 * </p>
 */
@SpringBootApplication(scanBasePackages = "com.credit.platform.server")
@EnableAsync
public class DecisionServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionServerApplication.class, args);
    }
}
