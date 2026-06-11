<template>
  <div>
    <div class="page-header">
      <h3>决策报告</h3>
      <a-space>
        <a-input-search v-model:value="searchKey" placeholder="搜索 traceId / 客户ID" style="width: 320px" @search="loadReports" />
        <a-button type="primary" @click="loadReports">查询</a-button>
      </a-space>
    </div>
    <a-alert v-if="esError" message="决策日志查询暂不可用" type="info" show-icon style="margin-bottom:12px" closable />
    <a-table :columns="columns" :data-source="data" :loading="loading" :pagination="{ pageSize: 20 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key==='actionType'">
          <a-tag :color="actionColor(record.action)">{{ record.action }}</a-tag>
        </template>
        <template v-else-if="column.key==='operation'">
          <a-button type="link" size="small" @click="showDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="detailVisible" title="日志详情" :footer="null" width="800px">
      <a-descriptions v-if="detailRecord" bordered size="small" :column="2">
        <a-descriptions-item label="ID">{{ detailRecord.id }}</a-descriptions-item>
        <a-descriptions-item label="操作类型"><a-tag :color="actionColor(detailRecord.action)">{{ detailRecord.action }}</a-tag></a-descriptions-item>
        <a-descriptions-item label="目标类型">{{ detailRecord.target_type }}</a-descriptions-item>
        <a-descriptions-item label="目标ID">{{ detailRecord.target_id }}</a-descriptions-item>
        <a-descriptions-item label="操作人">{{ detailRecord.operator }}</a-descriptions-item>
        <a-descriptions-item label="版本">{{ detailRecord.target_version ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="备注" :span="2">{{ detailRecord.comment || '-' }}</a-descriptions-item>
        <a-descriptions-item label="时间">{{ detailRecord.operated_at }}</a-descriptions-item>
        <a-descriptions-item label="快照" :span="2" v-if="detailRecord.after_snapshot">
          <a-typography-paragraph code :content="detailRecord.after_snapshot" style="max-height:200px;overflow:auto;margin:0" />
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get } from '@/api/request'

const loading = ref(false)
const searchKey = ref('')
const data = ref<any[]>([])
const esError = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '操作类型', dataIndex: 'action', key: 'actionType', width: 100 },
  { title: '目标类型', dataIndex: 'target_type', key: 'target_type', width: 120 },
  { title: '目标ID', dataIndex: 'target_id', key: 'target_id', width: 160, ellipsis: true },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 120 },
  { title: '备注', dataIndex: 'comment', key: 'comment', ellipsis: true },
  { title: '时间', dataIndex: 'operated_at', key: 'operated_at', width: 180 },
  { title: '操作', key: 'operation', width: 80 },
]

function actionColor(r: string): string {
  const m: Record<string,string> = { CREATE:'blue', APPROVE:'green', REJECT:'red', PUBLISH:'purple', SUBMIT:'orange', WITHDRAW:'default' }
  return m[r] || 'default'
}

const detailVisible = ref(false)
const detailRecord = ref<any>(null)
function showDetail(r: any) { detailRecord.value = r; detailVisible.value = true }

async function loadReports() {
  loading.value = true; esError.value = false
  try {
    const params: any = { size: 100 }
    if (searchKey.value) params.targetId = searchKey.value
    const res: any = await get('/logs', params)
    data.value = res?.records || (Array.isArray(res) ? res : [])
  } catch {
    esError.value = true
    data.value = []
  }
  finally { loading.value = false }
}

onMounted(loadReports)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px }
.page-header h3 { margin: 0 }
</style>
