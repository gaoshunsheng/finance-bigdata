<template>
  <div>
    <div class="page-header">
      <h3>实验管理</h3>
      <a-button type="primary">
        <template #icon><PlusOutlined /></template> 新建实验
      </a-button>
    </div>
    <a-table :columns="columns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="id">
      <template #emptyText><a-empty description="暂无实验数据" /></template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'

const columns = [
  { title: '实验名称', dataIndex: 'name', key: 'name' },
  { title: '流量键', dataIndex: 'trafficKey', key: 'trafficKey', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '分组数', key: 'groups', width: 80 },
  { title: '开始时间', dataIndex: 'startDate', key: 'startDate', width: 160 },
  { title: '结束时间', dataIndex: 'endDate', key: 'endDate', width: 160 },
  { title: '操作', key: 'action', width: 200 },
]
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
