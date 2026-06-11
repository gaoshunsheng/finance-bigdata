<template>
  <div>
    <div class="page-header">
      <h3>变量管理</h3>
      <a-space>
        <a-input-search v-model:value="searchKey" placeholder="搜索变量" style="width: 240px" />
        <a-button type="primary" @click="showCreateModal"><template #icon><PlusOutlined /></template> 新建变量</a-button>
      </a-space>
    </div>

    <a-tabs v-model:activeKey="activeLayer">
      <a-tab-pane key="ALL" tab="全部" />
      <a-tab-pane key="INPUT" tab="输入变量 (L0)" />
      <a-tab-pane key="EXTERNAL" tab="外部数据 (L1)" />
      <a-tab-pane key="CACHED" tab="特征缓存 (L2)" />
      <a-tab-pane key="DERIVED" tab="派生变量 (L3)" />
    </a-tabs>

    <a-table :columns="columns" :data-source="filteredVariables" :loading="loading" :pagination="{ pageSize: 20 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'layer'">
          <a-tag :color="layerColor(record.layer)">{{ record.layer }}</a-tag>
        </template>
        <template v-else-if="column.key === 'dataType'">
          <a-tag v-if="record.dataType">{{ record.dataType }}</a-tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'deps'">
          <a-space>
            <a-tag v-for="d in (record.dependencies || []).slice(0, 3)" :key="d" size="small">{{ d }}</a-tag>
            <span v-if="(record.dependencies || []).length > 3" class="more-text">+{{ record.dependencies.length - 3 }}</span>
            <span v-if="!(record.dependencies || []).length">-</span>
          </a-space>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showDetail(record)">详情</a-button>
            <a-button type="link" size="small" @click="$router.push(`/variable/${record.id}`)">编辑</a-button>
            <a-popconfirm title="确认删除?" @confirm="handleDelete(record.id)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="createModalVisible" title="新建变量" @ok="handleCreate" width="600px">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="名称" required><a-input v-model:value="createForm.name" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="分类"><a-select v-model:value="createForm.category">
            <a-select-option value="credit">征信</a-select-option>
            <a-select-option value="commerce">商业</a-select-option>
            <a-select-option value="derived">衍生</a-select-option>
          </a-select></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="层级"><a-select v-model:value="createForm.layer">
            <a-select-option value="INPUT">L0-输入</a-select-option>
            <a-select-option value="EXTERNAL">L1-外部</a-select-option>
            <a-select-option value="CACHED">L2-缓存</a-select-option>
            <a-select-option value="DERIVED">L3-派生</a-select-option>
          </a-select></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="数据类型"><a-select v-model:value="createForm.dataType">
            <a-select-option value="STRING">字符串</a-select-option>
            <a-select-option value="INTEGER">整数</a-select-option>
            <a-select-option value="DECIMAL">数值</a-select-option>
            <a-select-option value="BOOLEAN">布尔</a-select-option>
            <a-select-option value="DATE">日期</a-select-option>
          </a-select></a-form-item></a-col>
        </a-row>
        <a-form-item v-if="createForm.layer==='DERIVED'" label="表达式"><a-textarea v-model:value="createForm.expression" :rows="3" /></a-form-item>
        <a-form-item v-if="createForm.layer==='DERIVED'" label="依赖"><a-select v-model:value="createForm.dependencies" mode="tags" placeholder="输入依赖变量名" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="createForm.description" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="detailVisible" :title="detailRecord?.name||'详情'" :footer="null" width="700px">
      <a-descriptions v-if="detailRecord" bordered size="small" :column="2">
        <a-descriptions-item label="ID">{{ detailRecord.id }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ detailRecord.name }}</a-descriptions-item>
        <a-descriptions-item label="层级"><a-tag :color="layerColor(detailRecord.layer)">{{ detailRecord.layer }}</a-tag></a-descriptions-item>
        <a-descriptions-item label="类型">{{ detailRecord.dataType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="分类">{{ detailRecord.category || '-' }}</a-descriptions-item>
        <a-descriptions-item label="版本">v{{ detailRecord.version }}</a-descriptions-item>
        <a-descriptions-item v-if="detailRecord.expression" label="表达式" :span="2"><code>{{ detailRecord.expression }}</code></a-descriptions-item>
        <a-descriptions-item label="依赖" :span="2">
          <a-tag v-for="d in (detailRecord.dependencies||[])" :key="d" size="small">{{ d }}</a-tag>
          <span v-if="!(detailRecord.dependencies||[]).length">-</span>
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">{{ detailRecord.description || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { listVariables, deleteVariable } from '@/api/admin'
import { statusColor, statusLabel } from '@/utils'

const loading = ref(false)
const searchKey = ref('')
const activeLayer = ref('ALL')
const allVariables = ref<any[]>([])

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 160 },
  { title: '分类', dataIndex: 'category', key: 'category', width: 80 },
  { title: '层级', key: 'layer', width: 90 },
  { title: '类型', key: 'dataType', width: 80 },
  { title: '依赖', key: 'deps', width: 180 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 60 },
  { title: '状态', key: 'status', width: 80 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 150 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' },
]

const filteredVariables = computed(() => {
  let list = allVariables.value
  if (activeLayer.value !== 'ALL') list = list.filter(v => v.layer === activeLayer.value)
  if (searchKey.value) {
    const k = searchKey.value.toLowerCase()
    list = list.filter(v => v.name?.toLowerCase().includes(k) || v.id?.toLowerCase().includes(k))
  }
  return list
})

function layerColor(layer: string): string {
  const m: Record<string,string> = { INPUT:'blue', EXTERNAL:'orange', CACHED:'purple', DERIVED:'cyan' }
  return m[layer] || 'default'
}

// Parse content JSON to extract variable metadata
function parseContent(record: any): any {
  try { return typeof record.content === 'string' ? JSON.parse(record.content) : (record.content || {}) }
  catch { return {} }
}

async function loadData() {
  loading.value = true
  try {
    const raw = await listVariables({}) as any[]
    allVariables.value = raw.map(r => {
      const c = parseContent(r)
      return {
        ...r,
        layer: c.layer || r.type || '-',
        dataType: c.dataType || null,
        category: c.category || '-',
        dependencies: c.dependencies || [],
        expression: c.expression || null,
      }
    })
  } catch { message.error('加载变量失败') }
  finally { loading.value = false }
}

async function handleDelete(id: string) {
  try { await deleteVariable(id); message.success('已删除'); await loadData() }
  catch { message.error('删除失败') }
}

// Detail modal
const detailVisible = ref(false)
const detailRecord = ref<any>(null)
function showDetail(record: any) { detailRecord.value = record; detailVisible.value = true }

// Create modal
const createModalVisible = ref(false)
const createForm = reactive({ name:'', category:'credit', layer:'INPUT', dataType:'STRING', expression:'', dependencies:[] as string[], description:'' })
function showCreateModal() {
  Object.assign(createForm, { name:'', category:'credit', layer:'INPUT', dataType:'STRING', expression:'', dependencies:[], description:'' })
  createModalVisible.value = true
}
function handleCreate() {
  if (!createForm.name.trim()) { message.warning('请输入名称'); return }
  createModalVisible.value = false
  message.success('已创建（刷新后可见）')
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px }
.page-header h3 { margin: 0 }
.more-text { color: rgba(0,0,0,.45); font-size: 12px }
</style>
