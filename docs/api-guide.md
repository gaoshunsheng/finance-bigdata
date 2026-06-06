# API 使用指南

## 1. 概述

本平台提供 110 个 API 端点，分布在 4 个服务：

| 服务 | 端口 | 基础路径 | 端点数 | 说明 |
|------|------|---------|--------|------|
| Decision Server | 8080 | /api/v1/decision | 3 | 决策执行 |
| Decision Admin | 8081 | /api/v1 | 83 | 管理后台(认证/CRUD/发布/审计) |
| Data Service | 8083 | /api/v1 | 4 | 数据查询(特征/画像/报表) |
| Model Platform | 8082 | /api/v1 | 20 | 模型训练/评估/推理/监控 |

认证方式：JWT Bearer Token（Authorization: Bearer \<token\>）

---

## 2. 快速开始

### 获取 JWT Token

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

响应：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "rt_abc123...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "username": "admin",
  "role": "ADMIN"
}
```

### 刷新 Token

```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"rt_abc123..."}'
```

---

## 3. 决策执行 API

决策执行服务负责运行策略并返回实时决策结果。

### 执行决策

```bash
curl -X POST http://localhost:8080/api/v1/decision/execute \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: req-001" \
  -H "X-Channel: APP" \
  -d '{
    "strategyId": "credit-score-v1",
    "channel": "APP",
    "applicant": {
      "customerId": "C100001",
      "loanAmount": 50000,
      "loanTerm": 12,
      "age": 35,
      "annualIncome": 200000
    },
    "metadata": {
      "source": "mobile-app",
      "version": "2.0"
    }
  }'
```

响应：

```json
{
  "decisionId": "20260606143025-00001",
  "result": "PASS",
  "score": 720,
  "rejectReason": null,
  "rejectCode": null,
  "traceId": "trace-abc123",
  "durationMs": 45,
  "extra": {}
}
```

### 查询决策报告

```bash
curl http://localhost:8080/api/v1/decision/report/20260606143025-00001 \
  -H "Authorization: Bearer <token>"
```

### 健康检查

```bash
curl http://localhost:8080/api/v1/decision/health
```

---

## 4. 认证管理 API

所有认证相关端点运行在 Decision Admin 服务（端口 8081）。

### 登录 — POST /api/v1/auth/login

（见第 2 节快速开始）

### 获取当前用户信息 — GET /api/v1/auth/me

```bash
curl http://localhost:8081/api/v1/auth/me \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "username": "admin",
  "displayName": "管理员",
  "email": "admin@example.com",
  "role": "ADMIN",
  "createdAt": "2026-01-01 00:00:00"
}
```

### 创建用户 — POST /api/v1/auth/users（需要 ADMIN 角色）

```bash
curl -X POST http://localhost:8081/api/v1/auth/users \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "editor1",
    "password": "pass123",
    "displayName": "编辑员",
    "email": "editor@example.com",
    "role": "EDITOR"
  }'
```

### 列出所有用户 — GET /api/v1/auth/users

```bash
curl http://localhost:8081/api/v1/auth/users \
  -H "Authorization: Bearer <token>"
```

### 修改密码 — POST /api/v1/auth/change-password

```bash
curl -X POST http://localhost:8081/api/v1/auth/change-password \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "oldPassword": "admin123",
    "newPassword": "newPass456"
  }'
```

### 登出 — POST /api/v1/auth/logout

```bash
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"rt_abc123..."}'
```

---

## 5. 规则资产 CRUD API

7 种资产类型共享相同的 CRUD 模式，区别仅在于路径和 type 参数值：

| 资产类型 | 路径 | type 参数值 |
|---------|------|-----------|
| 规则 | /api/v1/rules | RULE |
| 评分卡 | /api/v1/scorecards | SCORECARD |
| 决策表 | /api/v1/tables | DECISION_TABLE |
| 决策树 | /api/v1/trees | DECISION_TREE |
| 决策流 | /api/v1/flows | FLOW |
| 变量 | /api/v1/variables | VARIABLE |
| 实验 | /api/v1/experiments | EXPERIMENT |

以下以规则（rules）为例，其余资产类型将路径中的 `rules` 替换为对应路径即可。

### 创建规则 — POST /api/v1/rules

```bash
curl -X POST http://localhost:8081/api/v1/rules \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "年龄限制规则",
    "content": "{\"conditions\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":18}],\"action\":\"REJECT\"}",
    "description": "拒绝18岁以下申请"
  }'
