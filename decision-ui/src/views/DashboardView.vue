<template>
  <div class="dashboard">
    <a-row :gutter="[16, 16]">
      <a-col :span="6">
        <a-card>
          <a-statistic title="条件规则" :value="stats.rules" class="stat-card">
            <template #prefix><ApartmentOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic title="评分卡" :value="stats.scorecards" class="stat-card">
            <template #prefix><FundOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic title="决策表" :value="stats.tables" class="stat-card">
            <template #prefix><TableOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic title="决策流" :value="stats.flows" class="stat-card">
            <template #prefix><ApartmentOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :span="16">
        <a-card title="快速操作">
          <a-row :gutter="[16, 16]">
            <a-col :span="6">
              <a-button type="primary" block @click="$router.push('/rule/create')">
                <template #icon><PlusOutlined /></template>
                新建规则
              </a-button>
            </a-col>
            <a-col :span="6">
              <a-button block @click="$router.push('/scorecard/create')">
                <template #icon><PlusOutlined /></template>
                新建评分卡
              </a-button>
            </a-col>
            <a-col :span="6">
              <a-button block @click="$router.push('/table/create')">
                <template #icon><PlusOutlined /></template>
                新建决策表
              </a-button>
            </a-col>
            <a-col :span="6">
              <a-button block @click="$router.push('/flow/create')">
                <template #icon><PlusOutlined /></template>
                新建决策流
              </a-button>
            </a-col>
          </a-row>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="系统信息">
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="平台版本">v1.0.0</a-descriptions-item>
            <a-descriptions-item label="引擎状态">
              <a-badge status="success" text="运行中" />
            </a-descriptions-item>
            <a-descriptions-item label="当前用户">{{ authStore.user?.displayName }}</a-descriptions-item>
            <a-descriptions-item label="角色">{{ roleLabel(authStore.user?.role || '') }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import {
  ApartmentOutlined,
  FundOutlined,
  TableOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/utils'
import { listRules, listScorecards, listTables, listFlows } from '@/api/admin'

const authStore = useAuthStore()

const stats = reactive({
  rules: 0,
  scorecards: 0,
  tables: 0,
  flows: 0,
})

async function loadStats() {
  try {
    const [rules, scorecards, tables, flows] = await Promise.allSettled([
      listRules(),
      listScorecards(),
      listTables(),
      listFlows(),
    ])

    if (rules.status === 'fulfilled' && rules.value) {
      stats.rules = (rules.value as any).length ?? 0
    }
    if (scorecards.status === 'fulfilled' && scorecards.value) {
      stats.scorecards = (scorecards.value as any).length ?? 0
    }
    if (tables.status === 'fulfilled' && tables.value) {
      stats.tables = (tables.value as any).length ?? 0
    }
    if (flows.status === 'fulfilled' && flows.value) {
      stats.flows = (flows.value as any).length ?? 0
    }
  } catch {
    // Silently keep zeros on error
  }
}

onMounted(loadStats)
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
}

.stat-card {
  text-align: center;
}
</style>
