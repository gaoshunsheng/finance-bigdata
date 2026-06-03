<template>
  <div class="action-editor">
    <div class="action-header">
      <span class="action-label">动作配置</span>
      <a-button size="small" @click="addAction">
        <template #icon><PlusOutlined /></template> 添加动作
      </a-button>
    </div>

    <div v-for="(action, index) in actions" :key="index" class="action-row">
      <a-select v-model:value="action.type" style="width: 120px" @change="onActionTypeChange(action)">
        <a-select-option value="OUTPUT">输出</a-select-option>
        <a-select-option value="REJECT">拒绝</a-select-option>
        <a-select-option value="APPROVE">通过</a-select-option>
        <a-select-option value="MANUAL">人工审核</a-select-option>
        <a-select-option value="ASSIGN">赋值</a-select-option>
      </a-select>

      <a-input
        v-if="action.type !== 'ASSIGN'"
        v-model:value="action.reason"
        style="flex: 1"
        placeholder="描述/原因"
      />

      <template v-if="action.type === 'ASSIGN' && action.properties">
        <a-input
          v-model:value="action.properties.variable"
          style="width: 150px"
          placeholder="变量名"
        />
        <a-input
          v-model:value="action.properties.expression"
          style="width: 200px"
          placeholder="表达式/值"
        />
      </template>

      <a-button size="small" type="text" danger @click="removeAction(index)">
        <DeleteOutlined />
      </a-button>
    </div>

    <div v-if="!actions.length" class="empty-hint">点击上方按钮添加动作</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'

interface ActionItem {
  type: string
  result?: string
  reason?: string
  properties?: Record<string, any>
}

const props = defineProps<{ modelValue: ActionItem[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', val: ActionItem[]): void }>()

const actions = ref<ActionItem[]>(props.modelValue || [])

watch(() => props.modelValue, (v) => { if (v) actions.value = v }, { deep: true })
watch(actions, (v) => emit('update:modelValue', v), { deep: true })

function addAction() {
  actions.value.push({ type: 'OUTPUT', reason: '' })
}

function removeAction(index: number) {
  actions.value.splice(index, 1)
}

function onActionTypeChange(action: ActionItem) {
  if (action.type === 'ASSIGN') {
    action.properties = { variable: '', expression: '' }
  } else {
    delete action.properties
  }
}
</script>

<style scoped>
.action-editor {
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 12px;
  margin-top: 16px;
}
.action-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.action-label { font-weight: 600; font-size: 14px; }
.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.empty-hint {
  color: rgba(0, 0, 0, 0.25);
  font-size: 12px;
  padding: 8px;
}
</style>