```

响应：

```json
{
  "id": "rule-001",
  "name": "年龄限制规则",
  "type": "RULE",
  "version": 1,
  "status": "DRAFT",
  "createdAt": "2026-06-06 14:30:00",
  "updatedAt": "2026-06-06 14:30:00"
}
```

### 列出规则 — GET /api/v1/rules

```bash
curl http://localhost:8081/api/v1/rules \
  -H "Authorization: Bearer <token>"
```

### 获取规则详情 — GET /api/v1/rules/{id}

```bash
curl http://localhost:8081/api/v1/rules/rule-001 \
  -H "Authorization: Bearer <token>"
```

### 更新规则 — PUT /api/v1/rules/{id}

```bash
curl -X PUT http://localhost:8081/api/v1/rules/rule-001 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "{\"conditions\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":21}],\"action\":\"REJECT\"}",
    "description": "拒绝21岁以下申请"
  }'
```

### 删除规则 — DELETE /api/v1/rules/{id}

```bash
curl -X DELETE http://localhost:8081/api/v1/rules/rule-001 \
  -H "Authorization: Bearer <token>"
```

### 查看版本历史 — GET /api/v1/rules/{id}/versions

```bash
curl http://localhost:8081/api/v1/rules/rule-001/versions \
  -H "Authorization: Bearer <token>"
```

### 创建新版本 — POST /api/v1/rules/{id}/versions

```bash
curl -X POST http://localhost:8081/api/v1/rules/rule-001/versions \
  -H "Authorization: Bearer <token>"
```

### 删除指定版本 — DELETE /api/v1/rules/{id}/{version}

```bash
curl -X DELETE http://localhost:8081/api/v1/rules/rule-001/3 \
  -H "Authorization: Bearer <token>"
```

---

## 6. 发布管理 API

### 完整发布流程

```
DRAFT → promote-to-testing → TESTING → submit-approval → PENDING_REVIEW
  → approve → APPROVED → grayscale/start → GRAYSCALE → (ramp-up × N) → RELEASED
```

各状态说明：

| 状态 | 含义 |
|------|------|
| DRAFT | 草稿，编辑中 |
| TESTING | 测试中 |
| PENDING_REVIEW | 待审批 |
| APPROVED | 审批通过 |
| GRAYSCALE | 灰度发布中 |
| RELEASED | 已全量发布 |

以下操作中 `{type}` 取值为：RULE、SCORECARD、DECISION_TABLE、DECISION_TREE、FLOW、VARIABLE、EXPERIMENT。

### 推进到测试 — POST /api/v1/publish/{type}/{id}/promote-to-testing

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/promote-to-testing \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"operator":"admin"}'
```

### 提交审批 — POST /api/v1/publish/{type}/{id}/submit-approval

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/submit-approval \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "operator": "editor1",
    "comment": "已完成测试验证"
  }'
```

### 审批通过 — POST /api/v1/publish/{type}/{id}/approve

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/approve \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "operator": "approver1",
    "comment": "审批通过"
  }'
```

### 审批拒绝 — POST /api/v1/publish/{type}/{id}/reject

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/reject \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "operator": "approver1",
    "reason": "阈值设置过低"
  }'
```

### 启动灰度发布 — POST /api/v1/publish/{type}/{id}/grayscale/start

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/grayscale/start \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "operator": "admin",
    "percentage": 5
  }'
```

### 提升灰度比例 — POST /api/v1/publish/{type}/{id}/grayscale/ramp-up

逐步扩大灰度流量比例，每次调用按预设步长递增。

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/grayscale/ramp-up \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"operator":"admin"}'
```

### 暂停灰度 — POST /api/v1/publish/{type}/{id}/grayscale/pause

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/grayscale/pause \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"operator":"admin"}'
```

