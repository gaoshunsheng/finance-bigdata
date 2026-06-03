<template>
  <div>
    <a-page-header :title="isEdit ? '编辑决策表' : '新建决策表'" @back="$router.back()" />

    <a-form layout="vertical">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="决策表名称" required>
            <a-input v-model:value="form.name" placeholder="请输入决策表名称" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="描述">
            <a-input v-model:value="form.description" placeholder="请输入描述" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="visual" tab="可视化编辑">
        <a-alert message="输入列支持通配符: * (匹配任意值), 留空表示匹配所有" type="info" show-icon style="margin-bottom: 12px" />
        <TableEditor v-model="tableModel" />
      </a-tab-pane>
      <a-tab-pane key="json" tab="JSON 编辑">
        <a-textarea
          v-model:value="jsonContent"
          :rows="18"
          placeholder="决策表 JSON 定义"
          style="font-family: 'Fira Code', Consolas, monospace; font-size: 13px"
        />
      </a-tab-pane>
    </a-tabs>

    <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #f0f0f0">
      <a-space>
        <a-button type="primary" @click="handleSave">保存</a-button>
        <a-button @click="$router.back()">取消</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import TableEditor from '@/components/table/TableEditor.vue'
import type { TableModel } from '@/types/flow'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('visual')

const form = reactive({ name: '', description: '' })
const tableModel = reactive<TableModel>({
  columns: [],
  rows: [],
  hitPolicy: 'FIRST_MATCH',
})

const jsonContent = ref('{}')
watch(tableModel, (v) => { jsonContent.value = JSON.stringify(v, null, 2) }, { deep: true })

function handleSave() {
  if (!form.name.trim()) {
    message.warning('请输入决策表名称')
    return
  }
  message.success(isEdit.value ? '决策表已更新' : '决策表已创建')
  router.push('/table')
}
</script>
