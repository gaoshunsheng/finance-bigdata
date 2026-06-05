# 大数据集群部署指南

## 1. 概述

大数据平台是信贷风控决策引擎的数据底座，负责数据的采集、存储、计算和服务化输出。平台包含以下核心组件：

| 组件 | 版本 | 用途 |
|------|------|------|
| Hadoop HDFS | 3.3.x | 分布式文件存储 |
| Hive | 3.1.x | 离线数仓 & SQL 查询引擎 |
| Kafka | 3.6.x | 实时消息队列 & CDC 管道 |
| Flink | 1.18.x | 实时流计算引擎 |
| HBase | 2.4.x | 实时特征存储 (LSM) |
| DolphinScheduler | 3.2.x | 作业调度平台 |
| Spark | 3.5.x | 离线 ETL 计算 |
| DataX | 3.x | 离线数据同步 |
| Canal | 1.1.x | MySQL CDC 增量采集 |

## 2. 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据源层                                          │
│                                                                             │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────────┐     │
│   │  MySQL   │    │ Oracle   │    │  API     │    │  日志文件         │     │
│   └────┬─────┘    └────┬─────┘    └────┬─────┘    └───────┬──────────┘     │
│        │               │               │                  │                 │
└────────┼───────────────┼───────────────┼──────────────────┼─────────────────┘
         │               │               │                  │
         ▼               ▼               ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            数据采集层                                        │
│                                                                             │
│   ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐     │
│   │  DataX           │    │  Canal (CDC)     │    │  Filebeat        │     │
│   │  全量/增量同步    │    │  Binlog → Kafka  │    │  日志 → Kafka    │     │
│   └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘     │
│            │                       │                        │               │
└────────────┼───────────────────────┼────────────────────────┼───────────────┘
             │                       │                        │
             ▼                       ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         数据存储 & 消息层                                    │
│                                                                             │
│   ┌──────────────────────────────┐    ┌──────────────────────────────┐     │
│   │          HDFS                │    │          Kafka               │     │
│   │  /data/ods (原始层)           │    │  cdc_credit_query            │     │
│   │  /data/dwd (明细层)           │    │  cdc_overdue_event           │     │
│   │  /data/dws (汇总层)           │    │  application_event           │     │
│   │  /data/ads (应用层)           │    │  transaction_event           │     │
│   │                              │    │  cdc_all_events              │     │
│   └──────────────┬───────────────┘    │  app-logs                    │     │
│                  │                    └──────────────┬───────────────┘     │
│                  │                                   │                     │
└──────────────────┼───────────────────────────────────┼─────────────────────┘
                   │                                   │
                   ▼                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           数据计算层                                        │
│                                                                             │
│   ┌──────────────────────────────┐    ┌──────────────────────────────┐     │
│   │     Spark (离线 ETL)         │    │     Flink (实时计算)          │     │
│   │                              │    │                              │     │
│   │  ODS → DWD 清洗转换           │    │  CreditQuery3mJob            │     │
│   │  DWD → DWS 聚合汇总           │    │  Overdue6mJob                │     │
│   │  DWS → ADS 应用指标           │    │  ApplyFreq1mJob              │     │
│   │                              │    │  TransactionSummaryJob       │     │
│   │  调度: DolphinScheduler       │    │  DataQualityCheckJob         │     │
│   └──────────────┬───────────────┘    └──────────────┬───────────────┘     │
│                  │                                   │                     │
└──────────────────┼───────────────────────────────────┼─────────────────────┘
                   │                                   │
                   ▼                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         数据服务层                                          │
│                                                                             │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────────┐     │
│   │  Redis   │    │  HBase   │    │   ES     │    │  data-service    │     │
│   │  热数据   │    │  特征库   │    │  检索    │    │  REST API :8083  │     │
│   │  缓存     │    │  实时特征 │    │  日志    │    │                  │     │
│   └────┬─────┘    └────┬─────┘    └────┬─────┘    └───────┬──────────┘     │
│        │               │               │                  │                 │
└────────┼───────────────┼───────────────┼──────────────────┼─────────────────┘
         │               │               │                  │
         ▼               ▼               ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         决策引擎 (Decision Engine)                           │
│                                                                             │
│   decision-server:8080    decision-admin:8081    model-platform:8082        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. 环境要求

| 项目 | 最低要求 | 推荐配置 |
|------|---------|---------|
| Docker | >= 20.10 | >= 24.0 |
| Docker Compose | >= 2.20 | >= 2.24 |
| 内存 | 16 GB | 32 GB |
| CPU | 4 核 | 8 核 |
| 磁盘 | 100 GB | 500 GB (SSD) |
| 操作系统 | Linux / macOS | Ubuntu 22.04 LTS |