### 恢复灰度 — POST /api/v1/publish/{type}/{id}/grayscale/resume

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/grayscale/resume \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"operator":"admin"}'
```

### 灰度回滚 — POST /api/v1/publish/{type}/{id}/grayscale/rollback

回滚灰度发布，恢复到上一版本。

```bash
curl -X POST http://localhost:8081/api/v1/publish/RULE/rule-001/grayscale/rollback \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"operator":"admin"}'
```

### 版本对比 — GET /api/v1/publish/{type}/{id}/diff?from=1&to=2

```bash
curl "http://localhost:8081/api/v1/publish/RULE/rule-001/diff?from=1&to=2" \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "fromVersion": 1,
  "toVersion": 2,
  "diff": [
    {"op": "replace", "path": "/conditions/0/value", "from": 18, "to": 21}
  ]
}
```

### 查看待审批列表 — GET /api/v1/publish/pending-approvals

```bash
curl http://localhost:8081/api/v1/publish/pending-approvals \
  -H "Authorization: Bearer <token>"
```

### 查询发布状态 — GET /api/v1/publish/{type}/{id}/status

```bash
curl http://localhost:8081/api/v1/publish/RULE/rule-001/status \
  -H "Authorization: Bearer <token>"
```

---

## 7. 数据服务 API

数据服务提供客户特征、企业画像和 BI 报表查询能力。

### 查询客户特征 — GET /api/v1/features/{customerId}

P99 延迟 < 20ms。

```bash
curl http://localhost:8083/api/v1/features/C100001 \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "customerId": "C100001",
  "features": {
    "credit_score": 720,
    "overdue_count_3m": 0,
    "overdue_count_6m": 1,
    "avg_monthly_payment": 8500,
    "max_overdue_days": 5,
    "loan_count": 3,
    "total_loan_amount": 150000
  },
  "updatedAt": "2026-06-06 08:00:00"
}
```

### 查询指定特征 — GET /api/v1/features/{customerId}/select

仅返回指定 key 的特征值，减少数据传输量。

```bash
curl "http://localhost:8083/api/v1/features/C100001/select?keys=credit_score,overdue_count_3m" \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "customerId": "C100001",
  "features": {
    "credit_score": 720,
    "overdue_count_3m": 0
  }
}
```

### 查询企业画像 — GET /api/v1/profile/{enterpriseId}

P99 延迟 < 500ms。

```bash
curl http://localhost:8083/api/v1/profile/ENT001 \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "enterpriseId": "ENT001",
  "basicInfo": {
    "name": "XX科技有限公司",
    "industry": "信息技术",
    "registeredCapital": 5000000,
    "establishedDate": "2015-03-20"
  },
  "financialMetrics": {
    "annualRevenue": 50000000,
    "netProfitMargin": 0.12,
    "debtToAssetRatio": 0.35
  },
  "riskIndicators": {
    "legalDisputes": 2,
    "taxArrears": false,
    "businessStatus": "NORMAL"
  },
  "updatedAt": "2026-06-06 06:00:00"
}
```

### 查询 BI 报表

#### 业务报表 — GET /api/v1/reports/business（T+1，按日更新）

```bash
curl "http://localhost:8083/api/v1/reports/business?date=2026-06-05" \
  -H "Authorization: Bearer <token>"
```

#### 风控报表 — GET /api/v1/reports/risk（实时）

```bash
curl http://localhost:8083/api/v1/reports/risk \
  -H "Authorization: Bearer <token>"
```

#### 渠道分析 — GET /api/v1/reports/channel（T+1，按日更新）

```bash
curl "http://localhost:8083/api/v1/reports/channel?date=2026-06-05" \
  -H "Authorization: Bearer <token>"
```

#### 数据质量 — GET /api/v1/reports/quality（实时）

```bash
curl http://localhost:8083/api/v1/reports/quality \
  -H "Authorization: Bearer <token>"
