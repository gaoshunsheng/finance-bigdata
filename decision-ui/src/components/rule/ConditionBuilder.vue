<template>
  <div class="condition-builder">
    <div class="condition-header">
      <span class="condition-label">条件配置</span>
      <a-space>
        <a-button size="small" @click="addCondition">
          <template #icon><PlusOutlined /></template> 添加条件
        </a-button>
        <a-button size="small" @click="addGroup('AND')">
          <template #icon><FolderOutlined /></template> AND 组
        </a-button>
        <a-button size="small" @click="addGroup('OR')">
          <template #icon><FolderOpenOutlined /></template> OR 组
        </a-button>
      </a-space>
    </div>

    <div class="condition-tree">
      <ConditionNode
        :node="rootNode"
        :depth="0"
        :fields="fields"
        :grouped-fields="groupedFields"
        @update:node="onRootUpdate"
        @remove="onRemoveRoot"
      />
    </div>

    <!-- JSON 预览 -->
    <a-collapse style="margin-top: 12px">
      <a-collapse-panel header="JSON 预览">
        <pre class="json-preview">{{ JSON.stringify(rootNode, null, 2) }}</pre>
      </a-collapse-panel>
    </a-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { PlusOutlined, FolderOutlined, FolderOpenOutlined } from '@ant-design/icons-vue'
import ConditionNode from './ConditionNode.vue'

export interface ConditionField {
  name: string
  label: string
  type: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE'
  operators?: string[]
}

export interface FieldGroup {
  layer: string
  label: string
  options: { value: string; label: string; dataType: string }[]
}

export interface ConditionTreeNode {
  id: string
  type: 'CONDITION' | 'AND' | 'OR' | 'NOT'
  condition?: {
    field: string
    operator: string
    value: any
    valueType: string
  }
  children?: ConditionTreeNode[]
}

const props = defineProps<{
  modelValue: ConditionTreeNode
  fields: ConditionField[]
  groupedFields?: FieldGroup[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ConditionTreeNode): void
}>()

let idCounter = 0
function nextId(): string {
  return `node_${++idCounter}_${Date.now()}`
}

const rootNode = ref<ConditionTreeNode>(
  props.modelValue || { id: nextId(), type: 'AND', children: [] },
)

watch(
  () => props.modelValue,
  (val) => {
    if (val) rootNode.value = val
  },
  { deep: true },
)

watch(rootNode, (val) => emit('update:modelValue', val), { deep: true })

function addCondition() {
  if (!rootNode.value.children) rootNode.value.children = []
  rootNode.value.children.push({
    id: nextId(),
    type: 'CONDITION',
    condition: { field: props.fields[0]?.name || '', operator: 'EQ', value: '', valueType: 'STRING' },
  })
}

function addGroup(logic: 'AND' | 'OR') {
  if (!rootNode.value.children) rootNode.value.children = []
  rootNode.value.children.push({
    id: nextId(),
    type: logic,
    children: [],
  })
}

function onRootUpdate(node: ConditionTreeNode) {
  rootNode.value = node
}

function onRemoveRoot() {
  rootNode.value = { id: nextId(), type: 'AND', children: [] }
}
</script>

<style scoped>
.condition-builder {
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 12px;
}

.condition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.condition-label {
  font-weight: 600;
  font-size: 14px;
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
