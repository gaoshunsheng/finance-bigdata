package com.credit.platform.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 决策管理后台 — Spring Boot 启动类。
 * <p>
 * 提供规则/评分卡/决策流/变量的 CRUD 管理 API。
 * 使用 MyBatis-Plus + MySQL 持久化。
 * </p>
 */
@SpringBootApplication
@MapperScan("com.credit.platform.admin.mapper")
public class DecisionAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionAdminApplication.class, args);
    }
}