```

---

## 8. 模型平台 API

模型平台提供从数据集管理、模型训练、评估、推理到监控的全流程能力。

### 数据集管理

#### 创建数据集 — POST /api/v1/data/datasets

```bash
curl -X POST http://localhost:8082/api/v1/data/datasets \
  -H "Content-Type: application/json" \
  -d '{
    "label_column": "is_default",
    "positive_label": 1,
    "negative_label": 0
  }'
```

响应：

```json
{
  "id": "ds_001",
  "label_column": "is_default",
  "positive_label": 1,
  "negative_label": 0,
  "row_count": 0,
  "created_at": "2026-06-06 14:30:00"
}
```

#### 上传数据 — POST /api/v1/data/datasets/{id}/upload

```bash
curl -X POST http://localhost:8082/api/v1/data/datasets/ds_001/upload \
  -H "Content-Type: multipart/form-data" \
  -F "file=@training_data.csv"
```

#### 列出数据集 — GET /api/v1/data/datasets

```bash
curl http://localhost:8082/api/v1/data/datasets
```

#### 获取数据集详情 — GET /api/v1/data/datasets/{id}

```bash
curl http://localhost:8082/api/v1/data/datasets/ds_001
```

#### 删除数据集 — DELETE /api/v1/data/datasets/{id}

```bash
curl -X DELETE http://localhost:8082/api/v1/data/datasets/ds_001
```

### 模型训练

#### 提交训练任务 — POST /api/v1/training/train

```bash
curl -X POST http://localhost:8082/api/v1/training/train \
  -H "Content-Type: application/json" \
  -d '{
    "name": "credit-risk-v2",
    "algorithm": "XGBOOST",
    "dataset_id": "ds_001",
    "target_column": "is_default",
    "cv_folds": 5
  }'
```

支持的算法：XGBOOST、LIGHTGBM、RANDOM_FOREST、LOGISTIC_REGRESSION、NEURAL_NETWORK。

响应：

```json
{
  "model_id": "model_001",
  "name": "credit-risk-v2",
  "algorithm": "XGBOOST",
  "status": "TRAINING",
  "created_at": "2026-06-06 14:35:00"
}
```

#### 查询训练状态 — GET /api/v1/training/{model_id}/status

```bash
curl http://localhost:8082/api/v1/training/model_001/status
```

#### 列出训练任务 — GET /api/v1/training/jobs

```bash
curl http://localhost:8082/api/v1/training/jobs
```

#### 停止训练任务 — POST /api/v1/training/{model_id}/stop

```bash
curl -X POST http://localhost:8082/api/v1/training/model_001/stop
```

### 模型评估

#### 评估模型 — POST /api/v1/evaluation/evaluate

```bash
curl -X POST http://localhost:8082/api/v1/evaluation/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "model_id": "model_001",
    "threshold": 0.5
  }'
```

响应：

```json
{
  "model_id": "model_001",
  "metrics": {
    "auc": 0.856,
    "ks": 0.542,
    "gini": 0.712,
    "precision": 0.823,
    "recall": 0.789,
    "f1_score": 0.806,
    "accuracy": 0.851
  },
  "confusion_matrix": {
    "tp": 789,
    "fp": 170,
    "fn": 211,
    "tn": 8830
  },
  "evaluated_at": "2026-06-06 15:00:00"
}
```

#### 获取评估历史 — GET /api/v1/evaluation/{model_id}/history

```bash
curl http://localhost:8082/api/v1/evaluation/model_001/history
```

### 模型推理

#### 单条推理 — POST /api/v1/inference/predict

```bash
curl -X POST http://localhost:8082/api/v1/inference/predict \
  -H "Content-Type: application/json" \
  -d '{
    "model_id": "model_001",
    "features": {
      "age": 35,
      "income": 200000,
      "loan_amount": 50000
    },
    "return_explanation": true
  }'
```

响应：

```json
{
  "prediction": 1,
  "probability": 0.23,
  "explanation": {
    "age": {"contribution": -0.05, "direction": "NEGATIVE"},
    "income": {"contribution": -0.12, "direction": "NEGATIVE"},
    "loan_amount": {"contribution": 0.08, "direction": "POSITIVE"}
  },
  "predicted_at": "2026-06-06 15:05:00"
}
```

#### 批量推理 — POST /api/v1/inference/batch-predict

```bash
curl -X POST http://localhost:8082/api/v1/inference/batch-predict \
  -H "Content-Type: application/json" \
  -d '{
    "model_id": "model_001",
    "records": [
      {"age":35,"income":200000,"loan_amount":50000},
      {"age":28,"income":120000,"loan_amount":30000}
    ]
  }'
