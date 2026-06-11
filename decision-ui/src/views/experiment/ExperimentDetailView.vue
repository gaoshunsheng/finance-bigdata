<template>
  <div>
    <a-page-header :title="experiment?.name || '实验详情'" @back="$router.back()" />
    <a-spin :spinning="loading">
      <template v-if="experiment">
        <a-descriptions bordered :column="2" style="margin-bottom: 16px">
          <a-descriptions-item label="实验ID">{{ experiment.id }}</a-descriptions-item>
          <a-descriptions-item label="名称">{{ experiment.name }}</a-descriptions-item>
          <a-descriptions-item label="版本">v{{ experiment.version }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="experiment.content?.enabled ? 'green' : 'default'">
              {{ experiment.content?.enabled ? '运行中' : '已停止' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="流量键">{{ experiment.content?.trafficKey || '-' }}</a-descriptions-item>
          <a-descriptions-item label="终止条件">{{ experiment.content?.terminationCondition || '-' }}</a-descriptions-item>
          <a-descriptions-item label="描述" :span="2">{{ experiment.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="创建人">{{ experiment.createdBy }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ experiment.updatedAt }}</a-descriptions-item>
        </a-descriptions>

        <a-divider>实验分组</a-divider>
        <a-table :columns="groupColumns" :data-source="experiment.content?.groups || []" :pagination="false" row-key="groupId" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'trafficRatio'">
              <a-progress :percent="Math.round(record.trafficRatio * 100)" :size="'small'" />
            </template>
          </template>
        </a-table>
      </template>
      <a-empty v-else-if="!loading" description="未找到实验数据" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { getExperiment } from '@/api/admin'

const route = useRoute()
const loading = ref(false)
const experiment = ref<any>(null)

const groupColumns = [
  { title: '分组ID', dataIndex: 'groupId', key: 'groupId' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '流量比例', key: 'trafficRatio', width: 160 },
  { title: '绑定策略', dataIndex: 'strategyId', key: 'strategyId' },
]

onMounted(async () => {
  const id = route.params.id as string
  if (!id) return
  loading.value = true
  try {
    const raw = await getExperiment(id) as any
    const content = typeof raw.content === 'string' ? JSON.parse(raw.content) : (raw.content || {})
    experiment.value = { ...raw, content }
  } catch {
    message.error('加载实验数据失败')
  } finally { loading.value = false }
})
</script>
