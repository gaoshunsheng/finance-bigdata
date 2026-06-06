# decision-ui 前端开发指南

本文档是 `decision-ui` 模块的完整前端开发指南，涵盖技术栈、目录结构、开发规范、构建部署等内容。

---

## 1. 技术栈说明

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Vue | 3.5.x | 响应式 UI 框架 |
| 语言 | TypeScript | 6.0.x | 类型安全的 JavaScript 超集 |
| 构建工具 | Vite | 8.0.x | 开发服务器 + 生产构建 |
| UI 组件库 | Ant Design Vue | 4.2.x | 企业级 UI 组件 |
| 图标 | @ant-design/icons-vue | 7.0.x | Ant Design 图标库 |
| 图表 | ECharts + vue-echarts | 6.1.x / 8.0.x | 数据可视化 |
| 图编辑 | AntV X6 (含插件) | 2.19.x | DAG 流程设计器 |
| 状态管理 | Pinia | 3.0.x | 全局状态管理 |
| 路由 | Vue Router | 4.6.x | SPA 路由管理 |
| HTTP 客户端 | Axios | 1.17.x | API 请求 |
| 进度条 | NProgress | 0.2.x | 路由切换加载指示 |
| 日期 | Day.js | 1.11.x | 日期格式化 |
| CSS 预处理 | Less | 4.6.x | CSS 预处理器 |
| 自动导入 | unplugin-vue-components + unplugin-auto-import | - | 组件自动按需导入 |

---

## 2. 目录结构详解

```
decision-ui/
├── public/                  # 静态资源（不经过构建）
├── src/
│   ├── api/                 # API 请求模块
│   │   ├── request.ts       # Axios 实例、拦截器、Token 自动刷新
│   │   ├── auth.ts          # 认证相关 API（登录、登出、用户管理）
│   │   ├── admin.ts         # 管理后台 API
│   │   ├── data.ts          # 数据查询 API
│   │   ├── publish.ts       # 发布中心 API
│   │   └── index.ts         # 统一导出
│   ├── assets/
│   │   └── styles/
│   │       └── global.css   # 全局样式、NProgress 颜色、滚动条、Ant Design 覆盖
│   ├── components/          # 可复用业务组件
│   │   ├── flow/
│   │   │   └── FlowDesigner.vue       # 决策流可视化设计器（SVG 画布）
│   │   ├── rule/
│   │   │   ├── ConditionBuilder.vue   # 条件组合器
│   │   │   ├── ConditionNode.vue      # 单个条件节点
│   │   │   └── ActionEditor.vue       # 动作编辑器
│   │   ├── scorecard/
│   │   │   └── ScorecardEditor.vue    # 评分卡编辑器
│   │   └── table/
│   │       └── TableEditor.vue        # 决策表编辑器
│   ├── layouts/
│   │   └── AdminLayout.vue            # 管理后台主布局（侧边栏 + 顶栏 + 内容区）
│   ├── router/
│   │   └── index.ts        # 路由配置 + 导航守卫 + NProgress
│   ├── stores/              # Pinia 状态仓库
│   │   ├── auth.ts          # 认证状态（用户信息、角色、权限）
│   │   └── app.ts           # 应用状态（侧边栏折叠、面包屑）
│   ├── types/               # TypeScript 类型定义
│   │   ├── api.ts           # 通用 API 响应结构、分页类型
│   │   ├── auth.ts          # 用户、角色、权限类型
│   │   ├── rule.ts          # 规则相关类型
│   │   ├── scorecard.ts     # 评分卡类型
│   │   ├── flow.ts          # 决策流节点、边、模型类型
│   │   ├── variable.ts      # 变量类型
│   │   ├── experiment.ts    # 实验管理类型
│   │   └── index.ts         # 统一导出
│   ├── utils/               # 工具函数
│   │   ├── token.ts         # Token 存取（localStorage 封装）
│   │   └── index.ts         # 通用工具（角色标签映射等）
│   ├── views/               # 页面视图
│   │   ├── login/           # 登录页
│   │   ├── dashboard/       # 首页概览（同 DashboardView.vue）
│   │   ├── rule/            # 条件规则（列表 + 编辑）
│   │   ├── scorecard/       # 评分卡（列表 + 编辑）
│   │   ├── table/           # 决策表（列表 + 编辑）
│   │   ├── flow/            # 决策流（列表 + 设计器）
│   │   ├── variable/        # 变量管理
│   │   ├── experiment/      # 实验管理（列表 + 详情）
│   │   ├── sandbox/         # 沙箱测试
│   │   ├── publish/         # 发布中心
│   │   ├── report/          # 决策报告（列表 + 详情）
│   │   └── analytics/       # 分析看板（ECharts 图表）
│   ├── App.vue              # 根组件
│   ├── main.ts              # 应用入口
│   └── env.d.ts             # 环境变量类型声明
├── index.html               # HTML 入口
├── vite.config.ts           # Vite 配置（代理、别名、组件自动导入）
├── tsconfig.json            # TypeScript 根配置
├── tsconfig.app.json        # 应用 TS 配置
├── tsconfig.node.json       # Node 端 TS 配置
├── components.d.ts          # 自动生成的组件类型声明
├── Dockerfile               # 多阶段 Docker 构建（Node 构建 + Nginx 运行）
└── package.json             # 依赖和脚本
```