### Docker 资源配置

Docker Desktop 用户需调整资源分配：

```json
{
  "cpus": 8,
  "memory": 32768,
  "disk": "500GB"
}
```

或在 `~/.docker/daemon.json` 中配置：

```json
{
  "max-concurrent-downloads": 10,
  "storage-driver": "overlay2",
  "log-driver": "json-file",
  "log-opts": {"max-size": "100m", "max-file": "3"}
}
```

## 4. 快速启动

### 4.1 启动所有服务

```bash
cd docs/deployment

# 启动基础服务 (MySQL, Redis, ES) 和应用服务
docker compose up -d

# 等待健康检查通过
docker compose ps --format "table {{.Name}}\t{{.Status}}"

# 查看启动日志
docker compose logs -f
```

### 4.2 等待健康检查

所有服务启动后需等待健康检查通过（约 2-5 分钟）：

```bash
# 一键检查所有服务状态
docker compose ps

# 逐个验证基础设施
curl -sf http://localhost:9200/_cluster/health?pretty   # ES
redis-cli -a redis_pass_2024 ping                        # Redis
mysql -h 127.0.0.1 -u credit_admin -pcredit_pass_2024 \
  -e "SELECT 1"                                          # MySQL
```

### 4.3 初始化 Hive 数据库

```bash
# 通过 beeline 执行 DDL 脚本（按层级顺序）
# 1. ODS 层
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/01_create_ods_database.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/02_ods_loan_application.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/03_ods_customer_info.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/04_ods_credit_report.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/05_ods_repayment_record.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/06_ods_external_data.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ods/07_ods_decision_log.sql

# 2. DWD 层
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dwd/01_create_dwd_database.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dwd/02_dwd_loan_application_detail.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dwd/03_dwd_customer_profile.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dwd/04_dwd_credit_event.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dwd/05_dwd_transaction_detail.sql

# 3. DWS 层
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dws/01_create_dws_database.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dws/02_dws_customer_credit_summary.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dws/03_dws_product_loan_summary.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/dws/04_dws_channel_summary.sql

# 4. ADS 层
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ads/01_create_ads_database.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ads/02_ads_credit_score_wide_table.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ads/03_ads_risk_indicator_summary.sql
beeline -u "jdbc:hive2://localhost:10000" -f data-warehouse/hive/ads/04_ads_decision_analysis.sql
```

## 5. 组件端口列表

| 服务 | 端口 | 访问地址 | 说明 |
|------|------|---------|------|
| MySQL | 3306 | `localhost:3306` | 关系型数据库 |
| Redis | 6379 | `localhost:6379` | 缓存 & 热数据 |
| Elasticsearch | 9200 | `http://localhost:9200` | 全文检索 & 日志 |
| Kafka | 9092 | `localhost:9092` | 消息队列 |
| Kafka UI | 9093 | `http://localhost:9093` | Kafka 管理界面 (可选) |
| HDFS NameNode | 9870 | `http://localhost:9870` | HDFS Web UI |
| HDFS DataNode | 9864 | `http://localhost:9864` | DataNode Web UI |
| Hive Server2 | 10000 | `localhost:10000` | Hive JDBC (beeline) |
| Hive Metastore | 9083 | `localhost:9083` | Metastore Thrift |
| HBase Master | 16010 | `http://localhost:16010` | HBase Web UI |
| HBase Thrift | 9090 | `localhost:9090` | HBase Thrift 接口 |
| Flink JobManager | 8081 | `http://localhost:8081` | Flink Web UI |
| DolphinScheduler | 12345 | `http://localhost:12345/dolphinscheduler` | 调度平台 UI |
| data-service | 8083 | `http://localhost:8083` | 数据服务 REST API |
| decision-server | 8080 | `http://localhost:8080` | 决策引擎 |
| decision-admin | 8081 | `http://localhost:8081` | 管理后台 |
| model-platform | 8082 | `http://localhost:8082` | 模型平台 |

## 6. Kafka Topic 初始化

```bash
# 进入 Kafka 容器
docker exec -it finance-kafka bash

# 创建 Topic
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic cdc_credit_query --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic cdc_overdue_event --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic application_event --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic transaction_event --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic cdc_all_events --partitions 3 --replication-factor 1

kafka-topics --bootstrap-server localhost:9092 \
  --create --topic app-logs --partitions 3 --replication-factor 1

# 验证 Topic 列表
kafka-topics --bootstrap-server localhost:9092 --list

# 查看 Topic 详情
kafka-topics --bootstrap-server localhost:9092 --describe --topic cdc_credit_query
```

### Topic 说明

