package com.credit.platform.data.service.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Trino 配置 — 用于 ADS 层数据查询和 BI 报表。
 *
 * <p>连接参数从 application.yml 读取，支持环境变量覆盖。
 * <p>数据源: hive catalog, ads schema (T+1 离线数据)
 * <p>安全修复: 使用 HikariCP 连接池，避免每次查询新建 JDBC 连接。
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

    @Value("${trino.pool.max-size:5}")
    private int poolMaxSize;

    @Value("${trino.pool.min-idle:1}")
    private int poolMinIdle;

    @Value("${trino.pool.idle-timeout-ms:300000}")
    private long poolIdleTimeoutMs;

    private HikariDataSource dataSource;

    /**
     * 创建 HikariCP 连接池。
     */
    @Bean
    public HikariDataSource trinoDataSource() {
        String jdbcUrl = url + "/" + catalog + "/" + schema;

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user);
        if (password != null && !password.isEmpty()) {
            hc.setPassword(password);
        }
        hc.setMaximumPoolSize(poolMaxSize);
        hc.setMinimumIdle(poolMinIdle);
        hc.setIdleTimeout(poolIdleTimeoutMs);
        hc.setConnectionTimeout(10000);
        hc.setPoolName("trino-pool");
        // Trino 使用 PostgreSQL 驱动的精简版
        hc.setDriverClassName("io.trino.jdbc.TrinoDriver");

        this.dataSource = new HikariDataSource(hc);
        log.info("Trino 连接池初始化: url={}, catalog={}, schema={}, maxPool={}",
            url, catalog, schema, poolMaxSize);
        return this.dataSource;
    }

    /**
     * 从连接池获取 Trino JDBC 连接。
     *
     * @return Trino JDBC Connection
     * @throws SQLException 连接失败时抛出
     */
    public Connection createConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Trino DataSource 未初始化");
        }
        return dataSource.getConnection();
    }

    @PreDestroy
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Trino 连接池已关闭");
        }
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
