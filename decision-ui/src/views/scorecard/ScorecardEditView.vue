<template>
  <div>
    <a-page-header :title="isEdit ? '编辑评分卡' : '新建评分卡'" @back="$router.back()" />
    <a-spin :spinning="loading">
      <a-form layout="vertical" style="max-width: 1000px">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="描述"><a-input v-model:value="form.description" /></a-form-item></a-col>
        </a-row>
      </a-form>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="visual" tab="可视化编辑">
          <ScorecardEditor v-model="scorecardModel" :grouped-fields="scorecardGroupedOptions" />
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
import ScorecardEditor from '@/components/scorecard/ScorecardEditor.vue'
import type { ScorecardModel } from '@/types/scorecard'
import { getScorecard, createScorecard, updateScorecard } from '@/api/admin'
import { useVariableOptions } from '@/composables/useVariableOptions'

const { groupedOptions, load: loadVariables } = useVariableOptions()

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('visual')
const loading = ref(false)
const saving = ref(false)

const scorecardGroupedOptions = computed(() =>
  groupedOptions.value.map(g => ({
    layer: g.layer,
    label: g.label,
    options: g.options.map(o => ({ value: o.value, label: o.label, dataType: o.dataType })),
  }))
)

const form = reactive({ name: '', description: '' })
const scorecardModel = reactive<ScorecardModel>({ initialScore: 500, characteristics: [], cutoff: 500 })
const jsonContent = ref('{}')

// Sync UI model → JSON
watch(scorecardModel, (v) => {
  // Convert back to engine format: from/to → range, cutoff number → cutoff object
  const engine = {
    scorecardId: route.params.id as string || '',
    name: form.name,
    version: 1,
    initialScore: v.initialScore,
    characteristics: v.characteristics.map(c => ({
      name: c.name,
      field: c.field,
      bins: c.bins.map(b => ({
        range: [b.from ?? null, b.to ?? null],
        score: b.score,
        label: b.label || `[${b.from ?? 'min'}, ${b.to ?? 'max'})`,
      })),
    })),
    cutoff: { reject: v.cutoff ?? 500, review: (v.cutoff ?? 500) + 50, pass: (v.cutoff ?? 500) + 50 },
  }
  jsonContent.value = JSON.stringify(engine, null, 2)
}, { deep: true })

// Load engine format → UI model
function loadEngineToUI(content: any) {
  if (content.initialScore != null) scorecardModel.initialScore = content.initialScore
  if (content.cutoff) {
    scorecardModel.cutoff = typeof content.cutoff === 'object' ? (content.cutoff.reject ?? 500) : content.cutoff
  }
  if (content.characteristics) {
    scorecardModel.characteristics = content.characteristics.map((c: any) => ({
      name: c.name,
      field: c.field,
      bins: (c.bins || []).map((b: any) => ({
        from: b.range?.[0] ?? undefined,
        to: b.range?.[1] ?? undefined,
        score: b.score,
        label: b.label || '',
      })),
    }))
  }
}

onMounted(async () => {
  loadVariables()
  if (isEdit.value) {
    loading.value = true
    try {
      const card = await getScorecard(route.params.id as string) as any
      form.name = card.name || ''
      form.description = card.description || ''
      const content = typeof card.content === 'string' ? JSON.parse(card.content) : (card.content || {})
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
    if (isEdit.value) { await updateScorecard(route.params.id as string, payload); message.success('已更新') }
    else { await createScorecard(payload); message.success('已创建') }
    router.push('/scorecard')
  } catch (err: any) { message.error(err?.message || '保存失败') }
  finally { saving.value = false }
}
</script>
