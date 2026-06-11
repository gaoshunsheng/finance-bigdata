<template>
  <div>
    <div class="page-header">
      <h3>实验管理</h3>
      <a-button type="primary" @click="$router.push('/experiment/create')"><template #icon><PlusOutlined /></template> 新建实验</a-button>
    </div>
    <a-table :columns="columns" :data-source="data" :loading="loading" :pagination="{ pageSize: 20 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record._enabled ? 'green' : 'default'">{{ record._enabled ? '运行中' : '已停止' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'groups'">
          <span>{{ record._groupCount }}</span>
        </template>
        <template v-else-if="column.key === 'trafficKey'">
          <span>{{ record._trafficKey }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="$router.push(`/experiment/${record.id}`)">查看</a-button>
            <a-popconfirm title="确认删除?" @confirm="handleDelete(record.id)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { listExperiments, deleteExperiment } from '@/api/admin'

const loading = ref(false)
const data = ref<any[]>([])

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '流量键', key: 'trafficKey', width: 140 },
  { title: '状态', key: 'status', width: 100 },
  { title: '分组数', key: 'groups', width: 80 },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'action', width: 160 },
]

async function loadData() {
  loading.value = true
  try {
    const raw = await listExperiments({}) as any[]
    data.value = raw.map(r => {
      let c: any = {}
      try { c = typeof r.content === 'string' ? JSON.parse(r.content) : (r.content || {}) } catch {}
      return {
        ...r,
        _enabled: c.enabled ?? false,
        _trafficKey: c.trafficKey || '-',
        _groupCount: c.groups?.length ?? 0,
      }
    })
  } catch { message.error('加载失败') }
  finally { loading.value = false }
}

async function handleDelete(id: string) {
  try { await deleteExperiment(id); message.success('已删除'); await loadData() }
  catch { message.error('删除失败') }
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px }
.page-header h3 { margin: 0 }
</style>