### 目录职责说明

| 目录 | 职责 | 约定 |
|------|------|------|
| `api/` | 按 Backend Service Domain 拆分 API 模块，统一通过 `request.ts` 发起请求 | 文件名与后端服务对应，函数名语义化 |
| `components/` | 可复用业务组件，按功能域分子目录 | 组件名使用 PascalCase，文件名与组件名一致 |
| `views/` | 页面级组件，每个路由对应一个 view 文件 | 列表页以 `*ListView.vue` 命名，编辑页以 `*EditView.vue` 命名 |
| `stores/` | Pinia 状态仓库，按功能域拆分 | 使用 Composition API 风格 (`defineStore('name', () => {...})`) |
| `types/` | TypeScript 类型定义，与后端 DTO 对应 | 文件名与 `api/` 模块一一对应 |
| `utils/` | 纯工具函数，无副作用 | 保持函数纯净，便于单元测试 |

---

## 3. 开发服务器启动

### 3.1 环境准备

- Node.js >= 20.x
- npm >= 9.x

### 3.2 安装依赖

```bash
cd decision-ui
npm install
```

### 3.3 启动开发服务器

```bash
npm run dev
```

开发服务器默认运行在 `http://localhost:3000`。

### 3.4 环境变量配置

环境变量通过 Vite 的 `import.meta.env` 读取。支持以下变量：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `VITE_API_BASE_URL` | API 基础路径 | `/api/v1` |

可在项目根目录创建 `.env.local` 文件覆盖默认值：

```
VITE_API_BASE_URL=/api/v1
```

### 3.5 Vite 代理配置

开发环境下，Vite 已配置 API 代理将请求转发到后端服务：

```typescript
// vite.config.ts
server: {
  port: 3000,
  proxy: {
    '/api/v1/decision':  { target: 'http://localhost:8080', changeOrigin: true },
    '/api/v1/features':  { target: 'http://localhost:8083', changeOrigin: true },
    '/api/v1/profile':   { target: 'http://localhost:8083', changeOrigin: true },
    '/api/v1/reports':   { target: 'http://localhost:8083', changeOrigin: true },
    '/api':              { target: 'http://localhost:8081', changeOrigin: true },
  },
}
```

路径别名 `@` 已映射到 `src/` 目录，可在导入时直接使用：

```typescript
import { useAuthStore } from '@/stores/auth'
```

### 3.6 其他脚本

| 命令 | 说明 |
|------|------|
| `npm run dev` | 启动开发服务器（端口 3000） |
| `npm run build` | TypeScript 类型检查 + 生产构建 |
| `npm run preview` | 本地预览生产构建产物 |

---

## 4. 组件开发规范

### 4.1 Composition API + `<script setup>`

所有组件统一使用 `<script setup lang="ts">` 语法：

```vue
<template>
  <div class="my-component">
    <a-button @click="handleClick">{{ label }}</a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

// Props 定义
const props = defineProps<{
  label?: string
  modelValue?: string
}>()

// Emits 定义
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

// 响应式数据
const count = ref(0)

// 计算属性
const doubleCount = computed(() => count.value * 2)

// 方法
function handleClick() {
  count.value++
  emit('update:modelValue', String(count.value))
}

// 生命周期
onMounted(() => {
  // 初始化逻辑
})

onBeforeUnmount(() => {
  // 清理逻辑（事件监听、定时器等）
})
</script>

<style scoped>
.my-component {
  padding: 16px;
}
</style>
```

### 4.2 Props 和 Emit 类型定义