```

### 模型导出

#### 导出模型 — POST /api/v1/export/export

```bash
curl -X POST http://localhost:8082/api/v1/export/export \
  -H "Content-Type: application/json" \
  -d '{
    "model_id": "model_001",
    "format": "ONNX"
  }'
```

支持的导出格式：ONNX、PMML、JSON、PICKLE。

#### 列出已导出模型 — GET /api/v1/export/models

```bash
curl http://localhost:8082/api/v1/export/models
```

#### 下载已导出模型 — GET /api/v1/export/models/{id}/download

```bash
curl -O http://localhost:8082/api/v1/export/models/model_001/download
```

### 模型监控

#### 查看监控仪表盘 — GET /api/v1/monitoring/dashboard/{model_id}

```bash
curl http://localhost:8082/api/v1/monitoring/dashboard/model_001
```

响应：

```json
{
  "model_id": "model_001",
  "status": "HEALTHY",
  "metrics": {
    "psi": 0.03,
    "auc_drift": 0.002,
    "prediction_mean": 0.28,
    "prediction_std": 0.15,
    "request_count_24h": 15280,
    "avg_latency_ms": 12
  },
  "alerts": [],
  "last_updated": "2026-06-06 15:10:00"
}
```

#### 查看性能趋势 — GET /api/v1/monitoring/performance/{model_id}

```bash
curl http://localhost:8082/api/v1/monitoring/performance/model_001
```

#### 查看特征漂移 — GET /api/v1/monitoring/drift/{model_id}

```bash
curl http://localhost:8082/api/v1/monitoring/drift/model_001
```

---

## 9. 审计日志 API

### 查询审计日志（分页） — GET /api/v1/audit

```bash
curl "http://localhost:8081/api/v1/audit?page=0&size=20" \
  -H "Authorization: Bearer <token>"
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 0 | 页码（从 0 开始） |
| size | int | 20 | 每页条数 |
| startDate | string | — | 起始日期（yyyy-MM-dd） |
| endDate | string | — | 截止日期（yyyy-MM-dd） |
| operator | string | — | 操作人 |
| action | string | — | 操作类型（CREATE/UPDATE/DELETE/PUBLISH/APPROVE） |

响应：

```json
{
  "content": [
    {
      "id": 1001,
      "targetType": "RULE",
      "targetId": "rule-001",
      "action": "UPDATE",
      "operator": "editor1",
      "detail": "更新规则内容，版本从 v2 升级到 v3",
      "ipAddress": "192.168.1.100",
      "timestamp": "2026-06-06 14:30:00"
    }
  ],
  "totalElements": 156,
  "totalPages": 8,
  "page": 0,
  "size": 20
}
```

### 查询目标审计历史 — GET /api/v1/audit/{type}/{id}

```bash
curl http://localhost:8081/api/v1/audit/RULE/rule-001 \
  -H "Authorization: Bearer <token>"
```

### 审计统计 — GET /api/v1/audit/stats

```bash
curl http://localhost:8081/api/v1/audit/stats \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "totalActions": 15280,
  "todayActions": 156,
  "byAction": {
    "CREATE": 3200,
    "UPDATE": 5800,
    "DELETE": 1200,
    "PUBLISH": 3800,
    "APPROVE": 1280
  },
  "topOperators": [
    {"username": "admin", "count": 6200},
    {"username": "editor1", "count": 4800},
    {"username": "approver1", "count": 4280}
  ]
}
```

---

## 10. 错误码参考

### HTTP 状态码

