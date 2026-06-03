<template>
  <div>
    <div class="page-header">
      <h3>决策流管理</h3>
      <a-button type="primary" @click="$router.push('/flow/create')">
        <template #icon><PlusOutlined /></template> 新建决策流
      </a-button>
    </div>
    <a-table :columns="columns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="id">
      <template #emptyText><a-empty description="暂无决策流数据" /></template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '节点数', key: 'nodes', width: 80 },
  { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'action', width: 240 },
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
