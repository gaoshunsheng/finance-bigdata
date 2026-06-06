package com.credit.platform.data.service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Trino 配置 — 用于 ADS 层数据查询和 BI 报表。
 *
 * <p>连接参数从 application.yml 读取，支持环境变量覆盖。
 * <p>数据源: hive catalog, ads schema (T+1 离线数据)
 */
@Configuration
public class TrinoConfig {

    private static final Logger log = LoggerFactory.getLogger(TrinoConfig.class);

    @Value("${trino.url:jdbc:trino://localhost:8086}")
    private String url;

    @Value("${trino.user:admin}")
    private String user;

    @Value("${trino.password:}")
    private String password;

    @Value("${trino.catalog:hive}")
    private String catalog;

    @Value("${trino.schema:default}")
    private String schema;

    /**
     * 创建 Trino JDBC 连接。
     * 注意: 每次调用 query 时获取新连接，避免连接池管理复杂性。
     * 生产环境建议使用 HikariCP 连接池。
     *
     * @return Trino JDBC Connection
     * @throws SQLException 连接失败时抛出
     */
    public Connection createConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        String jdbcUrl = url + "/" + catalog + "/" + schema;
        log.info("创建 Trino 连接: url={}, user={}, catalog={}, schema={}", url, user, catalog, schema);
        return DriverManager.getConnection(jdbcUrl, props);
    }

    public String getUrl() {
        return url;
    }

    public String getCatalog() {
        return catalog;
    }

    public String getSchema() {
        return schema;
    }
}