Props 使用泛型方式定义类型，避免使用 `defineProps` 的对象语法：

```typescript
// 推荐
const props = defineProps<{
  title: string
  count?: number
  items: Array<{ id: string; name: string }>
}>()

// 带默认值
const props = withDefaults(defineProps<{
  title: string
  pageSize?: number
}>(), {
  pageSize: 10,
})
```

Emit 使用类型化签名：

```typescript
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'save', data: FlowModel): void
  (e: 'delete', id: string): void
}>()
```

### 4.3 组件命名规范

| 场景 | 命名规则 | 示例 |
|------|---------|------|
| 页面组件 | PascalCase + View 后缀 | `RuleListView.vue`, `RuleEditView.vue` |
| 业务组件 | PascalCase | `ConditionBuilder.vue`, `FlowDesigner.vue` |
| 目录名 | kebab-case | `flow/`, `scorecard/`, `rule/` |
| 路由 name | PascalCase | `RuleList`, `RuleCreate`, `FlowDesign` |

### 4.4 样式规范

- 所有组件样式使用 `<style scoped>`，防止样式泄漏
- 全局样式仅在 `src/assets/styles/global.css` 中定义
- 使用 Less 预处理器时，Vite 已配置 `javascriptEnabled: true`
- 主色调统一使用 Ant Design 的 `#1677ff`

### 4.5 Ant Design Vue 组件使用

项目通过 `unplugin-vue-components` 配置了 Ant Design Vue 的自动按需导入：

```typescript
// vite.config.ts
Components({
  resolvers: [
    AntDesignVueResolver({
      importStyle: false,
    }),
  ],
})
```

在模板中可以直接使用 Ant Design Vue 组件，无需手动导入：

```vue
<template>
  <a-button type="primary">按钮</a-button>
  <a-table :columns="columns" :dataSource="data" />
</template>
```

图标组件仍需手动导入：

```typescript
import { DashboardOutlined, EditOutlined } from '@ant-design/icons-vue'
```

---

## 5. 状态管理规范

### 5.1 Store 设计模式

所有 Store 统一使用 Composition API 风格：

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useMyStore = defineStore('myStore', () => {
  // State
  const items = ref<Item[]>([])
  const loading = ref(false)

  // Getters (computed)
  const itemCount = computed(() => items.value.length)

  // Actions (普通函数，可以是 async)
  async function fetchItems() {
    loading.value = true
    try {
      items.value = await getItemsFromApi()
    } finally {
      loading.value = false
    }
  }

  // 必须返回所有需要暴露的状态和方法
  return { items, loading, itemCount, fetchItems }
})
```

### 5.2 Auth Store (`stores/auth.ts`)

认证 Store 管理用户登录态、角色和权限：

```typescript
const authStore = useAuthStore()

// 状态
authStore.user          // 当前用户信息 (User | null)
authStore.permissions   // 权限列表 (Permission[])
authStore.loading       // 登录中状态

// 计算属性
authStore.isLoggedIn    // 是否已登录
authStore.isAdmin       // 是否管理员
authStore.isApprover    // 是否审批人 (ADMIN 或 APPROVER)
authStore.roleLevel     // 角色等级 (VIEWER=0 < EDITOR=1 < APPROVER=2 < ADMIN=3)

// 方法
authStore.login(username, password)   // 登录
authStore.fetchUser()                 // 刷新用户信息
authStore.logout()                    // 登出

// 权限检查
authStore.hasPermission('rule:write')  // 是否拥有特定权限
authStore.hasRoleLevel(2)              // 角色等级是否 >= 2
```

**角色权限映射**（与后端 `Role.java` 保持一致）：

| 角色 | 权限范围 |
|------|---------|
| VIEWER | 所有模块的只读权限 |
| EDITOR | VIEWER + 写权限 + 沙箱执行 |
| APPROVER | EDITOR + 发布审批 + 灰度管理 |
| ADMIN | APPROVER + 用户管理 + 审计日志 |

### 5.3 App Store (`stores/app.ts`)

应用全局 UI 状态：

```typescript
const appStore = useAppStore()

appStore.sidebarCollapsed   // 侧边栏是否折叠
appStore.breadcrumbs        // 面包屑数据

