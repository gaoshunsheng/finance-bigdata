<template>
  <div>
    <div class="page-header">
      <h3>决策报告</h3>
      <a-input-search
        v-model:value="searchKey"
        placeholder="搜索 traceId / 申请人"
        style="width: 320px"
        @search="onSearch"
      />
    </div>
    <a-table :columns="columns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="traceId">
      <template #emptyText><a-empty description="暂无决策报告" /></template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const searchKey = ref('')

const columns = [
  { title: 'TraceID', dataIndex: 'traceId', key: 'traceId', width: 200 },
  { title: '策略', dataIndex: 'strategyName', key: 'strategyName' },
  { title: '结果', dataIndex: 'result', key: 'result', width: 100 },
  { title: '耗时(ms)', dataIndex: 'durationMs', key: 'durationMs', width: 100 },
  { title: '渠道', dataIndex: 'channel', key: 'channel', width: 80 },
  { title: '决策时间', dataIndex: 'decidedAt', key: 'decidedAt', width: 180 },
  { title: '操作', key: 'action', width: 100 },
]

function onSearch() {
  // TODO: 调用 API 搜索
}
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
