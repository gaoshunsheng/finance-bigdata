import { ref, computed } from 'vue'
import { listVariables } from '@/api/admin'

export interface VariableOption {
  value: string      // variable id, e.g. "var_age"
  label: string      // display name, e.g. "年龄"
  dataType: string   // NUMBER / STRING / BOOLEAN / INTEGER / DECIMAL
  layer: string      // INPUT / EXTERNAL / CACHED / DERIVED
  category: string
}

export interface VariableGroup {
  layer: string
  label: string
  options: VariableOption[]
}

const LAYER_LABELS: Record<string, string> = {
  INPUT: 'L0 输入变量',
  EXTERNAL: 'L1 外部变量',
  CACHED: 'L2 缓存变量',
  DERIVED: 'L3 派生变量',
}

const LAYER_ORDER = ['INPUT', 'EXTERNAL', 'CACHED', 'DERIVED']

// Identifier fields that should not be used as condition/characteristic fields
const EXCLUDED_IDS = new Set(['var_customer_id', 'var_application_id'])

// Shared cache across components
let cachedVariables: VariableOption[] | null = null
let fetchPromise: Promise<VariableOption[]> | null = null

async function fetchVariables(): Promise<VariableOption[]> {
  if (cachedVariables) return cachedVariables
  if (fetchPromise) return fetchPromise

  fetchPromise = (async () => {
    try {
      const data = await listVariables()
      const list = Array.isArray(data) ? data : []
      cachedVariables = list
        .filter((v: any) => !EXCLUDED_IDS.has(v.id))
        .map((v: any) => {
          const content = typeof v.content === 'string' ? JSON.parse(v.content) : (v.content || {})
          return {
            value: v.id,
            label: v.name || v.id,
            dataType: content.dataType || 'STRING',
            layer: content.layer || 'INPUT',
            category: content.category || '',
          }
        })
      return cachedVariables!
    } catch {
      return []
    }
  })()

  const result = await fetchPromise
  fetchPromise = null
  return result
}

export function useVariableOptions() {
  const variables = ref<VariableOption[]>([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      variables.value = await fetchVariables()
    } finally {
      loading.value = false
    }
  }

  /** Force refresh (bypass cache) */
  async function refresh() {
    cachedVariables = null
    await load()
  }

  /** Variables grouped by layer for a-select with groupLabels */
  const groupedOptions = computed<VariableGroup[]>(() => {
    const groups: Map<string, VariableOption[]> = new Map()
    for (const layer of LAYER_ORDER) {
      groups.set(layer, [])
    }
    for (const v of variables.value) {
      const layer = LAYER_ORDER.includes(v.layer) ? v.layer : 'INPUT'
      groups.get(layer)!.push(v)
    }
    return LAYER_ORDER
      .map(layer => ({
        layer,
        label: LAYER_LABELS[layer] || layer,
        options: groups.get(layer) || [],
      }))
      .filter(g => g.options.length > 0)
  })

  /** Flat list for simple selects */
  const flatOptions = computed(() =>
    variables.value.map(v => ({ value: v.value, label: `${v.label} (${v.dataType})` })),
  )

  /** Lookup variable info by id */
  function getVariable(id: string): VariableOption | undefined {
    return variables.value.find(v => v.value === id)
  }

  /** Map variable dataType to ConditionField type */
  function getFieldType(id: string): 'NUMBER' | 'STRING' | 'BOOLEAN' {
    const v = getVariable(id)
    if (!v) return 'STRING'
    const dt = v.dataType.toUpperCase()
    if (dt === 'BOOLEAN') return 'BOOLEAN'
    if (dt === 'INTEGER' || dt === 'DECIMAL' || dt === 'NUMBER' || dt === 'LONG' || dt === 'DOUBLE') return 'NUMBER'
    return 'STRING'
  }

  return {
    variables,
    loading,
    groupedOptions,
    flatOptions,
    load,
    refresh,
    getVariable,
    getFieldType,
  }
}