appStore.toggleSidebar()    // 切换侧边栏折叠状态
appStore.setBreadcrumbs(items)  // 设置面包屑
```

### 5.4 Store 使用注意事项

- Store 在 `main.ts` 中通过 `app.use(createPinia())` 全局注册
- Auth Store 在应用启动时立即初始化用户信息：`authStore.fetchUser()`
- 不要在 Store 外部直接操作 localStorage 中的 Token，统一使用 `@/utils/token.ts` 的工具函数
- 在组件中使用 Store 时直接调用 `useXxxStore()`，无需传参

---

## 6. API 调用规范

### 6.1 Request 模块 (`api/request.ts`)

项目封装了统一的 Axios 实例，所有 API 调用通过该实例发起：

```typescript
import { get, post, put, del } from '@/api/request'

// GET 请求
const data = await get<User>('/auth/me')

// GET 带查询参数
const page = await get<PageResult<Rule>>('/rules', { page: 1, size: 20 })

// POST 请求
const result = await post<Rule>('/rules', { name: '新规则', ... })

// PUT 请求
await put<void>(`/rules/${id}`, { name: '更新规则', ... })

// DELETE 请求
await del<void>(`/rules/${id}`)
```

### 6.2 请求拦截器

请求拦截器自动在 Header 中附加 Bearer Token：

```typescript
// 自动执行，无需手动处理
config.headers.Authorization = `Bearer ${token}`
```

### 6.3 响应拦截器

响应拦截器统一处理业务错误码和 HTTP 错误：

- **业务错误**：当 `data.code` 不为 `0` 或 `200` 时，自动弹出错误提示
- **401 Unauthorized**：自动触发 Token 刷新流程
- **403 Forbidden**：提示"没有操作权限"
- **404 Not Found**：提示"请求的资源不存在"
- **500+ Server Error**：提示"服务器异常"

### 6.4 Token 自动刷新

Token 刷新采用"队列等待"模式，确保并发请求不会重复刷新：

1. 收到 401 响应后，检查是否有 Refresh Token
2. 如无 Refresh Token，清除 Token 并跳转登录页
3. 如正在刷新中（`isRefreshing = true`），将请求加入等待队列
4. 刷新成功后，用新 Token 重试所有队列中的请求
5. 刷新失败，清除 Token 并跳转登录页

```typescript
// 刷新流程（自动执行，开发者无需关心）
// 1. POST /auth/refresh { refreshToken }
// 2. 保存新的 accessToken 和 refreshToken
// 3. 重试所有排队的请求
```

### 6.5 API 模块编写规范

新增 API 模块时，参照 `auth.ts` 的模式：

```typescript
// src/api/feature.ts
import { get, post, put, del } from './request'
import type { FeatureType } from '@/types'

export function listFeatures(params?: PageParams): Promise<PageResult<FeatureType>> {
  return get('/features', params)
}

export function getFeature(id: string): Promise<FeatureType> {
  return get(`/features/${id}`)
}

export function createFeature(data: CreateFeatureRequest): Promise<FeatureType> {
  return post('/features', data)
}

export function updateFeature(id: string, data: UpdateFeatureRequest): Promise<FeatureType> {
  return put(`/features/${id}`, data)
}

export function deleteFeature(id: string): Promise<void> {
  return del(`/features/${id}`)
}
```

然后在 `api/index.ts` 中统一导出：

```typescript
export * from './feature'
```

### 6.6 类型约定

API 函数的返回值类型应与 `types/` 中的类型定义对应，使用泛型参数：

```typescript
// 统一响应结构（types/api.ts）
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 分页请求参数
interface PageParams {
  page?: number
  size?: number
  sort_by?: string
  sort_order?: 'asc' | 'desc'
}

