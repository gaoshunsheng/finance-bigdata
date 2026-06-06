# decision-ui

> 风控决策平台前端，基于 Vue 3 + TypeScript + Ant Design Vue 构建的单页应用。

## 功能概述

- **首页概览** — 决策平台 Dashboard，展示关键指标和统计图表
- **规则管理** — 条件规则的可视化创建、编辑和列表管理，支持条件树构建器
- **评分卡管理** — 评分卡的可视化编辑，支持特征分箱配置
- **决策表管理** — 决策表的创建和编辑
- **决策流设计** — 基于 AntV X6 的可视化决策流 DAG 设计器
- **变量管理** — 变量定义的列表管理
- **实验管理** — A/B 实验的列表和详情查看
- **沙箱测试** — 在线规则调试，支持历史样本回放
- **发布中心** — 规则发布、审批、灰度发布的统一管理
- **决策报告** — 决策日志查询和执行详情展示
- **分析看板** — 数据可视化分析（ECharts）
- **认证鉴权** — JWT 登录认证，路由守卫

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.x | 前端框架 |
| TypeScript | 6.0.x | 类型安全 |
| Vite | 8.0.x | 构建工具 |
| Ant Design Vue | 4.2.x | UI 组件库 |
| Vue Router | 4.6.x | 路由管理 |
| Pinia | 3.0.x | 状态管理 |
| AntV X6 | 2.19.x | DAG 可视化设计器 |
| ECharts / vue-echarts | 6.1.x / 8.0.x | 数据可视化图表 |
| Axios | 1.17.x | HTTP 请求 |
| Day.js | 1.11.x | 日期处理 |
| Less | 4.6.x | CSS 预处理器 |

## 目录结构

```
src/
├── api/                — API 接口封装
│   ├── request.ts          — Axios 实例和请求拦截器
│   ├── index.ts            — API 统一导出
│   ├── admin.ts            — 管理后台 API
│   ├── auth.ts             — 认证 API
│   ├── data.ts             — 数据平台 API
│   └── publish.ts          — 发布相关 API
├── components/         — 公共组件
│   ├── rule/               — 规则编辑组件
│   │   ├── ConditionBuilder.vue — 条件构建器
│   │   ├── ConditionNode.vue    — 条件节点
│   │   └── ActionEditor.vue     — 动作编辑器
│   ├── scorecard/          — 评分卡编辑组件
│   │   └── ScorecardEditor.vue
│   ├── table/              — 决策表编辑组件
│   │   └── TableEditor.vue
│   └── flow/               — 决策流组件
│       └── FlowDesigner.vue    — DAG 可视化设计器
├── views/              — 页面视图
│   ├── DashboardView.vue      — 首页概览
│   ├── login/LoginView.vue    — 登录页
│   ├── rule/                  — 规则管理
│   ├── scorecard/             — 评分卡管理
│   ├── table/                 — 决策表管理
│   ├── flow/                  — 决策流管理
│   ├── variable/              — 变量管理
│   ├── experiment/            — 实验管理
│   ├── sandbox/SandboxView.vue — 沙箱测试
│   ├── publish/PublishCenterView.vue — 发布中心
│   ├── report/                — 决策报告
│   └── analytics/             — 分析看板
├── layouts/            — 布局组件
│   └── AdminLayout.vue        — 管理后台布局（侧边栏 + 顶栏 + 内容区）
├── router/             — 路由配置
│   └── index.ts              — 路由定义和导航守卫
├── stores/             — Pinia 状态管理
│   ├── app.ts                — 应用全局状态
│   └── auth.ts               — 认证状态
├── types/              — TypeScript 类型定义
│   ├── api.ts                — API 响应类型
│   ├── auth.ts               — 认证类型
│   ├── rule.ts               — 规则类型
│   ├── scorecard.ts          — 评分卡类型
│   ├── flow.ts               — 决策流类型
│   ├── variable.ts           — 变量类型
│   ├── experiment.ts         — 实验类型
│   └── index.ts              — 统一导出
├── utils/              — 工具函数
│   ├── index.ts              — 通用工具
│   └── token.ts              — Token 管理
├── assets/             — 静态资源
├── App.vue             — 根组件
├── main.ts             — 应用入口
└── env.d.ts            — 环境类型声明
```

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器（端口 3000）
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

## 开发服务器代理配置

Vite 开发服务器已配置代理，自动转发 API 请求：

| 路径 | 目标服务 |
|------|---------|
| `/api/v1/decision` | `http://localhost:8080`（decision-server） |
| `/api/v1/features` | `http://localhost:8083`（data-service） |
| `/api/v1/profile` | `http://localhost:8083`（data-service） |
| `/api/v1/reports` | `http://localhost:8083`（data-service） |
| `/api` | `http://localhost:8081`（decision-admin） |

## 构建与部署

```bash
# TypeScript 类型检查 + 构建
npm run build

# 输出目录: dist/
# 部署到 Nginx 或 CDN
```

## 依赖关系

- **上游服务**: `decision-admin`（8081）、`decision-server`（8080）、`data-service`（8083）
