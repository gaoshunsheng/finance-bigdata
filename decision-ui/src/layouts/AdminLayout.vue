<template>
  <a-layout class="admin-layout" :style="{ minHeight: '100vh' }">
    <!-- 侧边栏 -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      collapsible
      :trigger="null"
      width="220"
      collapsed-width="64"
      class="admin-sider"
      theme="dark"
    >
      <!-- Logo -->
      <div class="sider-logo" @click="router.push('/dashboard')">
        <SafetyCertificateOutlined class="logo-icon" />
        <span v-show="!collapsed" class="logo-text">风控决策平台</span>
      </div>

      <!-- 菜单 -->
      <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        theme="dark"
        mode="inline"
        @click="onMenuClick"
      >
        <a-menu-item key="/dashboard">
          <template #icon><DashboardOutlined /></template>
          <span>首页概览</span>
        </a-menu-item>

        <a-sub-menu key="rule-mgmt">
          <template #icon><ApartmentOutlined /></template>
          <template #title>规则管理</template>
          <a-menu-item key="/rule">条件规则</a-menu-item>
          <a-menu-item key="/scorecard">评分卡</a-menu-item>
          <a-menu-item key="/table">决策表</a-menu-item>
          <a-menu-item key="/flow">决策流</a-menu-item>
        </a-sub-menu>

        <a-menu-item key="/variable">
          <template #icon><FunctionOutlined /></template>
          <span>变量管理</span>
        </a-menu-item>

        <a-menu-item key="/experiment">
          <template #icon><ExperimentOutlined /></template>
          <span>实验管理</span>
        </a-menu-item>

        <a-menu-item key="/sandbox">
          <template #icon><BugOutlined /></template>
          <span>沙箱测试</span>
        </a-menu-item>

        <a-menu-item key="/publish">
          <template #icon><RocketOutlined /></template>
          <span>发布中心</span>
        </a-menu-item>

        <a-sub-menu key="log-mgmt">
          <template #icon><FileSearchOutlined /></template>
          <template #title>决策日志</template>
          <a-menu-item key="/report">决策报告</a-menu-item>
        </a-sub-menu>

        <a-menu-item key="/analytics">
          <template #icon><BarChartOutlined /></template>
          <span>分析看板</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <!-- 右侧内容 -->
    <a-layout>
      <!-- 顶部栏 -->
      <a-layout-header class="admin-header">
        <div class="header-left">
          <component
            :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined"
            class="trigger-icon"
            @click="collapsed = !collapsed"
          />
          <!-- 面包屑 -->
          <a-breadcrumb class="header-breadcrumb">
            <a-breadcrumb-item>
              <router-link to="/dashboard">首页</router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item v-if="route.meta.parent">
              {{ route.meta.parent }}
            </a-breadcrumb-item>
            <a-breadcrumb-item>{{ route.meta.title }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>

        <div class="header-right">
          <a-dropdown>
            <span class="user-info">
              <a-avatar :size="28" style="background-color: #1677ff">
                {{ userStore.user?.displayName?.charAt(0) || 'U' }}
              </a-avatar>
              <span class="user-name">{{ userStore.user?.displayName || '用户' }}</span>
              <DownOutlined />
            </span>
            <template #overlay>
              <a-menu>
                <a-menu-item key="role" disabled>
                  <TagOutlined /> {{ roleLabel(userStore.user?.role || '') }}
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <!-- 内容区 -->
      <a-layout-content class="admin-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  DashboardOutlined,
  ApartmentOutlined,
  FunctionOutlined,
  ExperimentOutlined,
  BugOutlined,
  RocketOutlined,
  FileSearchOutlined,
  BarChartOutlined,
  SafetyCertificateOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DownOutlined,
  LogoutOutlined,
  TagOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/utils'

const router = useRouter()
const route = useRoute()
const userStore = useAuthStore()

const collapsed = ref(false)
const selectedKeys = computed(() => {
  const path = route.path
  // 精确匹配路由前缀
  if (path.startsWith('/rule')) return ['/rule']
  if (path.startsWith('/scorecard')) return ['/scorecard']
  if (path.startsWith('/table')) return ['/table']
  if (path.startsWith('/flow')) return ['/flow']
  return [path]
})

const openKeys = ref<string[]>(['rule-mgmt', 'log-mgmt'])

// 同步展开的子菜单
watch(() => route.path, () => {
  const rulePaths = ['/rule', '/scorecard', '/table', '/flow']
  if (rulePaths.some(p => route.path.startsWith(p))) {
    if (!openKeys.value.includes('rule-mgmt')) {
      openKeys.value.push('rule-mgmt')
    }
  }
  if (route.path.startsWith('/report')) {
    if (!openKeys.value.includes('log-mgmt')) {
      openKeys.value.push('log-mgmt')
    }
  }
}, { immediate: true })

function onMenuClick({ key }: { key: string }) {
  router.push(key)
}

async function handleLogout() {
  await userStore.logout()
  message.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-sider {
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.sider-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  margin: 12px 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  overflow: hidden;
  white-space: nowrap;
}

.sider-logo:hover {
  background: rgba(255, 255, 255, 0.2);
}

.logo-icon {
  font-size: 22px;
  color: #1677ff;
  flex-shrink: 0;
}

.logo-text {
  margin-left: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  height: 48px;
  line-height: 48px;
  z-index: 9;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.trigger-icon {
  font-size: 18px;
  cursor: pointer;
  transition: color 0.3s;
}

.trigger-icon:hover {
  color: #1677ff;
}

.header-breadcrumb {
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 0 8px;
  transition: background 0.3s;
}

.user-info:hover {
  background: rgba(0, 0, 0, 0.04);
  border-radius: 4px;
}

.user-name {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.85);
}

.admin-content {
  margin: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 6px;
  min-height: calc(100vh - 80px);
  overflow-y: auto;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
