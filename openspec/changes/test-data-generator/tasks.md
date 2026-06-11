## Status: COMPLETED

All tasks have been implemented and committed (d83c59e).

## Tasks

- [x] T01: 创建 MySQL 业务源表 DDL — `docs/deployment/init-sql/02_credit_platform_tables.sql`
- [x] T02: 创建项目骨架和 requirements.txt — `tools/data-generator/` 目录结构
- [x] T03: 创建配置模块 — `config.yaml` + `config.py`
- [x] T04: 创建数据模型 — `models.py` (Pydantic: 4表+4事件+验证基线)
- [x] T05: 创建 MySQL 连接池 — `db.py` (batch_insert/execute/query)
- [x] T06: 创建 Kafka 辅助模块 — `kafka_helper.py`
- [x] T07: 实现批量历史数据生成器 — `batch_data_gen.py` (10K客户×12月, 4层画像)
- [x] T08: 实现实时数据模拟器 — `realtime_gen.py` (MySQL+Kafka双路径+决策API)
- [x] T09: 实现全链路验证脚本 — `verify_pipeline.py` + `verifiers/` (6检查点)
- [x] T10: Flink 窗口参数环境变量化 — 4个Flink Job修改
- [x] T11: 更新 docker-compose.override.yml — Flink测试窗口环境变量
- [x] T12: 创建 README.md

## Verification

- Flink 模块编译通过 (`mvn compile -pl data-platform/flink-jobs`)
- MySQL DDL 已在 Docker 环境执行成功 (4张表+2个用户)
- Python 依赖安装成功
