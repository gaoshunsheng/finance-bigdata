<template>
  <div>
    <a-page-header :title="isEdit ? '设计决策流' : '新建决策流'" @back="$router.back()" />
    <a-spin :spinning="loading">
      <a-form layout="inline" style="margin-bottom: 12px">
        <a-form-item label="名称">
          <a-input v-model:value="form.name" placeholder="决策流名称" style="width: 240px" />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="form.description" placeholder="描述" style="width: 300px" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
            <a-button @click="$router.back()">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="visual" tab="可视化设计">
          <FlowDesigner ref="designerRef" v-model="flowModel" />
        </a-tab-pane>
        <a-tab-pane key="json" tab="JSON 编辑">
          <a-textarea v-model:value="jsonContent" :rows="20" style="font-family:monospace;font-size:13px" />
        </a-tab-pane>
      </a-tabs>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import FlowDesigner from '@/components/flow/FlowDesigner.vue'
import type { FlowModel, FlowNode, FlowEdge } from '@/types/flow'
import { getFlow, createFlow, updateFlow } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('visual')
const loading = ref(false)
const saving = ref(false)

const form = reactive({ name: '', description: '' })
const flowModel = ref<FlowModel>({ nodes: [], edges: [] })
const jsonContent = ref('{}')

// Format conversion
function engineToFlowModel(content: any): FlowModel {
  const nodeList: FlowNode[] = (content.nodes || []).map((n: any, i: number) => ({
    id: n.id,
    type: n.type,
    name: n.config?.name || n.id,
    position: n.position || { x: 80 + (i % 4) * 200, y: 80 + Math.floor(i / 4) * 120 },
    config: n.config || {},
  }))
  const edgeList: FlowEdge[] = (content.edges || []).map((e: any, i: number) => ({
    id: e.id || `edge_${i}`,
    source: e.from || e.source,
    target: e.to || e.target,
    condition: e.condition || undefined,
    label: e.condition || e.label || undefined,
  }))
  return { nodes: nodeList, edges: edgeList }
}

function flowModelToEngine(): any {
  const m = flowModel.value
  return {
    flowId: route.params.id as string || '',
    name: form.name,
    version: 1,
    nodes: m.nodes.map(n => ({ id: n.id, type: n.type, config: n.config })),
    edges: m.edges.map(e => ({ from: e.source, to: e.target, condition: e.condition || null })),
  }
}

// Watch UI model → sync JSON
watch(flowModel, () => {
  jsonContent.value = JSON.stringify(flowModelToEngine(), null, 2)
}, { deep: true })

onMounted(async () => {
  if (isEdit.value) {
    loading.value = true
    try {
      const flow = await getFlow(route.params.id as string) as any
      form.name = flow.name || ''
      form.description = flow.description || ''
      const content = typeof flow.content === 'string' ? JSON.parse(flow.content) : (flow.content || {})
      flowModel.value = engineToFlowModel(content)
      jsonContent.value = JSON.stringify(content, null, 2)
    } catch { message.error('加载失败') }
    finally { loading.value = false }
  }
})

async function handleSave() {
  if (!form.name.trim()) { message.warning('请输入名称'); return }
  saving.value = true
  try {
    const engine = flowModelToEngine()
    const payload = { name: form.name, description: form.description, content: JSON.stringify(engine) }
    if (isEdit.value) { await updateFlow(route.params.id as string, payload); message.success('已更新') }
    else { await createFlow(payload); message.success('已创建') }
    router.push('/flow')
  } catch (err: any) { message.error(err?.message || '保存失败') }
  finally { saving.value = false }
}
</script>
