<template>
  <a-select
    :value="modelValue"
    @update:value="$emit('update:modelValue', $event)"
    :placeholder="placeholder"
    :style="{ width }"
    :size="size"
    :show-search="true"
    :filter-option="filterOption"
    allow-clear
  >
    <a-select-opt-group v-for="group in groupedOptions" :key="group.layer" :label="group.label">
      <a-select-option v-for="opt in group.options" :key="opt.value" :value="opt.value">
        <span>{{ opt.label }}</span>
        <a-tag size="small" style="margin-left:8px" :color="dataTypeColor(opt.dataType)">
          {{ opt.dataType }}
        </a-tag>
      </a-select-option>
    </a-select-opt-group>
  </a-select>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useVariableOptions } from '@/composables/useVariableOptions'

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  width?: string
  size?: 'small' | 'middle' | 'large'
}>(), {
  placeholder: '选择变量',
  width: '200px',
  size: 'middle',
})

defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { groupedOptions, load } = useVariableOptions()

onMounted(() => { load() })

function filterOption(input: string, option: any) {
  const label = option.label?.toString().toLowerCase() || ''
  const value = option.value?.toString().toLowerCase() || ''
  const q = input.toLowerCase()
  return label.includes(q) || value.includes(q)
}

function dataTypeColor(dt: string): string {
  switch (dt.toUpperCase()) {
    case 'INTEGER':
    case 'DECIMAL':
    case 'NUMBER':
    case 'LONG':
    case 'DOUBLE':
      return 'blue'
    case 'BOOLEAN':
      return 'orange'
    case 'STRING':
      return 'green'
    default:
      return 'default'
  }
}
</script>
