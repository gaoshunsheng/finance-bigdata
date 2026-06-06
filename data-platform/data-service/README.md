# data-service

> 数据服务 API，Spring Boot 应用，提供特征查询、企业画像和 BI 报表服务。

## 功能概述

- **特征查询** — Redis -> HBase 二级查询策略，目标 P99 < 20ms
- **企业画像** — 企业客户信息查询和画像构建
- **BI 报表** — 基于 Trino 的 OLAP 报表查询
- **多数据源整合** — Redis、HBase、Elasticsearch、Trino 四种数据源统一服务

## 包结构

```
src/main/java/com/credit/platform/data/service/
├── DataServiceApplication.java  — Spring Boot 启动类
├── config/                      — 数据源配置
│   ├── RedisConfig.java             — Redis 连接配置
│   ├── HBaseConfig.java             — HBase 连接配置
│   ├── ESConfig.java                — Elasticsearch 配置
│   └── TrinoConfig.java             — Trino JDBC 配置
├── controller/                  — REST API
│   ├── FeatureController.java       — 特征查询接口
│   ├── EnterpriseProfileController.java — 企业画像接口
│   └── ReportController.java        — BI 报表接口
├── service/                     — 业务服务
│   ├── FeatureQueryService.java     — 特征查询服务（核心）
│   ├── EnterpriseProfileService.java — 企业画像服务
│   ├── TrinoQueryService.java       — Trino OLAP 查询
│   └── ReportService.java           — 报表服务
└── model/                       — 数据模型
    ├── CustomerFeatures.java        — 客户特征模型
    └── ApiResponse.java             — 统一响应
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `DataServiceApplication` | Spring Boot 启动类，端口 8083 |
| `FeatureQueryService` | 特征查询核心服务，实现 Redis -> HBase 二级查询，Redis 未命中时自动回查 HBase 并回写缓存 |
| `TrinoQueryService` | Trino OLAP 查询服务，执行 SQL 查询 Hive 数据湖 |
| `EnterpriseProfileService` | 企业画像服务 |
| `ReportService` | BI 报表服务 |
| `CustomerFeatures` | 客户特征数据模型，包含特征 Map、来源标识和查询耗时 |

## 配置项

`application.yml` 中的关键配置：

```yaml
server:
  port: 8083

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 3000ms

hbase:
  zookeeper:
    quorum: ${HBASE_ZK_QUORUM:localhost}
    property:
      clientPort: ${HBASE_ZK_PORT:2181}

elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}

trino:
  url: ${TRINO_URL:jdbc:trino://localhost:8086}
  user: ${TRINO_USER:admin}
  catalog: ${TRINO_CATALOG:hive}
  schema: ${TRINO_SCHEMA:default}

kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

## 特征查询策略

```
请求 → Redis 查询 → 命中 → 返回（亚毫秒）
                  → 未命中 → HBase 查询 → 命中 → 回写 Redis（TTL 1h）→ 返回（毫秒级）
                                        → 未命中 → 返回空
```

特征 Key 命名规范：`feature:{featureType}:{customerId}`

支持的 featureType：
- `credit_query_3m` — 3 月信贷查询频次
- `overdue_6m` — 6 月逾期统计
- `apply_freq_1m` — 1 月申请频次
- `transaction_summary_1h` — 1 小时交易汇总
- `credit_score` — 信用评分
- `risk_level` — 风险等级

## 构建与运行

```bash
# 构建
mvn clean package -pl data-platform/data-service -am

# 运行
java -jar data-platform/data-service/target/data-service-1.0.0-SNAPSHOT.jar
```

## 测试

```bash
mvn test -pl data-platform/data-service
```

## 依赖关系

- **依赖**: `engine-common`、Spring Boot、Redis (Lettuce)、HBase Client、Elasticsearch、Trino JDBC
- **被依赖**: `decision-ui`（前端代理）、`decision-server`（间接通过 HTTP 调用）
