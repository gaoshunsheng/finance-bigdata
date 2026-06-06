# data-platform

> 大数据平台，包含 Flink 实时计算、数据服务 API 和数据治理框架三个子模块。

## 功能概述

- **实时特征计算** — 5 个 Flink 流处理作业，从 Kafka 消费事件并计算实时特征，写入 Redis/HBase
- **数据服务 API** — 特征查询、企业画像、BI 报表，支持 Redis -> HBase 二级查询策略
- **数据治理** — 元数据管理、数据血缘分析、五维数据质量监控、数据安全和生命周期管理

## 子模块结构

```
data-platform/
├── flink-jobs/         — Flink 实时计算作业
├── data-service/       — 数据服务 Spring Boot 应用
├── data-governance/    — 数据治理框架库
└── pom.xml             — 父 POM，统一管理 Flink/Kafka/HBase/Hadoop/ES 版本
```

## 关键版本

| 组件 | 版本 |
|------|------|
| Flink | 1.18.1 |
| Kafka | 3.6.1 |
| HBase | 2.5.5-hadoop3 |
| Hadoop | 3.3.6 |
| Elasticsearch | 8.13.4 |

## 构建与运行

```bash
# 构建全部子模块
mvn clean package -pl data-platform -am

# 仅构建某个子模块
mvn clean package -pl data-platform/flink-jobs -am
mvn clean package -pl data-platform/data-service -am
mvn clean package -pl data-platform/data-governance -am
```

## 测试

```bash
mvn test -pl data-platform
```

## 依赖关系

- **依赖**: `engine-common`
- **被依赖**: `decision-server`（通过 `data-service` 查询特征数据）