// 分页响应
interface PageResult<T> {
  content: T[]
  total: number
  page: number
  size: number
  total_pages: number
}
```

---

## 7. 路由和权限守卫

### 7.1 路由配置

路由定义在 `src/router/index.ts` 中，使用 `createWebHistory`（HTML5 History 模式）。所有需要认证的页面作为 `AdminLayout` 的子路由：

```typescript
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      // 所有业务页面...
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]
```

### 7.2 路由懒加载

所有页面组件使用动态导入（`() => import(...)`），实现按需加载：

```typescript
{
  path: 'rule',
  name: 'RuleList',
  component: () => import('@/views/rule/RuleListView.vue'),
  meta: { title: '条件规则', parent: '规则管理' },
}
```

### 7.3 路由 Meta 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `title` | `string` | 页面标题，用于面包屑和浏览器标签 |
| `requiresAuth` | `boolean` | 是否需要登录（默认 `true`） |
| `parent` | `string` | 父级菜单名，用于面包屑 |
| `hidden` | `boolean` | 是否在菜单中隐藏（编辑页通常隐藏） |
| `icon` | `string` | 菜单图标名 |

### 7.4 导航守卫 (`beforeEach`)

```typescript
router.beforeEach((to, _from, next) => {
  NProgress.start()

  // 1. 不需要认证的页面直接放行
  if (to.meta.requiresAuth === false) {
    // 已登录用户访问登录页时重定向到首页
    if (getAccessToken() && to.name === 'Login') {
      next({ path: '/dashboard' })
      return
    }
    next()
    return
  }

  // 2. 需要认证但未登录，跳转登录页（携带 redirect 参数）
  if (!getAccessToken()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  next()
})
```

### 7.5 后置守卫 (`afterEach`)

```typescript
router.afterEach((to) => {
  NProgress.done()
  document.title = `${to.meta.title || '决策引擎'} - 风控决策平台`
})
```

### 7.6 NProgress 配置

NProgress 已配置为不显示旋转加载图标：

```typescript
NProgress.configure({ showSpinner: false })
```

自定义颜色在 `global.css` 中设置：

```css
#nprogress .bar {
  background: #1677ff !important;
  height: 2px !important;
}
```

### 7.7 新增路由示例

添加新页面时，按以下步骤操作：

1. 在 `views/` 下创建页面组件
2. 在路由配置的 `children` 数组中添加路由
3. 列表页和编辑页使用相同模式（Create 和 Edit 复用同一个组件）

```typescript
// 列表页
{
  path: 'feature',
  name: 'FeatureList',
  component: () => import('@/views/feature/FeatureListView.vue'),
  meta: { title: '功能管理' },
},
// 新建页
{
  path: 'feature/create',
  name: 'FeatureCreate',
  component: () => import('@/views/feature/FeatureEditView.vue'),
  meta: { title: '新建功能', parent: '功能管理', hidden: true },
},
// 编辑页
{
  path: 'feature/:id',
  name: 'FeatureEdit',
  component: () => import('@/views/feature/FeatureEditView.vue'),
  meta: { title: '编辑功能', parent: '功能管理', hidden: true },
},
```

---

## 8. 类型系统

### 8.1 类型文件组织

类型文件与 API 模块和业务域一一对应：

| 文件 | 内容 |
|------|------|
| `types/api.ts` | 通用响应结构 `ApiResponse<T>`、`PageParams`、`PageResult<T>` |
| `types/auth.ts` | `User`、`LoginRequest`、`LoginResponse`、`Permission` |
| `types/rule.ts` | 规则相关类型 |
| `types/scorecard.ts` | 评分卡相关类型 |
| `types/flow.ts` | 决策流类型 `FlowNode`、`FlowEdge`、`FlowModel`、`NodeType` |
| `types/variable.ts` | 变量相关类型 |
| `types/experiment.ts` | 实验管理相关类型 |
| `types/index.ts` | 统一导出所有类型 |

所有类型通过 `types/index.ts` 统一导出，使用时只需：

```typescript
import type { User, Rule, PageResult } from '@/types'
```

### 8.2 通用 API 响应类型

```typescript
// types/api.ts
interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

interface PageParams {
  page?: number
  size?: number
  sort_by?: string
  sort_order?: 'asc' | 'desc'
}

interface PageResult<T> {
  content: T[]
  total: number
  page: number
  size: number
  total_pages: number
}
```

### 8.3 类型定义规范

- 使用 `interface` 定义数据结构，使用 `type` 定义联合类型和工具类型
- 所有 API 请求和响应都必须有对应的类型定义
- 类型名使用 PascalCase
- 使用 `import type` 语法导入类型，避免运行时引入
- 导出类型时在 `types/index.ts` 中统一 re-export

---

## 9. ECharts 使用规范

### 9.1 基本用法

项目使用 `vue-echarts` 组件（基于 ECharts 6），在分析看板等页面中使用：

```vue
<template>
  <v-chart :option="chartOption" :autoresize="true" style="height: 400px" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'

// 注册必要的组件
use([
  CanvasRenderer,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
])