| HTTP 状态码 | 含义 | 处理建议 |
|------------|------|---------|
| 200 | 成功 | — |
| 400 | 请求参数错误 | 检查请求体格式和必填字段 |
| 401 | 未认证 | 检查 Authorization header，Token 可能过期 |
| 403 | 无权限 | 当前用户角色不满足要求 |
| 404 | 资源不存在 | 检查 ID 是否正确 |
| 429 | 请求限流 | 降低请求频率，检查 RateLimit-Remaining header |
| 500 | 服务内部错误 | 查看服务日志，联系运维 |

### 业务错误码

响应体中的错误格式：

```json
{
  "code": "RULE_VERSION_CONFLICT",
  "message": "规则版本冲突，请刷新后重试",
  "timestamp": "2026-06-06 14:30:00"
}
```

| 错误码 | 说明 | 处理建议 |
|--------|------|---------|
| AUTH_TOKEN_EXPIRED | Token 已过期 | 调用刷新 Token 接口或重新登录 |
| AUTH_INVALID_CREDENTIALS | 用户名或密码错误 | 检查登录凭据 |
| AUTH_ACCOUNT_LOCKED | 账户已锁定 | 联系管理员解锁 |
| RULE_VERSION_CONFLICT | 规则版本冲突 | 刷新页面获取最新版本后重试 |
| PUBLISH_STATE_INVALID | 发布状态不允许此操作 | 检查当前状态是否允许执行该操作 |
| PUBLISH_APPROVAL_REQUIRED | 需要审批权限 | 使用具有 APPROVER 角色的账户操作 |
| MODEL_TRAINING_FAILED | 模型训练失败 | 检查数据集质量和参数配置 |
| MODEL_NOT_FOUND | 模型不存在 | 确认 model_id 是否正确 |
| DATASET_EMPTY | 数据集为空 | 上传数据后再进行训练 |
| FEATURE_NOT_FOUND | 特征数据不存在 | 确认 customerId 是否正确，检查数据同步状态 |
| RATE_LIMIT_EXCEEDED | 请求频率超限 | 降低请求频率，稍后重试 |
| DUPLICATE_NAME | 名称重复 | 使用不同的名称 |

---

## 11. 附录：完整端点列表

### Decision Server（端口 8080）— 3 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 1 | POST | /api/v1/decision/execute | 执行决策 |
| 2 | GET | /api/v1/decision/report/{decisionId} | 查询决策报告 |
| 3 | GET | /api/v1/decision/health | 健康检查 |

### Decision Admin — 认证管理（端口 8081）— 7 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 4 | POST | /api/v1/auth/login | 用户登录 |
| 5 | POST | /api/v1/auth/refresh | 刷新 Token |
| 6 | GET | /api/v1/auth/me | 获取当前用户信息 |
| 7 | POST | /api/v1/auth/users | 创建用户（ADMIN） |
| 8 | GET | /api/v1/auth/users | 列出所有用户 |
| 9 | POST | /api/v1/auth/change-password | 修改密码 |
| 10 | POST | /api/v1/auth/logout | 用户登出 |

### Decision Admin — 规则 CRUD（端口 8081）— 7 种资产 x 7 个操作 = 49 个端点

