<template>
  <div>
    <div class="page-header">
      <h3>变量管理</h3>
      <a-button type="primary">
        <template #icon><PlusOutlined /></template> 新建变量
      </a-button>
    </div>
    <a-table :columns="columns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="id">
      <template #emptyText><a-empty description="暂无变量数据" /></template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '分类', dataIndex: 'category', key: 'category', width: 120 },
  { title: '层级', dataIndex: 'layer', key: 'layer', width: 120 },
  { title: '数据类型', dataIndex: 'dataType', key: 'dataType', width: 100 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'action', width: 160 },
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
