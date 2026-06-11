<template>
  <div>
    <div class="page-header">
      <h3>评分卡管理</h3>
      <a-button type="primary" @click="$router.push('/scorecard/create')">
        <template #icon><PlusOutlined /></template> 新建评分卡
      </a-button>
    </div>
    <a-table :columns="columns" :data-source="data" :loading="loading" :pagination="{ pageSize: 20 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="$router.push(`/scorecard/${record.id}`)">查看</a-button>
            <a-popconfirm title="确认删除?" ok-text="删除" cancel-text="取消" @confirm="handleDelete(record.id)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
      <template #emptyText><a-empty description="暂无评分卡数据" /></template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { listScorecards, deleteScorecard } from '@/api/admin'
import { statusColor, statusLabel } from '@/utils'

const loading = ref(false)
const data = ref<any[]>([])

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'action', width: 200 },
]

async function loadData() {
  loading.value = true
  try {
    data.value = await listScorecards({}) as any[]
  } catch {
    message.error('加载评分卡列表失败')
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: string) {
  try {
    await deleteScorecard(id)
    message.success('已删除')
    await loadData()
  } catch {
    message.error('删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h3 { margin: 0; }
</style>