以规则（rules）为例，7 种资产（rules、scorecards、tables、trees、flows、variables、experiments）各提供以下操作：

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 11 | POST | /api/v1/rules | 创建规则 |
| 12 | GET | /api/v1/rules | 列出规则 |
| 13 | GET | /api/v1/rules/{id} | 获取规则详情 |
| 14 | PUT | /api/v1/rules/{id} | 更新规则 |
| 15 | DELETE | /api/v1/rules/{id} | 删除规则 |
| 16 | GET | /api/v1/rules/{id}/versions | 查看版本历史 |
| 17 | POST | /api/v1/rules/{id}/versions | 创建新版本 |
| 18 | DELETE | /api/v1/rules/{id}/{version} | 删除指定版本 |
| 19 | POST | /api/v1/scorecards | 创建评分卡 |
| 20 | GET | /api/v1/scorecards | 列出评分卡 |
| 21 | GET | /api/v1/scorecards/{id} | 获取评分卡详情 |
| 22 | PUT | /api/v1/scorecards/{id} | 更新评分卡 |
| 23 | DELETE | /api/v1/scorecards/{id} | 删除评分卡 |
| 24 | GET | /api/v1/scorecards/{id}/versions | 查看评分卡版本历史 |
| 25 | POST | /api/v1/scorecards/{id}/versions | 创建评分卡新版本 |
| 26 | DELETE | /api/v1/scorecards/{id}/{version} | 删除评分卡指定版本 |
| 27 | POST | /api/v1/tables | 创建决策表 |
| 28 | GET | /api/v1/tables | 列出决策表 |
| 29 | GET | /api/v1/tables/{id} | 获取决策表详情 |
| 30 | PUT | /api/v1/tables/{id} | 更新决策表 |
| 31 | DELETE | /api/v1/tables/{id} | 删除决策表 |
| 32 | GET | /api/v1/tables/{id}/versions | 查看决策表版本历史 |
| 33 | POST | /api/v1/tables/{id}/versions | 创建决策表新版本 |
| 34 | DELETE | /api/v1/tables/{id}/{version} | 删除决策表指定版本 |
| 35 | POST | /api/v1/trees | 创建决策树 |
| 36 | GET | /api/v1/trees | 列出决策树 |
| 37 | GET | /api/v1/trees/{id} | 获取决策树详情 |
| 38 | PUT | /api/v1/trees/{id} | 更新决策树 |
| 39 | DELETE | /api/v1/trees/{id} | 删除决策树 |
| 40 | GET | /api/v1/trees/{id}/versions | 查看决策树版本历史 |
| 41 | POST | /api/v1/trees/{id}/versions | 创建决策树新版本 |
| 42 | DELETE | /api/v1/trees/{id}/{version} | 删除决策树指定版本 |
| 43 | POST | /api/v1/flows | 创建决策流 |
| 44 | GET | /api/v1/flows | 列出决策流 |
| 45 | GET | /api/v1/flows/{id} | 获取决策流详情 |
| 46 | PUT | /api/v1/flows/{id} | 更新决策流 |
| 47 | DELETE | /api/v1/flows/{id} | 删除决策流 |
| 48 | GET | /api/v1/flows/{id}/versions | 查看决策流版本历史 |
| 49 | POST | /api/v1/flows/{id}/versions | 创建决策流新版本 |
| 50 | DELETE | /api/v1/flows/{id}/{version} | 删除决策流指定版本 |
| 51 | POST | /api/v1/variables | 创建变量 |
| 52 | GET | /api/v1/variables | 列出变量 |
| 53 | GET | /api/v1/variables/{id} | 获取变量详情 |
| 54 | PUT | /api/v1/variables/{id} | 更新变量 |
| 55 | DELETE | /api/v1/variables/{id} | 删除变量 |
| 56 | GET | /api/v1/variables/{id}/versions | 查看变量版本历史 |
| 57 | POST | /api/v1/variables/{id}/versions | 创建变量新版本 |
| 58 | DELETE | /api/v1/variables/{id}/{version} | 删除变量指定版本 |
| 59 | POST | /api/v1/experiments | 创建实验 |
| 60 | GET | /api/v1/experiments | 列出实验 |
| 61 | GET | /api/v1/experiments/{id} | 获取实验详情 |
| 62 | PUT | /api/v1/experiments/{id} | 更新实验 |
| 63 | DELETE | /api/v1/experiments/{id} | 删除实验 |
| 64 | GET | /api/v1/experiments/{id}/versions | 查看实验版本历史 |
| 65 | POST | /api/v1/experiments/{id}/versions | 创建实验新版本 |
| 66 | DELETE | /api/v1/experiments/{id}/{version} | 删除实验指定版本 |

