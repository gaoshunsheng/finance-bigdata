# 决策日志报表生成规格

## ADDED Requirements

### Requirement: 决策总览统计
系统 SHALL 从决策日志（ES 索引或本地 JSONL 文件）汇总生成决策总览统计数据，包含：总请求数、通过数、拒绝数、人工复核数、通过率、拒绝率、复核率、平均评分、评分中位数、P50/P95/P99 延迟（毫秒）。

#### Scenario: 生成总览统计
- **WHEN** 对 27,000 条决策日志执行总览统计
- **THEN** 输出包含 pass_rate（65-80%）、reject_rate（10-20%）、review_rate（5-15%）、avg_score、latency_percentiles 的结构化统计结果

### Requirement: 规则命中率统计
系统 SHALL 对所有决策日志中的 `hitRules` 字段进行聚合，统计每条规则的命中次数、命中率（=命中次数/总请求数），按命中率降序排列。

#### Scenario: 规则命中率排名
- **WHEN** 统计 27,000 条决策日志
- **THEN** 输出规则命中率排名表，TOP 命中规则应为准入规则（如年龄检查、收入检查），命中率与客户分层比例一致（高/异常客户命中拒绝规则的比例更高）

#### Scenario: 规则集维度汇总
- **WHEN** 按规则集维度（RS_BLACKLIST / RS_ELIGIBILITY）聚合
- **THEN** 输出每个规则集的总命中次数、命中率、贡献的 REJECT/REVIEW/PASS 数量

### Requirement: 评分分布统计
系统 SHALL 统计所有决策的评分分布，按分数区间（<500 / 500-550 / 550-650 / 650-750 / ≥750）分组计数，并关联各区间对应的决策结果分布（PASS/REJECT/REVIEW 比例）。

#### Scenario: 评分分布直方图数据
- **WHEN** 统计全部决策日志的评分字段
- **THEN** 输出 5 个分数段的计数和占比，大部分决策评分集中在 500-750 区间

#### Scenario: 评分区间决策结果交叉分析
- **WHEN** 按评分区间 × 决策结果交叉统计
- **THEN** <500 区间几乎全部为 REJECT，500-550 区间以 REVIEW 为主，≥550 区间以 PASS 为主

### Requirement: 时间维度趋势统计
系统 SHALL 按小时和按日维度汇总决策请求的时序数据，包含各时间窗口的请求数、通过率、拒绝率、平均评分，支持趋势变化检测。

#### Scenario: 按日汇总
- **WHEN** 决策日志时间跨度超过 1 天
- **THEN** 输出按 `yyyy-MM-dd` 分组的每日汇总数据（total, pass_rate, reject_rate, avg_score, avg_latency_ms）

#### Scenario: 按小时汇总
- **WHEN** 决策日志集中在同一天
- **THEN** 输出按 `HH:00` 分组的小时汇总数据，识别请求峰值时段

### Requirement: 报表输出格式
系统 SHALL 支持两种报表输出格式：(1) Markdown 报告（人类可读，含表格和概要描述），(2) CSV 文件（机器可读，供进一步分析）。报表文件输出到 `tools/data-generator/output/reports/` 目录。

#### Scenario: Markdown 报告生成
- **WHEN** 执行报表生成命令
- **THEN** 输出 `output/reports/decision_report_<timestamp>.md`，包含决策总览表、规则命中率 TOP10 表、评分分布表、按日汇总趋势表

#### Scenario: CSV 数据导出
- **WHEN** 执行报表生成命令
- **THEN** 同时输出 `output/reports/decision_stats_<timestamp>.csv`（总览一行）、`output/reports/rule_hit_stats_<timestamp>.csv`（规则命中率明细）、`output/reports/score_distribution_<timestamp>.csv`（评分分布）、`output/reports/daily_trend_<timestamp>.csv`（按日趋势）

### Requirement: 报表与数据生成联动
系统 SHALL 支持在端到端执行完成后自动生成报表（`--auto-report` 参数），无需手动分步执行。报表数据源优先从 ES 查询，ES 不可用时回退到本地 JSONL 文件。

#### Scenario: 自动报表生成
- **WHEN** 执行 `--mode batch --auto-report`
- **THEN** 批量决策执行完成后，自动调用报表生成逻辑，输出 Markdown 报告和 CSV 数据到 output/reports/

#### Scenario: ES 不可用时回退
- **WHEN** ES 连接失败或索引不存在
- **THEN** 报表生成器自动切换到读取本地 `output/decision_logs_*.jsonl` 文件作为数据源，在报表开头注明 "数据源: 本地文件 (ES 不可用)"
