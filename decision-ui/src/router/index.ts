import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getAccessToken } from '@/utils/token'

NProgress.configure({ showSpinner: false })

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
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '首页概览', icon: 'DashboardOutlined' },
      },
      // 规则管理
      {
        path: 'rule',
        name: 'RuleList',
        component: () => import('@/views/rule/RuleListView.vue'),
        meta: { title: '条件规则', parent: '规则管理' },
      },
      {
        path: 'rule/create',
        name: 'RuleCreate',
        component: () => import('@/views/rule/RuleEditView.vue'),
        meta: { title: '新建规则', parent: '规则管理', hidden: true },
      },
      {
        path: 'rule/:id',
        name: 'RuleEdit',
        component: () => import('@/views/rule/RuleEditView.vue'),
        meta: { title: '编辑规则', parent: '规则管理', hidden: true },
      },
      // 评分卡
      {
        path: 'scorecard',
        name: 'ScorecardList',
        component: () => import('@/views/scorecard/ScorecardListView.vue'),
        meta: { title: '评分卡', parent: '评分卡管理' },
      },
      {
        path: 'scorecard/create',
        name: 'ScorecardCreate',
        component: () => import('@/views/scorecard/ScorecardEditView.vue'),
        meta: { title: '新建评分卡', parent: '评分卡管理', hidden: true },
      },
      {
        path: 'scorecard/:id',
        name: 'ScorecardEdit',
        component: () => import('@/views/scorecard/ScorecardEditView.vue'),
        meta: { title: '编辑评分卡', parent: '评分卡管理', hidden: true },
      },
      // 决策表
      {
        path: 'table',
        name: 'TableList',
        component: () => import('@/views/table/TableListView.vue'),
        meta: { title: '决策表', parent: '决策表管理' },
      },
      {
        path: 'table/create',
        name: 'TableCreate',
        component: () => import('@/views/table/TableEditView.vue'),
        meta: { title: '新建决策表', parent: '决策表管理', hidden: true },
      },
      {
        path: 'table/:id',
        name: 'TableEdit',
        component: () => import('@/views/table/TableEditView.vue'),
        meta: { title: '编辑决策表', parent: '决策表管理', hidden: true },
      },
      // 决策流
      {
        path: 'flow',
        name: 'FlowList',
        component: () => import('@/views/flow/FlowListView.vue'),
        meta: { title: '决策流', parent: '决策流管理' },
      },
      {
        path: 'flow/create',
        name: 'FlowCreate',
        component: () => import('@/views/flow/FlowDesignView.vue'),
        meta: { title: '新建决策流', parent: '决策流管理', hidden: true },
      },
      {
        path: 'flow/:id',
        name: 'FlowDesign',
        component: () => import('@/views/flow/FlowDesignView.vue'),
        meta: { title: '设计决策流', parent: '决策流管理', hidden: true },
      },
      // 变量管理
      {
        path: 'variable',
        name: 'VariableList',
        component: () => import('@/views/variable/VariableListView.vue'),
        meta: { title: '变量管理' },
      },
      // 实验管理
      {
        path: 'experiment',
        name: 'ExperimentList',
        component: () => import('@/views/experiment/ExperimentListView.vue'),
        meta: { title: '实验管理' },
      },
      {
        path: 'experiment/:id',
        name: 'ExperimentDetail',
        component: () => import('@/views/experiment/ExperimentDetailView.vue'),
        meta: { title: '实验详情', parent: '实验管理', hidden: true },
      },
      // 沙箱测试
      {
        path: 'sandbox',
        name: 'Sandbox',
        component: () => import('@/views/sandbox/SandboxView.vue'),
        meta: { title: '沙箱测试' },
      },
      // 发布中心
      {
        path: 'publish',
        name: 'PublishCenter',
        component: () => import('@/views/publish/PublishCenterView.vue'),
        meta: { title: '发布中心' },
      },
      // 决策报告
      {
        path: 'report',
        name: 'ReportList',
        component: () => import('@/views/report/ReportListView.vue'),
        meta: { title: '决策报告', parent: '决策日志' },
      },
      {
        path: 'report/:traceId',
        name: 'ReportDetail',
        component: () => import('@/views/report/ReportDetailView.vue'),
        meta: { title: '报告详情', parent: '决策日志', hidden: true },
      },
      // 分析看板
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/analytics/AnalyticsView.vue'),
        meta: { title: '分析看板' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  NProgress.start()

  // 不需要认证的页面
  if (to.meta.requiresAuth === false) {
    if (getAccessToken() && to.name === 'Login') {
      next({ path: '/dashboard' })
      return
    }
    next()
    return
  }

  // 需要认证
  if (!getAccessToken()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  next()
})

router.afterEach((to) => {
  NProgress.done()
  document.title = `${to.meta.title || '决策引擎'} - 风控决策平台`
})

export default router
