## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  batch_data_gen.py          realtime_gen.py                 │
│  (一次性)                    (持续运行)                       │
│       │                          │                          │
│       │ INSERT                   ├─ 路径A: MySQL INSERT     │
│       ▼                          │   → Canal → Kafka长名    │
│  ┌──────────┐                    │                          │
│  │  MySQL   │◀───────────────────┤─ 路径B: Kafka扁平事件     │
│  │ 源表(4张)│                    │   → Flink短名topics      │
│  └──────────┘                    │                          │
│       │ DataX                    ├─ 路径C: 决策API(10%)     │
│       ▼                          │   → ES日志               │
│  ┌──────────┐                    │                          │
│  │  HDFS/   │→ Spark → DWD → DWS → ADS                    │
│  │  ODS     │                                               │
│  └──────────┘                                               │
│                                                             │
│  verify_pipeline.py: CP1→CP2→CP3→CP4→CP5→CP6               │
└─────────────────────────────────────────────────────────────┘
```

## Key Decisions

### 1. 实时生成器双路径设计
- **问题**: Canal CDC 输出 `{"data":[...],"type":"INSERT",...}` 信封格式，Flink 期望 `{"customerId":"C001","eventTime":"..."}` 扁平 JSON
- **决策**: realtime_gen 同时执行 MySQL INSERT（路径A）和 Kafka 扁平事件发送（路径B）
- **原因**: 当前无 CDC→扁平转换器，路径B 直接向短名 topics 发送扁平事件保证 Flink 管道可测试

### 2. Flink 窗口环境变量化
- **问题**: 窗口参数硬编码（90天/180天），测试时需等数天才能触发窗口
- **决策**: 添加 FLINK_WINDOW_SIZE_MS / FLINK_WINDOW_SLIDE_MS 环境变量，docker-compose.override.yml 设置测试默认值（天→分钟等比缩小）
- **原因**: 不修改生产默认值，测试时通过环境变量覆盖

### 3. 验证脚本容错设计
- **问题**: 部分infra可能未启动（HDFS/HBase/Hive）
- **决策**: 不可用的服务输出 SKIP 而非 FAIL，不影响其他检查点
- **原因**: 用户可能只想验证部分环节

## Data Model

### 客户分层画像
| 层级 | 占比 | 收入 | 逾期率 | 查询频次 |
|------|------|------|--------|---------|
| PREMIUM | 40% | 30-100w | 5% | 0-3次/年 |
| NORMAL | 30% | 10-50w | 30% | 1-5次/年 |
| HIGH_RISK | 20% | 3-15w | 70% | 3-15次/年 |
| ANOMALY | 10% | 极端值 | 90% | 5-20次/年 |

### MySQL 源表 (从 DataX 配置反向推导)
- `customer_info` (15列) — 匹配 mysql_to_hdfs_customer_info.json
- `loan_application` (12列) — 匹配 mysql_to_hdfs_loan_application.json
- `repayment_record` (13列) — 匹配 mysql_to_hdfs_repayment_record.json
- `credit_report` (13列) — 匹配 ods_credit_report.sql

### Kafka 扁平事件 (匹配 Flink POJO)
- `CreditQueryEvent` → topic: cdc_credit_query
- `OverdueEvent` → topic: cdc_overdue_event
- `ApplyEvent` → topic: application_event
- `TransactionEvent` → topic: transaction_event (注意 eventTime 用 ISO-8601 Instant 格式)

### Redis 特征 Key 格式
- Key: `feature:{featureType}:{customerId}`
- featureType: credit_query_3m / overdue_6m / apply_freq_1m / transaction_summary_1h
- TTL: 86400秒, Value: JSON

### HBase RowKey 格式
- Table: customer_feature, CF: cf
- RowKey: `{reversed(customerId)}_{featureType}_{windowEnd}`

## File Structure

```
tools/data-generator/
├── requirements.txt, config.yaml, config.py
├── models.py, db.py, kafka_helper.py
├── batch_data_gen.py, realtime_gen.py, verify_pipeline.py
└── verifiers/ (base, mysql, hdfs, hive, canal, flink, decision)
```

## Test Window Config

| Flink Job | 生产窗口 | 测试窗口 (env override) |
|-----------|---------|----------------------|
| CreditQuery3mJob | 90d / 1h slide | 90min / 1min slide |
| Overdue6mJob | 180d / 1d slide | 180min / 1min slide |
| ApplyFreq1mJob | 30d / 1h slide | 30min / 1min slide |
| TransactionSummaryJob | 1h tumbling | 1min tumbling |
