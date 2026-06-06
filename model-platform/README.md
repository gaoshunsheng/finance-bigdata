# model-platform

> 风控模型平台，基于 FastAPI 的 Python 服务，提供模型训练、评估、导出、推理和监控能力。

## 功能概述

- **数据准备** — 特征工程（WOE/IV 计算）、样本管理与采样
- **模型训练** — 支持 XGBoost、LightGBM、scikit-learn 等算法的模型训练
- **模型评估** — KS、AUC、Gini 等指标评估，PSI 稳定性检测
- **模型导出** — 导出为 ONNX 格式，支持跨平台高性能推理
- **推理服务** — REST API 和 gRPC 双协议推理服务
- **模型监控** — PSI 预警、KS 趋势监控，自动化模型漂移检测
- **审计日志** — 模型全生命周期审计追踪

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| FastAPI | 0.111.0 | Web 框架 |
| Pydantic | 2.8.0 | 数据校验 |
| scikit-learn | 1.5.0 | 机器学习 |
| XGBoost | 2.0.3 | 梯度提升树 |
| LightGBM | 4.4.0 | 梯度提升树 |
| ONNX / ONNX Runtime | 1.16.x / 1.18.x | 模型导出与推理 |
| scorecardpy | 0.1.9.7 | WOE/IV 计算 |
| gRPC | 1.64.1 | 高性能推理协议 |
| SQLAlchemy | 2.0.31 | 数据库 ORM |
| Redis | 5.0.7 | 缓存 |
| Loguru | 0.7.2 | 日志 |
| Uvicorn | 0.30.1 | ASGI 服务器 |

## 目录结构

```
app/
├── main.py                 — FastAPI 应用入口
├── config.py               — 配置管理（环境变量）
├── models/                 — 数据模型
├── schemas/                — Pydantic Schema
├── utils/                  — 工具函数
├── api/                    — API 路由
│   ├── router.py           — 路由注册
│   └── endpoints/          — API 端点
│       ├── data_prep.py    — 数据准备 /api/v1/data/
│       ├── training.py     — 模型训练 /api/v1/training/
│       ├── evaluation.py   — 模型评估 /api/v1/evaluation/
│       ├── export.py       — 模型导出 /api/v1/export/
│       ├── inference.py    — 推理服务 /api/v1/inference/
│       ├── monitoring.py   — 模型监控 /api/v1/monitoring/
│       └── audit.py        — 审计日志 /api/v1/audit/
├── core/                   — 核心业务逻辑
│   ├── data_prep/          — 数据准备
│   │   ├── feature_engineering.py — 特征工程
│   │   └── sample_manager.py      — 样本管理
│   ├── training/           — 训练
│   │   └── trainer.py      — 模型训练器
│   ├── inference/          — 推理
│   │   ├── inference_service.py — 推理服务
│   │   └── grpc_service.py      — gRPC 推理
│   ├── evaluation/         — 评估
│   │   └── evaluator.py    — 模型评估器
│   ├── export/             — 导出
│   │   └── exporter.py     — ONNX 导出器
│   └── monitoring/         — 监控
│       ├── monitor.py      — 模型监控器
│       └── audit.py        — 审计
└── storage/                — 存储目录
    ├── models/             — 模型文件
    ├── data/               — 数据文件
    └── reports/            — 报告文件

tests/
├── unit/                   — 单元测试
│   ├── test_feature_engineering.py
│   ├── test_sample_manager.py
│   ├── test_trainer.py
│   ├── test_evaluator.py
│   ├── test_inference.py
│   ├── test_exporter.py
│   ├── test_monitor.py
│   └── test_audit.py
└── integration/            — 集成测试
    └── test_api.py
```

## 关键类/文件说明

| 文件 | 说明 |
|------|------|
| `app/main.py` | FastAPI 应用入口，CORS 中间件，路由注册 |
| `app/config.py` | 配置管理，支持环境变量覆盖 |
| `app/api/router.py` | API 路由聚合，7 个子路由模块 |
| `app/core/data_prep/feature_engineering.py` | 特征工程，WOE 编码和 IV 计算 |
| `app/core/data_prep/sample_manager.py` | 样本管理，采样和分层 |
| `app/core/training/trainer.py` | 模型训练器，支持 XGBoost/LightGBM/sklearn |
| `app/core/evaluation/evaluator.py` | 模型评估器，KS/AUC/Gini 指标 |
| `app/core/inference/inference_service.py` | REST 推理服务 |
| `app/core/inference/grpc_service.py` | gRPC 高性能推理 |
| `app/core/export/exporter.py` | ONNX 模型导出 |
| `app/core/monitoring/monitor.py` | PSI 预警和 KS 趋势监控 |
| `app/core/monitoring/audit.py` | 模型生命周期审计 |
| `requirements.txt` | Python 依赖清单 |
| `Dockerfile` | Docker 镜像构建 |

## 配置项

通过环境变量配置：

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `MODEL_PLATFORM_HOST` | `0.0.0.0` | 服务监听地址 |
| `MODEL_PLATFORM_PORT` | `8082` | 服务端口 |
| `MODEL_PLATFORM_DEBUG` | `false` | 调试模式 |
| `MODEL_PLATFORM_DB_URL` | `mysql+pymysql://...` | 数据库连接 |
| `MODEL_PLATFORM_REDIS_URL` | `redis://localhost:6379/2` | Redis 连接 |
| `MODEL_PLATFORM_GRPC_PORT` | `50051` | gRPC 端口 |

## 构建与运行

```bash
# 安装依赖
pip install -r requirements.txt

# 启动服务
uvicorn app.main:app --host 0.0.0.0 --port 8082

# Docker 构建
docker build -t model-platform .

# Docker 运行
docker run -p 8082:8082 -p 50051:50051 model-platform
```

## 测试

```bash
# 运行全部测试
pytest

# 运行单元测试
pytest tests/unit/

# 运行集成测试
pytest tests/integration/

# 带覆盖率报告
pytest --cov=app --cov-report=html
```

## API 端点

| 前缀 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `/api/v1/data/` | 数据准备 |
| `/api/v1/training/` | 模型训练 |
| `/api/v1/evaluation/` | 模型评估 |
| `/api/v1/export/` | 模型导出 |
| `/api/v1/inference/` | 推理服务 |
| `/api/v1/monitoring/` | 模型监控 |
| `/api/v1/audit/` | 审计日志 |

## 依赖关系

- **上游调用方**: `decision-server`（通过 ModelServiceClient 调用推理 API）
- **下游依赖**: MySQL、Redis
