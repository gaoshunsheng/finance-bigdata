package com.credit.platform.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 决策管理后台 — Spring Boot 启动类。
 * <p>
 * 提供规则/评分卡/决策流/变量的 CRUD 管理 API。
 * </p>
 */
@SpringBootApplication
public class DecisionAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionAdminApplication.class, args);
    }
}
