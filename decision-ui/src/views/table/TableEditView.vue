<template>
  <div>
    <a-page-header :title="isEdit ? '编辑决策表' : '新建决策表'" @back="$router.back()" />
    <a-spin :spinning="loading">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="描述"><a-input v-model:value="form.description" /></a-form-item></a-col>
        </a-row>
      </a-form>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="visual" tab="可视化编辑">
          <TableEditor v-model="tableModel" :grouped-fields="tableGroupedOptions" />
        </a-tab-pane>
        <a-tab-pane key="json" tab="JSON 编辑">
          <a-textarea v-model:value="jsonContent" :rows="18" style="font-family:monospace;font-size:13px" />
        </a-tab-pane>
      </a-tabs>

      <div style="margin-top:16px;padding-top:16px;border-top:1px solid #f0f0f0">
        <a-space>
          <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import TableEditor from '@/components/table/TableEditor.vue'
import type { TableModel } from '@/types/flow'
import { getTable, createTable, updateTable } from '@/api/admin'
import { useVariableOptions } from '@/composables/useVariableOptions'

const { groupedOptions, load: loadVariables } = useVariableOptions()

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('visual')
const loading = ref(false)
const saving = ref(false)

const tableGroupedOptions = computed(() =>
  groupedOptions.value.map(g => ({
    layer: g.layer,
    label: g.label,
    options: g.options.map(o => ({ value: o.value, label: o.label, dataType: o.dataType })),
  }))
)

const form = reactive({ name: '', description: '' })
const tableModel = reactive<TableModel>({ columns: [], rows: [], hitPolicy: 'FIRST_MATCH' })
const jsonContent = ref('{}')

// Sync UI → engine JSON
watch(tableModel, (v) => {
  const engine: any = {
    tableId: route.params.id as string || '',
    name: form.name,
    version: 1,
    columns: v.columns.map(c => ({ name: c.label || c.field, field: c.field })),
    rows: v.rows.map(r => ({
      conditions: v.columns.map(col => r.cells?.[col.field] ?? '*'),
      result: r.output || {},
    })),
    hitPolicy: v.hitPolicy,
  }
  jsonContent.value = JSON.stringify(engine, null, 2)
}, { deep: true })

// Load engine → UI
function loadEngineToUI(content: any) {
  if (content.columns) {
    tableModel.columns = content.columns.map((c: any) => ({
      field: c.field || c.name,
      label: c.name || c.field,
      type: 'INPUT' as const,
      dataType: 'STRING',
    }))
  }
  if (content.rows) {
    tableModel.rows = content.rows.map((r: any, i: number) => {
      const cells: Record<string, any> = {}
      ;(content.columns || []).forEach((c: any, j: number) => {
        cells[c.field || c.name] = r.conditions?.[j] ?? '*'
      })
      return { id: `row_${i}`, cells, output: r.result || {} }
    })
  }
  if (content.hitPolicy) tableModel.hitPolicy = content.hitPolicy
}

onMounted(async () => {
  loadVariables()
  if (isEdit.value) {
    loading.value = true
    try {
      const table = await getTable(route.params.id as string) as any
      form.name = table.name || ''
      form.description = table.description || ''
      const content = typeof table.content === 'string' ? JSON.parse(table.content) : (table.content || {})
      loadEngineToUI(content)
    } catch { message.error('加载失败') }
    finally { loading.value = false }
  }
})

async function handleSave() {
  if (!form.name.trim()) { message.warning('请输入名称'); return }
  saving.value = true
  try {
    const content = JSON.parse(jsonContent.value)
    const payload = { name: form.name, description: form.description, content: JSON.stringify(content) }
    if (isEdit.value) { await updateTable(route.params.id as string, payload); message.success('已更新') }
    else { await createTable(payload); message.success('已创建') }
    router.push('/table')
  } catch (err: any) { message.error(err?.message || '保存失败') }
  finally { saving.value = false }
}
</script>
