<template>
  <div class="table-editor">
    <!-- 命中策略 -->
    <div class="table-toolbar">
      <a-space>
        <span>命中策略:</span>
        <a-radio-group v-model:value="model.hitPolicy" button-style="solid" size="small">
          <a-radio-button value="FIRST_MATCH">首次匹配</a-radio-button>
          <a-radio-button value="ALL_MATCH">全部匹配</a-radio-button>
          <a-radio-button value="PRIORITY">优先级</a-radio-button>
        </a-radio-group>
      </a-space>
      <a-space>
        <a-button size="small" @click="addColumn('INPUT')">
          <template #icon><PlusOutlined /></template> 输入列
        </a-button>
        <a-button size="small" @click="addColumn('OUTPUT')">
          <template #icon><PlusOutlined /></template> 输出列
        </a-button>
        <a-button size="small" @click="addRow">
          <template #icon><PlusOutlined /></template> 添加行
        </a-button>
        <a-button size="small" @click="addRows(5)">批量添加5行</a-button>
      </a-space>
    </div>

    <!-- 决策表 -->
    <div class="table-container">
      <table class="decision-table" v-if="model.columns.length">
        <thead>
          <tr>
            <th class="row-num-col">#</th>
            <th
              v-for="(col, cIdx) in model.columns"
              :key="cIdx"
              :class="['table-th', col.type === 'INPUT' ? 'input-col' : 'output-col']"
            >
              <div class="th-content">
                <a-input
                  v-model:value="col.label"
                  size="small"
                  class="th-input"
                  :placeholder="col.type === 'INPUT' ? '输入列名' : '输出列名'"
                />
                <a-tag :color="col.type === 'INPUT' ? 'blue' : 'green'" size="small" class="th-tag">
                  {{ col.type === 'INPUT' ? '入' : '出' }}
                </a-tag>
                <a-button size="small" type="text" danger @click="removeColumn(cIdx)">
                  <DeleteOutlined />
                </a-button>
              </div>
              <div class="th-field">
                <a-input
                  v-model:value="col.field"
                  size="small"
                  placeholder="字段名"
                  style="font-size: 11px"
                />
              </div>
            </th>
            <th class="action-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rIdx) in model.rows" :key="row.id">
            <td class="row-num-col">
              <span class="row-num">{{ rIdx + 1 }}</span>
              <a-input-number
                v-if="model.hitPolicy === 'PRIORITY'"
                v-model:value="row.priority"
                :min="1"
                size="small"
                style="width: 50px"
              />
            </td>
            <td
              v-for="(col, cIdx) in model.columns"
              :key="cIdx"
              :class="col.type === 'INPUT' ? 'input-cell' : 'output-cell'"
            >
              <a-input
                v-model:value="row.cells[col.field]"
                size="small"
                :placeholder="col.type === 'INPUT' ? '条件值 (* 通配)' : '输出值'"
              />
            </td>
            <td class="action-col">
              <a-button size="small" type="text" danger @click="removeRow(rIdx)">
                <DeleteOutlined />
              </a-button>
            </td>
          </tr>
          <tr v-if="!model.rows.length">
            <td :colspan="model.columns.length + 2" class="empty-cell">
              点击 "添加行" 创建决策规则
            </td>
          </tr>
        </tbody>
      </table>

      <a-empty v-else description="请先添加输入列和输出列" />
    </div>

    <!-- JSON 预览 -->
    <a-collapse style="margin-top: 12px">
      <a-collapse-panel header="JSON 预览">
        <pre class="json-preview">{{ JSON.stringify(model, null, 2) }}</pre>
      </a-collapse-panel>
    </a-collapse>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { TableModel, TableColumn, TableRow } from '@/types/flow'

const props = defineProps<{ modelValue: TableModel }>()
const emit = defineEmits<{ (e: 'update:modelValue', val: TableModel): void }>()

const model = reactive<TableModel>(
  props.modelValue || { columns: [], rows: [], hitPolicy: 'FIRST_MATCH' },
)

watch(() => props.modelValue, (v) => Object.assign(model, v), { deep: true })
watch(model, (v) => emit('update:modelValue', v), { deep: true })

let rowId = 0
function nextRowId(): string {
  return `row_${++rowId}`
}

function addColumn(type: 'INPUT' | 'OUTPUT') {
  const idx = model.columns.filter(c => c.type === type).length + 1
  const prefix = type === 'INPUT' ? 'input' : 'output'
  model.columns.push({
    field: `${prefix}_${idx}`,
    label: `${type === 'INPUT' ? '输入' : '输出'}${idx}`,
    type,
  })
}

function removeColumn(idx: number) {
  const field = model.columns[idx].field
  model.columns.splice(idx, 1)
  // 清理所有行中该字段的值
  model.rows.forEach(r => delete r.cells[field])
}

function addRow() {
  const cells: Record<string, any> = {}
  model.columns.forEach(c => { cells[c.field] = '' })
  model.rows.push({ id: nextRowId(), cells, priority: model.rows.length + 1 })
}

function addRows(n: number) {
  for (let i = 0; i < n; i++) addRow()
}

function removeRow(idx: number) {
  model.rows.splice(idx, 1)
}
</script>

<style scoped>
.table-editor {
  max-width: 100%;
  overflow-x: auto;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.table-container {
  overflow-x: auto;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.decision-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.decision-table th,
.decision-table td {
  border: 1px solid #f0f0f0;
  padding: 6px 8px;
  min-width: 100px;
}

.decision-table th {
  background: #fafafa;
  font-weight: 600;
}

.th-content {
  display: flex;
  align-items: center;
  gap: 4px;
}

.th-input {
  flex: 1;
  font-weight: 600;
}

.th-tag {
  flex-shrink: 0;
}

.th-field {
  margin-top: 4px;
}

.input-col { border-bottom: 2px solid #1677ff !important; }
.output-col { border-bottom: 2px solid #52c41a !important; }
.input-cell { background: rgba(22, 119, 255, 0.02); }
.output-cell { background: rgba(82, 196, 26, 0.02); }

.row-num-col {
  width: 50px;
  text-align: center;
  background: #fafafa;
  min-width: 50px;
}

.action-col {
  width: 50px;
  text-align: center;
  min-width: 50px;
}

.row-num {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.empty-cell {
  text-align: center;
  color: rgba(0, 0, 0, 0.25);
  padding: 24px;
}

.json-preview {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
  margin: 0;
}
</style>
