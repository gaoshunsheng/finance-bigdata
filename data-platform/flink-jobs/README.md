# flink-jobs

> Flink 实时特征计算作业，包含 5 个流处理作业和公共组件，从 Kafka 消费事件并计算实时特征。

## 功能概述

- **信贷查询 3 月频次** (FLINK_001) — 统计客户近 3 个月的信贷查询次数
- **逾期 6 月统计** (FLINK_002) — 统计客户近 6 个月的逾期天数和逾期次数
- **申请频次 1 月统计** (FLINK_003) — 统计客户近 1 个月的贷款申请次数
- **交易金额汇总** (FLINK_004) — 按客户维度，1 小时滚动窗口聚合交易总额、交易笔数和平均金额
- **数据质量检测** (FLINK_005) — 实时数据质量检测，异常数据写入 Elasticsearch 告警

## 包结构

```
src/main/java/com/credit/platform/data/flink/
├── common/                — 公共组件
│   ├── KafkaSourceFactory.java      — Kafka Source 工厂
│   ├── EventDeserializer.java       — 事件 JSON 反序列化器
│   ├── FeatureKey.java              — 特征 Key 构建工具
│   ├── RedisFeatureSink.java        — Redis 特征写入 Sink
│   ├── HBaseFeatureSink.java        — HBase 特征写入 Sink
│   └── ElasticsearchAlertSink.java  — ES 告警 Sink
├── credit_query/          — FLINK_001 信贷查询频次
│   └── CreditQuery3mJob.java
├── overdue/               — FLINK_002 逾期统计
│   └── Overdue6mJob.java
├── apply_freq/            — FLINK_003 申请频次
│   └── ApplyFreq1mJob.java
├── transaction/           — FLINK_004 交易汇总
│   └── TransactionSummaryJob.java
└── quality/               — FLINK_005 数据质量
    └── DataQualityCheckJob.java
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `CreditQuery3mJob` | FLINK_001: 从 Kafka 消费信贷查询事件，按客户统计 3 月查询频次 |
| `Overdue6mJob` | FLINK_002: 从 Kafka 消费逾期事件，按客户统计 6 月逾期天数和次数 |
| `ApplyFreq1mJob` | FLINK_003: 从 Kafka 消费申请事件，按客户统计 1 月申请频次 |
| `TransactionSummaryJob` | FLINK_004: 从 Kafka 消费交易事件，1 小时窗口聚合交易总额/笔数/均值 |
| `DataQualityCheckJob` | FLINK_005: 实时数据质量检测，异常告警写入 ES |
| `KafkaSourceFactory` | Kafka Source 统一构建工厂 |
| `RedisFeatureSink` | 特征数据写入 Redis（Key: `feature:{type}:{customerId}`） |
| `HBaseFeatureSink` | 特征数据写入 HBase（Table: `customer_feature`） |
| `ElasticsearchAlertSink` | 数据质量告警写入 Elasticsearch |

## 数据流

```
Kafka Topic → Flink Job → Redis (热查询) + HBase (持久化)
                          → ES (质量告警)
```

| Kafka Topic | Flink Job | 输出特征 Key |
|-------------|-----------|-------------|
| `credit_query_event` | CreditQuery3mJob | `credit_query_3m` |
| `overdue_event` | Overdue6mJob | `overdue_6m` |
| `apply_event` | ApplyFreq1mJob | `apply_freq_1m` |
| `transaction_event` | TransactionSummaryJob | `transaction_summary_1h` |

## 构建与运行

```bash
# 构建（shade 打包）
mvn clean package -pl data-platform/flink-jobs -am

# 提交到 Flink 集群
flink run -c com.credit.platform.data.flink.transaction.TransactionSummaryJob \
  flink-jobs-1.0.0-SNAPSHOT.jar
```

环境变量配置：

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka 集群地址 |
| `KAFKA_TOPIC` | 各 Job 专属 Topic | 消费的 Kafka Topic |
| `REDIS_URI` | `redis://localhost:6379` | Redis 连接 |
| `HBASE_ZK_QUORUM` | `localhost` | HBase ZooKeeper 地址 |
| `HBASE_ZK_PORT` | `2181` | HBase ZooKeeper 端口 |

## 测试

```bash
mvn test -pl data-platform/flink-jobs
```

## 依赖关系

- **依赖**: Flink 1.18.1、Kafka Client 3.6.1、HBase Client 2.5.5、Redis (Lettuce)、Elasticsearch 8.13.4
- **被依赖**: 无（独立作业）
