## Why

系统缺少完整的端到端测试数据，无法验证完整数据管道：MySQL 源表 → Canal CDC → Kafka → Flink → Redis/HBase → 决策引擎 → ES。需要一套 Python 工具生成足够量级的历史数据 + 实时数据流，并提供全链路验证能力。

关键发现：
- 4 张 MySQL 业务源表 (customer_info, loan_application, repayment_record, credit_report) 没有 MySQL DDL，只有 Hive ODS 层定义
- Canal CDC 输出信封格式与 Flink 期望的扁平 JSON 不兼容，需要双路径写入
- 4 个 Flink 作业窗口参数全部硬编码，需要环境变量化以支持测试加速

## What Changes

- **创建 MySQL 业务源表 DDL**: 从 DataX 配置反向推导 4 张表的 MySQL CREATE TABLE 语句，并创建 etl_user 和 canal 用户
- **创建批量历史数据生成器**: Python 脚本生成 10,000 客户 × 12 个月数据，4 层客户画像（优质/普通/高风险/异常），输出验证基线 expected_counts.json
- **创建实时数据模拟器**: 双路径事件生成 — MySQL INSERT（触发 Canal CDC）+ Kafka 扁平事件（供 Flink 消费）+ 决策引擎调用
- **创建全链路验证脚本**: 6 个检查点验证完整管道 — CP1 MySQL、CP2 DataX、CP3 Spark ETL、CP4 Canal CDC、CP5 Flink 特征、CP6 决策引擎
- **Flink 窗口参数环境变量化**: 4 个 Flink Job 的窗口大小和滑动间隔支持通过 FLINK_WINDOW_SIZE_MS / FLINK_WINDOW_SLIDE_MS 环境变量配置，测试环境将天级窗口缩小为分钟级

## Capabilities

### New Capabilities

- `test-data-generation`: 测试数据生成能力 — 批量历史数据生成（客户/贷款/还款/征信）、实时事件流模拟（MySQL+Kafka 双路径）、客户分层画像（优质/普通/高风险/异常）

### Modified Capabilities

- `data-compute`: 数据计算能力 — Flink 实时作业窗口参数支持环境变量配置，测试环境可加速特征计算

## Scope

- 仅涉及测试工具和 Flink 配置参数化，不修改任何业务逻辑
- 实时生成器通过直接向 Kafka 短名 topics 发送扁平事件绕过 Canal 格式不兼容问题
- 验证脚本对不可用的服务（HDFS/HBase/Hive）输出 SKIP 而非 FAIL

## Out of Scope

- 不修改 Canal 配置或添加 CDC→扁平 JSON 转换器
- 不修改决策引擎业务逻辑
- 不添加数据质量异常场景测试（空值、格式错误等）