| Topic | 分区数 | 生产者 | 消费者 | 说明 |
|-------|-------|--------|--------|------|
| cdc_credit_query | 3 | Canal | Flink CreditQuery3mJob | 征信查询 CDC 事件 |
| cdc_overdue_event | 3 | Canal | Flink Overdue6mJob | 逾期事件 CDC |
| application_event | 3 | decision-server | Flink ApplyFreq1mJob | 申请事件 |
| transaction_event | 3 | decision-server | Flink TransactionSummaryJob | 交易事件 |
| cdc_all_events | 3 | Canal | Flink DataQualityCheckJob | 全量 CDC 事件 (数据质量) |
| app-logs | 3 | Filebeat | ES (Logstash) | 应用日志 |

## 7. HDFS 目录初始化

```bash
# 进入 NameNode 容器或使用 HDFS 客户端
hdfs dfs -mkdir -p /data/ods
hdfs dfs -mkdir -p /data/dwd
hdfs dfs -mkdir -p /data/dws
hdfs dfs -mkdir -p /data/ads

# 设置目录权限
hdfs dfs -chmod -R 775 /data
hdfs dfs -chown -R hdfs:hadoop /data

# 验证目录结构
hdfs dfs -ls -R /data
```

预期输出：

```
/data/ods
/data/dwd
/data/dws
/data/ads
```

## 8. HBase 建表

```bash
# 进入 HBase Shell
hbase shell

# 执行建表脚本
# 1. 客户特征表
source 'data-warehouse/hbase/01_create_feature_table.hbase'

# 2. 历史记录表
source 'data-warehouse/hbase/02_create_history_table.hbase'

# 3. 外部数据表
source 'data-warehouse/hbase/03_create_external_data_table.hbase'

# 验证表列表
list

# 验证表结构
describe 'customer_feature'
```

### HBase 表清单

| 表名 | 列族 | 预分区 | 压缩 | TTL | 说明 |
|------|------|--------|------|-----|------|
| customer_feature | cf | 16 | SNAPPY | 3 年 | 客户特征 (评分/逾期/查询等) |
| credit_history | ch | 16 | SNAPPY | 5 年 | 信贷历史记录 |
| external_data | ed | 8 | SNAPPY | 1 年 | 外部数据 (征信/三方) |

## 9. Hive 建表

