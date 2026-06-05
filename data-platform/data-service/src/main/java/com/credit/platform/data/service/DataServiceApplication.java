package com.credit.platform.data.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据服务 API 启动类 — 提供特征查询、企业画像、BI 报表等数据服务。
 *
 * <p>对外暴露的 API:
 * <ul>
 *   <li>GET /api/v1/features/{customerId} — 客户实时特征查询（P99 < 20ms）</li>
 *   <li>GET /api/v1/profile/{enterpriseId} — 企业画像查询（P99 < 500ms）</li>
 *   <li>GET /api/v1/reports/{type} — BI 报表查询</li>
 * </ul>
 */
@SpringBootApplication
public class DataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataServiceApplication.class, args);
    }
}
