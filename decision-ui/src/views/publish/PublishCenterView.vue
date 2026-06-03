<template>
  <div>
    <a-page-header title="发布中心" />
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="pending" tab="待审批">
        <a-table :columns="pendingColumns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="id">
          <template #emptyText><a-empty description="暂无待审批记录" /></template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="grayscale" tab="灰度发布">
        <a-table :columns="grayscaleColumns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="id">
          <template #emptyText><a-empty description="暂无灰度发布记录" /></template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="history" tab="发布历史">
        <a-table :columns="historyColumns" :data-source="[]" :pagination="{ pageSize: 20 }" row-key="id">
          <template #emptyText><a-empty description="暂无发布历史" /></template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeTab = ref('pending')

const pendingColumns = [
  { title: '目标名称', dataIndex: 'targetName', key: 'targetName' },
  { title: '类型', dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '提交人', dataIndex: 'operator', key: 'operator', width: 100 },
  { title: '提交时间', dataIndex: 'operatedAt', key: 'operatedAt', width: 180 },
  { title: '操作', key: 'action', width: 160 },
]

const grayscaleColumns = [
  { title: '目标名称', dataIndex: 'targetName', key: 'targetName' },
  { title: '类型', dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: '灰度比例', dataIndex: 'percentage', key: 'percentage', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 240 },
]

const historyColumns = [
  { title: '目标名称', dataIndex: 'targetName', key: 'targetName' },
  { title: '类型', dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: '版本', dataIndex: 'toVersion', key: 'toVersion', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 100 },
  { title: '操作时间', dataIndex: 'operatedAt', key: 'operatedAt', width: 180 },
]
</script>