详见 [第 4.3 节](#43-初始化-hive-数据库)，按 ODS -> DWD -> DWS -> ADS 顺序执行。

也可以批量执行：

```bash
#!/bin/bash
HIVE_JDBC="jdbc:hive2://localhost:10000"
SCRIPTS_DIR="../../data-warehouse/hive"

for layer in ods dwd dws ads; do
  echo "=== 初始化 ${layer} 层 ==="
  for sql_file in $(ls ${SCRIPTS_DIR}/${layer}/*.sql | sort); do
    echo "  执行: $(basename ${sql_file})"
    beeline -u "${HIVE_JDBC}" -f "${sql_file}"
  done
done

echo "=== Hive 建表完成 ==="
```

### Hive 数仓分层说明

```
ODS (原始数据层)
├── ods_loan_application     -- 贷款申请原始数据
├── ods_customer_info        -- 客户信息原始数据
├── ods_credit_report        -- 征信报告原始数据
├── ods_repayment_record     -- 还款记录原始数据
├── ods_external_data        -- 外部数据原始数据
└── ods_decision_log         -- 决策日志原始数据

DWD (明细数据层)
├── dwd_loan_application_detail -- 清洗后的贷款申请明细
├── dwd_customer_profile        -- 标准化客户画像
├── dwd_credit_event            -- 信贷事件明细
└── dwd_transaction_detail      -- 交易明细

DWS (汇总数据层)
├── dws_customer_credit_summary -- 客户信用汇总
├── dws_product_loan_summary    -- 产品贷款汇总
└── dws_channel_summary         -- 渠道汇总

ADS (应用数据层)
├── ads_credit_score_wide_table   -- 信用评分宽表
├── ads_risk_indicator_summary    -- 风险指标汇总
└── ads_decision_analysis         -- 决策分析表
```

## 10. Flink 作业提交

### 10.1 构建 Flink 作业 JAR

```bash
cd data-platform
mvn clean package -pl flink-jobs -am -DskipTests

# JAR 输出路径
ls flink-jobs/target/flink-jobs-*.jar
```

### 10.2 通过 Flink Web UI 提交

1. 打开 `http://localhost:8081`
2. 点击 "Submit New Job" -> "Add New+" 上传 JAR
3. 选择入口类，填写参数，点击 Submit

### 10.3 通过 CLI 提交

```bash
FLINK_BIN=/opt/flink/bin
JAR_PATH=flink-jobs/target/flink-jobs-1.0-SNAPSHOT.jar
KAFKA_SERVERS=kafka:9092
HBASE_QUORUM=hbase:2181

# 作业 1: 近 3 个月征信查询次数统计
${FLINK_BIN}/flink run -d \
  -c com.credit.platform.data.flink.credit_query.CreditQuery3mJob \
  ${JAR_PATH} \
  --kafka.bootstrap-servers ${KAFKA_SERVERS} \
  --hbase.quorum ${HBASE_QUORUM}

# 作业 2: 近 6 个月逾期统计
${FLINK_BIN}/flink run -d \
  -c com.credit.platform.data.flink.overdue.Overdue6mJob \
  ${JAR_PATH} \
  --kafka.bootstrap-servers ${KAFKA_SERVERS} \
  --hbase.quorum ${HBASE_QUORUM}

# 作业 3: 近 1 个月申请频率统计
${FLINK_BIN}/flink run -d \
  -c com.credit.platform.data.flink.apply_freq.ApplyFreq1mJob \
  ${JAR_PATH} \
  --kafka.bootstrap-servers ${KAFKA_SERVERS} \
  --hbase.quorum ${HBASE_QUORUM}

# 作业 4: 交易汇总统计
${FLINK_BIN}/flink run -d \
  -c com.credit.platform.data.flink.transaction.TransactionSummaryJob \
  ${JAR_PATH} \
  --kafka.bootstrap-servers ${KAFKA_SERVERS} \
  --hbase.quorum ${HBASE_QUORUM}

# 作业 5: 数据质量检查
${FLINK_BIN}/flink run -d \
  -c com.credit.platform.data.flink.quality.DataQualityCheckJob \
  ${JAR_PATH} \
  --kafka.bootstrap-servers ${KAFKA_SERVERS} \
  --hbase.quorum ${HBASE_QUORUM}
```

### 10.4 Flink 作业清单

| 作业 | 入口类 | Source Topic | Sink | 说明 |
|------|--------|-------------|------|------|
| CreditQuery3mJob | `...credit_query.CreditQuery3mJob` | cdc_credit_query | HBase | 近 3 月征信查询次数 |
| Overdue6mJob | `...overdue.Overdue6mJob` | cdc_overdue_event | HBase | 近 6 月逾期统计 |
| ApplyFreq1mJob | `...apply_freq.ApplyFreq1mJob` | application_event | HBase | 近 1 月申请频率 |
| TransactionSummaryJob | `...transaction.TransactionSummaryJob` | transaction_event | HBase | 交易汇总 |
| DataQualityCheckJob | `...quality.DataQualityCheckJob` | cdc_all_events | Kafka + HBase | 数据质量检查 |

## 11. 验证

### 11.1 基础服务验证

```bash
# HDFS 健康
curl -sf http://localhost:9870/jmx?qry=Hadoop:service=NameNode,name=NameNodeInfo

# Kafka Topic 列表
docker exec finance-kafka kafka-topics --bootstrap-server localhost:9092 --list

# HBase 表列表
echo "list" | hbase shell

# Hive 连接测试
beeline -u "jdbc:hive2://localhost:10000" -e "SHOW DATABASES;"

# Flink 运行作业
curl -sf http://localhost:8081/jobs/overview | python3 -m json.tool
```

### 11.2 数据流验证

```bash
# 1. 发送测试消息到 Kafka
docker exec finance-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 --topic cdc_credit_query
# 输入 JSON 测试数据后 Ctrl+D

# 2. 消费验证
docker exec finance-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic cdc_credit_query --from-beginning

# 3. HBase 特征验证
echo "scan 'customer_feature', {LIMIT => 5}" | hbase shell

# 4. ES 索引验证
curl -sf http://localhost:9200/_cat/indices?v

# 5. data-service API 验证
curl -sf http://localhost:8083/api/v1/features/CUST001 | python3 -m json.tool
```

### 11.3 DolphinScheduler 验证

1. 访问 `http://localhost:12345/dolphinscheduler`
2. 默认账号: `admin` / `dolphinscheduler123`
3. 创建工作流并配置 Spark ETL 定时调度

## 12. 常用操作

### 停止所有服务

```bash
docker compose down          # 停止容器，保留数据卷
docker compose down -v       # 停止并删除所有数据卷 (慎用)
```

### 查看日志

```bash
docker compose logs -f kafka             # 跟踪 Kafka 日志
docker compose logs --tail 100 flink     # Flink 最近 100 行日志
docker compose logs -f hbase            # 跟踪 HBase 日志
```

### 资源监控

```bash
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"
```