### Decision Admin — 发布管理（端口 8081）— 11 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 67 | POST | /api/v1/publish/{type}/{id}/promote-to-testing | 推进到测试 |
| 68 | POST | /api/v1/publish/{type}/{id}/submit-approval | 提交审批 |
| 69 | POST | /api/v1/publish/{type}/{id}/approve | 审批通过 |
| 70 | POST | /api/v1/publish/{type}/{id}/reject | 审批拒绝 |
| 71 | POST | /api/v1/publish/{type}/{id}/grayscale/start | 启动灰度发布 |
| 72 | POST | /api/v1/publish/{type}/{id}/grayscale/ramp-up | 提升灰度比例 |
| 73 | POST | /api/v1/publish/{type}/{id}/grayscale/pause | 暂停灰度 |
| 74 | POST | /api/v1/publish/{type}/{id}/grayscale/resume | 恢复灰度 |
| 75 | POST | /api/v1/publish/{type}/{id}/grayscale/rollback | 灰度回滚 |
| 76 | GET | /api/v1/publish/{type}/{id}/diff | 版本对比 |
| 77 | GET | /api/v1/publish/pending-approvals | 查看待审批列表 |

### Decision Admin — 审计日志（端口 8081）— 3 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 78 | GET | /api/v1/audit | 查询审计日志（分页） |
| 79 | GET | /api/v1/audit/{type}/{id} | 查询目标审计历史 |
| 80 | GET | /api/v1/audit/stats | 审计统计 |

### Data Service（端口 8083）— 4 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 81 | GET | /api/v1/features/{customerId} | 查询客户特征 |
| 82 | GET | /api/v1/features/{customerId}/select | 查询指定特征 |
| 83 | GET | /api/v1/profile/{enterpriseId} | 查询企业画像 |
| 84 | GET | /api/v1/reports/business | 查询业务报表 |
| 85 | GET | /api/v1/reports/risk | 查询风控报表 |
| 86 | GET | /api/v1/reports/channel | 查询渠道分析报表 |
| 87 | GET | /api/v1/reports/quality | 查询数据质量报表 |

### Model Platform — 数据管理（端口 8082）— 5 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 88 | POST | /api/v1/data/datasets | 创建数据集 |
| 89 | POST | /api/v1/data/datasets/{id}/upload | 上传数据 |
| 90 | GET | /api/v1/data/datasets | 列出数据集 |
| 91 | GET | /api/v1/data/datasets/{id} | 获取数据集详情 |
| 92 | DELETE | /api/v1/data/datasets/{id} | 删除数据集 |

### Model Platform — 模型训练（端口 8082）— 4 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 93 | POST | /api/v1/training/train | 提交训练任务 |
| 94 | GET | /api/v1/training/{model_id}/status | 查询训练状态 |
| 95 | GET | /api/v1/training/jobs | 列出训练任务 |
| 96 | POST | /api/v1/training/{model_id}/stop | 停止训练任务 |

### Model Platform — 模型评估（端口 8082）— 2 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 97 | POST | /api/v1/evaluation/evaluate | 评估模型 |
| 98 | GET | /api/v1/evaluation/{model_id}/history | 获取评估历史 |

### Model Platform — 模型推理（端口 8082）— 2 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 99 | POST | /api/v1/inference/predict | 单条推理 |
| 100 | POST | /api/v1/inference/batch-predict | 批量推理 |

### Model Platform — 模型导出（端口 8082）— 3 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 101 | POST | /api/v1/export/export | 导出模型 |
| 102 | GET | /api/v1/export/models | 列出已导出模型 |
| 103 | GET | /api/v1/export/models/{id}/download | 下载已导出模型 |

### Model Platform — 模型监控（端口 8082）— 3 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 104 | GET | /api/v1/monitoring/dashboard/{model_id} | 查看监控仪表盘 |
| 105 | GET | /api/v1/monitoring/performance/{model_id} | 查看性能趋势 |
| 106 | GET | /api/v1/monitoring/drift/{model_id} | 查看特征漂移 |

### Model Platform — 策略管理（端口 8082）— 4 个端点

| 序号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 107 | POST | /api/v1/strategies | 创建策略 |
| 108 | GET | /api/v1/strategies | 列出策略 |
| 109 | GET | /api/v1/strategies/{id} | 获取策略详情 |
| 110 | PUT | /api/v1/strategies/{id} | 更新策略 |

---

> **共计 110 个 API 端点**：Decision Server 3 个 + Decision Admin 70 个 + Data Service 7 个 + Model Platform 19 个（含策略 4 个）= 99 个独立路径端点，加上 7 种资产各 8 个版本 CRUD 路径覆盖后总计 110 个端点。
