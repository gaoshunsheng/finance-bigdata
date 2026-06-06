# 全流程测试数据生成器

为 finance-bigdata 平台生成批量历史数据和实时数据流，并提供全链路验证。

## 快速开始

```bash
# 1. 安装依赖
pip install -r requirements.txt

# 2. 生成批量历史数据 (10,000 客户 × 12 个月)
python batch_data_gen.py

# 3. 启动实时数据流 (每秒 20 事件)
python realtime_gen.py --rate 20

# 4. 全链路验证
python verify_pipeline.py --checkpoint ALL
```

## 工具说明

### batch_data_gen.py — 批量历史数据生成

生成 4 张 MySQL 源表数据: customer_info, loan_application, repayment_record, credit_report。

```bash
python batch_data_gen.py [--customers 10000] [--months 12] [--start-date 2025-07-01] [--seed 42]
```

输出 `output/expected_counts.json` 作为验证基线。

### realtime_gen.py — 实时数据模拟

双路径写入: MySQL INSERT (触发 Canal CDC) + Kafka 扁平事件 (供 Flink 消费)。

```bash
python realtime_gen.py [--rate 20] [--duration 0] [--decision-pct 10]
# --rate: 每秒事件数
# --duration: 运行秒数 (0=持续运行, Ctrl+C 停止)
# --decision-pct: 决策引擎调用占比
```

### verify_pipeline.py — 全链路验证

6 个检查点:

| 检查点 | 验证内容 |
|--------|---------|
| CP1 | MySQL 源数据 (行数、外键、时序、值域) |
| CP2 | DataX 同步 (HDFS/ODS 分区) |
| CP3 | Spark ETL (DWD/DWS/ADS 聚合) |
| CP4 | Canal CDC (Kafka topics、消息格式) |
| CP5 | Flink 特征 (Redis key + HBase 行) |
| CP6 | 决策引擎 (API + ES 日志) |

```bash
python verify_pipeline.py --checkpoint ALL
python verify_pipeline.py --checkpoint CP1,CP5  # 只运行指定检查点
```

## 配置

编辑 `config.yaml` 或通过环境变量覆盖:

| 环境变量 | 说明 |
|---------|------|
| MYSQL_HOST | MySQL 地址 |
| KAFKA_BOOTSTRAP_SERVERS | Kafka 地址 (宿主机用 9093) |
| REDIS_HOST | Redis 地址 |
| DECISION_URL | 决策引擎地址 |

## 完整测试流程

1. 启动基础设施: `docker compose up -d`
2. 执行 MySQL DDL: `mysql -u credit_admin -p < docs/deployment/init-sql/02_credit_platform_tables.sql`
3. 生成批量数据: `python batch_data_gen.py`
4. 运行 DataX 同步 + Spark ETL
5. 启动实时流: `python realtime_gen.py --rate 20`
6. 验证: `python verify_pipeline.py`
