<template>
  <div :class="['condition-node', `depth-${depth}`, `type-${node.type.toLowerCase()}`]">
    <!-- 逻辑组节点 (AND / OR / NOT) -->
    <template v-if="node.type !== 'CONDITION'">
      <div class="logic-header">
        <a-tag :color="logicColor" class="logic-tag">
          {{ node.type }}
        </a-tag>
        <a-space size="small">
          <a-button size="small" type="link" @click="addChild('CONDITION')">
            <PlusOutlined /> 条件
          </a-button>
          <a-button size="small" type="link" @click="addChild(depth < 3 ? 'AND' : undefined)">
            <FolderOutlined /> 组
          </a-button>
          <a-button
            v-if="depth > 0"
            size="small"
            type="link"
            danger
            @click="$emit('remove', node.id)"
          >
            <DeleteOutlined />
          </a-button>
        </a-space>
      </div>
      <div class="logic-children">
        <ConditionNode
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          :depth="depth + 1"
          :fields="fields"
          @update:node="onChildUpdate($event, child.id)"
          @remove="onChildRemove"
        />
        <div v-if="!node.children?.length" class="empty-hint">
          点击上方按钮添加条件或逻辑组
        </div>
      </div>
    </template>

    <!-- 条件叶子节点 -->
    <template v-else>
      <div class="condition-row">
        <a-select
          v-model:value="node.condition!.field"
          style="width: 160px"
          placeholder="选择字段"
          @change="onFieldChange"
        >
          <a-select-option v-for="f in fields" :key="f.name" :value="f.name">
            {{ f.label }}
          </a-select-option>
        </a-select>

        <a-select
          v-model:value="node.condition!.operator"
          style="width: 120px"
          placeholder="运算符"
        >
          <a-select-option v-for="op in currentOperators" :key="op" :value="op">
            {{ operatorLabel(op) }}
          </a-select-option>
        </a-select>

        <template v-if="!['IS_NULL', 'IS_NOT_NULL'].includes(node.condition!.operator)">
          <a-input-number
            v-if="currentFieldType === 'NUMBER'"
            v-model:value="node.condition!.value"
            style="width: 140px"
            placeholder="数值"
          />
          <a-select
            v-else-if="node.condition!.operator === 'BETWEEN'"
            v-model:value="node.condition!.value"
            mode="tags"
            style="width: 200px"
            placeholder="输入范围值 (回车确认)"
            :max-count="2"
          />
          <a-select
            v-else-if="['IN', 'NOT_IN'].includes(node.condition!.operator)"
            v-model:value="node.condition!.value"
            mode="tags"
            style="width: 200px"
            placeholder="输入值 (回车确认)"
          />
          <a-switch
            v-else-if="currentFieldType === 'BOOLEAN'"
            v-model:checked="node.condition!.value"
          />
          <a-input
            v-else
            v-model:value="node.condition!.value"
            style="width: 200px"
            placeholder="输入值"
          />
        </template>

        <a-button
          size="small"
          type="text"
          danger
          @click="$emit('remove', node.id)"
        >
          <DeleteOutlined />
        </a-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  PlusOutlined,
  FolderOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import type { ConditionField, ConditionTreeNode } from './ConditionBuilder.vue'

const props = defineProps<{
  node: ConditionTreeNode
  depth: number
  fields: ConditionField[]
}>()

const emit = defineEmits<{
  (e: 'update:node', value: ConditionTreeNode): void
  (e: 'remove', id: string): void
}>()

const logicColor = computed(() => {
  switch (props.node.type) {
    case 'AND': return 'blue'
    case 'OR': return 'orange'
    case 'NOT': return 'red'
    default: return 'default'
  }
})

const currentField = computed(() =>
  props.fields.find(f => f.name === props.node.condition?.field),
)

const currentFieldType = computed(() => currentField.value?.type || 'STRING')

const currentOperators = computed(() => {
  if (currentField.value?.operators) return currentField.value.operators
  const type = currentFieldType.value
  const base = ['EQ', 'NEQ', 'IS_NULL', 'IS_NOT_NULL']
  if (type === 'NUMBER') return [...base, 'GT', 'LT', 'GTE', 'LTE', 'BETWEEN', 'IN', 'NOT_IN']
  if (type === 'STRING') return [...base, 'CONTAINS', 'STARTS_WITH', 'IN', 'NOT_IN']
  if (type === 'DATE') return [...base, 'GT', 'LT', 'GTE', 'LTE', 'BETWEEN']
  return base
})

function operatorLabel(op: string): string {
  const map: Record<string, string> = {
    EQ: '等于', NEQ: '不等于', GT: '大于', LT: '小于',
    GTE: '大于等于', LTE: '小于等于', BETWEEN: '区间',
    IN: '属于', NOT_IN: '不属于', CONTAINS: '包含',
    STARTS_WITH: '开头是', IS_NULL: '为空', IS_NOT_NULL: '不为空',
  }
  return map[op] || op
}

function onFieldChange(fieldName: string) {
  if (props.node.condition) {
    const field = props.fields.find(f => f.name === fieldName)
    props.node.condition.valueType = field?.type || 'STRING'
    props.node.condition.value = field?.type === 'NUMBER' ? 0 : ''
    props.node.condition.operator = 'EQ'
  }
}

function addChild(type?: string) {
  if (!props.node.children) props.node.children = []
  if (type === 'CONDITION') {
    props.node.children.push({
      id: `node_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      type: 'CONDITION',
      condition: { field: props.fields[0]?.name || '', operator: 'EQ', value: '', valueType: 'STRING' },
    })
  } else if (type) {
    props.node.children.push({
      id: `node_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      type: type as 'AND' | 'OR',
      children: [],
    })
  }
}

function onChildUpdate(updated: ConditionTreeNode, childId: string) {
  if (!props.node.children) return
  const idx = props.node.children.findIndex(c => c.id === childId)
  if (idx >= 0) {
    props.node.children[idx] = updated
  }
}

function onChildRemove(id: string) {
  if (!props.node.children) return
  props.node.children = props.node.children.filter(c => c.id !== id)
}
</script>

<style scoped>
.condition-node {
  border-left: 3px solid #d9d9d9;
  padding-left: 12px;
  margin: 8px 0;
}

.type-and { border-left-color: #1677ff; }
.type-or { border-left-color: #fa8c16; }
.type-not { border-left-color: #ff4d4f; }

.logic-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.logic-tag {
  font-weight: 600;
  min-width: 44px;
  text-align: center;
}

.logic-children {
  padding-left: 8px;
}

.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  background: #fafafa;
  border-radius: 4px;
  padding: 8px;
}

.empty-hint {
  color: rgba(0, 0, 0, 0.25);
  font-size: 12px;
  padding: 8px;
}

.depth-0 { border-left: none; padding-left: 0; }
</style>