const chartOption = computed(() => ({
  title: { text: '决策统计' },
  tooltip: {},
  xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed'] },
  yAxis: { type: 'value' },
  series: [{ type: 'bar', data: [120, 200, 150] }],
}))
</script>
```

### 9.2 生命周期管理

- 使用 `:autoresize="true"` 让图表自动适应容器大小
- 在组件卸载时 ECharts 实例会自动销毁（`vue-echarts` 内部处理）
- 如手动创建 ECharts 实例，必须在 `onBeforeUnmount` 中调用 `dispose()`

```typescript
import * as echarts from 'echarts/core'
import { onMounted, onBeforeUnmount, ref } from 'vue'

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

onMounted(() => {
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.setOption({ /* ... */ })
  }
})

// 手动 resize
function handleResize() {
  chartInstance?.resize()
}

onBeforeUnmount(() => {
  chartInstance?.dispose()
  chartInstance = null
  window.removeEventListener('resize', handleResize)
})
```

### 9.3 按需导入

推荐按需导入 ECharts 组件，减少打包体积：

```typescript
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'

use([CanvasRenderer, BarChart, GridComponent])
```

不要使用 `import * as echarts from 'echarts'` 全量导入。

---

## 10. 构建与部署

### 10.1 生产构建

```bash
npm run build
```

该命令先执行 `vue-tsc -b` 进行 TypeScript 类型检查，再执行 `vite build` 生成生产产物。构建输出到 `dist/` 目录。

可通过 `--build-arg` 覆盖构建时 API 基础路径：

```bash
docker build --build-arg VITE_API_BASE_URL=/api/v1 -t finance-decision-ui .
```

### 10.2 Docker 构建

项目提供了多阶段 Dockerfile：

**Stage 1 - 构建**：
- 基础镜像：`node:20-alpine`
- 分层构建：先安装依赖（缓存层），再构建源码
- 使用 npm 镜像加速：`--registry=https://registry.npmmirror.com`

**Stage 2 - 运行**：
- 基础镜像：`nginx:1.25-alpine`
- Nginx 监听端口：`3000`
- SPA 路由回退：所有未匹配路径返回 `index.html`
- 静态资源长缓存：`/assets/` 路径缓存 30 天
- Gzip 压缩已开启
- 健康检查端点：`/health`

```bash
# 构建镜像
docker build -t finance-decision-ui .

# 运行容器
docker run -d -p 3000:3000 --name decision-ui finance-decision-ui
```

### 10.3 Nginx 配置要点

```
server {
    listen 3000;
    server_name _;
    root /usr/share/nginx/html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源长缓存（Vite 构建的 assets 带 hash）
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 健康检查
    location /health {
        return 200 '{"status":"UP"}';
    }

    # Gzip 压缩
    gzip on;
    gzip_min_length 256;
}
```

### 10.4 部署注意事项

- 生产环境中，API 请求通过 Nginx 反向代理转发到后端服务，需在上游 Nginx 或 K8s Ingress 中配置代理规则
- `VITE_API_BASE_URL` 是构建时变量（Vite 静态替换），构建后不可更改，需在构建时确定正确的 API 路径
- Docker 健康检查配置为每 15 秒检查一次 `/health` 端点
- 应用启动后有 10 秒的宽限期（`start-period`）

---

## 附录：常见问题

### Q: 如何新增一个业务模块？

1. 在 `types/` 下创建类型定义文件，并在 `types/index.ts` 中导出
2. 在 `api/` 下创建 API 模块，在 `api/index.ts` 中导出
3. 在 `views/` 下创建 `ListView.vue` 和 `EditView.vue`
4. 在 `router/index.ts` 的 `children` 中添加路由
5. 如需可复用组件，在 `components/` 下创建

### Q: 如何处理跨域问题？

开发环境通过 Vite 代理解决跨域，配置在 `vite.config.ts` 的 `server.proxy` 中。生产环境通过 Nginx 反向代理解决。

### Q: Token 存储在哪里？

Token 存储在 `localStorage` 中，键名分别为 `decision_access_token` 和 `decision_refresh_token`。统一通过 `@/utils/token.ts` 中的函数操作：

```typescript
import { getAccessToken, setTokens, clearTokens } from '@/utils/token'
```

### Q: 如何进行权限控制？

使用 Auth Store 的 `hasPermission` 方法：

```typescript
const authStore = useAuthStore()

// 检查单一权限
if (authStore.hasPermission('rule:write')) {
  // 允许编辑
}

// 检查角色等级
if (authStore.hasRoleLevel(2)) {
  // APPROVER 及以上
}
```

在模板中可以结合 `v-if` 使用：

```vue
<a-button v-if="authStore.hasPermission('publish:approve')" @click="handleApprove">
  审批
</a-button>
```
