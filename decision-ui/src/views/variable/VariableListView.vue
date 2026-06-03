<template>
  <div>
    <div class="page-header">
      <h3>变量管理</h3>
      <a-space>
        <a-input-search
          v-model:value="searchKey"
          placeholder="搜索变量名称"
          style="width: 240px"
        />
        <a-button type="primary" @click="showCreateModal">
          <template #icon><PlusOutlined /></template> 新建变量
        </a-button>
      </a-space>
    </div>

    <!-- 层级标签 -->
    <a-tabs v-model:activeKey="activeLayer" @change="onLayerChange">
      <a-tab-pane key="ALL" tab="全部" />
      <a-tab-pane key="INPUT" tab="输入变量 (Layer 0)" />
      <a-tab-pane key="EXTERNAL_API" tab="外部API (Layer 1)" />
      <a-tab-pane key="FEATURE_STORE" tab="特征存储 (Layer 2)" />
      <a-tab-pane key="COMPUTED" tab="计算变量 (Layer 3)" />
    </a-tabs>

    <!-- 变量表格 -->
    <a-table :columns="columns" :data-source="filteredVariables" :pagination="{ pageSize: 20 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'layer'">
          <a-tag :color="layerColor(record.layer)">{{ layerLabel(record.layer) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'dataType'">
          <a-tag>{{ record.dataType }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'dependencies'">
          <a-space>
            <a-tag v-for="dep in (record.dependencies || []).slice(0, 3)" :key="dep" size="small">{{ dep }}</a-tag>
            <span v-if="(record.dependencies || []).length > 3" class="more-text">
              +{{ record.dependencies.length - 3 }}
            </span>
          </a-space>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showDependencyGraph(record)">依赖图</a-button>
            <a-button type="link" size="small">编辑</a-button>
            <a-popconfirm title="确认删除?" ok-text="删除" cancel-text="取消">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
      <template #emptyText><a-empty description="暂无变量数据" /></template>
    </a-table>

    <!-- 新建变量弹窗 -->
    <a-modal
      v-model:open="createModalVisible"
      title="新建变量"
      @ok="handleCreate"
      width="600px"
    >
      <a-form :model="createForm" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="变量名称" required>
              <a-input v-model:value="createForm.name" placeholder="如: age, income" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="分类" required>
              <a-select v-model:value="createForm.category" placeholder="选择分类">
                <a-select-option value="basic">基础信息</a-select-option>
                <a-select-option value="credit">征信数据</a-select-option>
                <a-select-option value="behavior">行为数据</a-select-option>
                <a-select-option value="external">外部数据</a-select-option>
                <a-select-option value="derived">衍生变量</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="层级" required>
              <a-select v-model:value="createForm.layer">
                <a-select-option value="INPUT">输入变量 (Layer 0)</a-select-option>
                <a-select-option value="EXTERNAL_API">外部API (Layer 1)</a-select-option>
                <a-select-option value="FEATURE_STORE">特征存储 (Layer 2)</a-select-option>
                <a-select-option value="COMPUTED">计算变量 (Layer 3)</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="数据类型" required>
              <a-select v-model:value="createForm.dataType">
                <a-select-option value="STRING">字符串</a-select-option>
                <a-select-option value="NUMBER">数值</a-select-option>
                <a-select-option value="BOOLEAN">布尔</a-select-option>
                <a-select-option value="DATE">日期</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item v-if="createForm.layer === 'COMPUTED'" label="计算表达式">
          <a-textarea v-model:value="createForm.expression" :rows="3" placeholder="如: income * 12 + assets" />
        </a-form-item>
        <a-form-item v-if="createForm.layer === 'COMPUTED'" label="依赖变量">
          <a-select v-model:value="createForm.dependencies" mode="tags" placeholder="输入依赖的变量名后回车" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="createForm.description" :rows="2" placeholder="变量说明" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 依赖图弹窗 -->
    <a-modal
      v-model:open="depGraphVisible"
      title="变量依赖关系"
      :footer="null"
      width="700px"
    >
      <div class="dep-graph-container">
        <div v-if="depGraphData" class="dep-graph">
          <div class="dep-node current">
            <span class="dep-dot" style="background: #1677ff"></span>
            <span>{{ depGraphData.name }}</span>
            <a-tag>{{ depGraphData.layer }}</a-tag>
          </div>
          <div v-if="depGraphData.dependencies?.length" class="dep-section">
            <h4>依赖 ({{ depGraphData.dependencies.length }})</h4>
            <div v-for="dep in depGraphData.dependencies" :key="dep" class="dep-node">
              <span class="dep-dot" style="background: #fa8c16"></span>
              <span>{{ dep }}</span>
            </div>
          </div>
          <div v-if="depGraphData.usedBy?.length" class="dep-section">
            <h4>被依赖 ({{ depGraphData.usedBy.length }})</h4>
            <div v-for="used in depGraphData.usedBy" :key="used" class="dep-node">
              <span class="dep-dot" style="background: #52c41a"></span>
              <span>{{ used }}</span>
            </div>
          </div>
          <a-empty v-if="!depGraphData.dependencies?.length && !depGraphData.usedBy?.length" description="无依赖关系" />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { statusColor, statusLabel } from '@/utils'

const searchKey = ref('')
const activeLayer = ref('ALL')

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 160 },
  { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
  { title: '层级', dataIndex: 'layer', key: 'layer', width: 120 },
  { title: '数据类型', dataIndex: 'dataType', key: 'dataType', width: 90 },
  { title: '依赖', key: 'dependencies', width: 200 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 60 },
  { title: '状态', key: 'status', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' },
]

// 示例数据 (后续从 API 获取)
const variables = ref<any[]>([])
const filteredVariables = computed(() => {
  let list = variables.value
  if (activeLayer.value !== 'ALL') {
    list = list.filter(v => v.layer === activeLayer.value)
  }
  if (searchKey.value) {
    const key = searchKey.value.toLowerCase()
    list = list.filter(v => v.name.toLowerCase().includes(key))
  }
  return list
})

function layerColor(layer: string): string {
  const map: Record<string, string> = {
    INPUT: 'blue',
    EXTERNAL_API: 'orange',
    FEATURE_STORE: 'purple',
    COMPUTED: 'cyan',
  }
  return map[layer] || 'default'
}

function layerLabel(layer: string): string {
  const map: Record<string, string> = {
    INPUT: 'Layer 0',
    EXTERNAL_API: 'Layer 1',
    FEATURE_STORE: 'Layer 2',
    COMPUTED: 'Layer 3',
  }
  return map[layer] || layer
}

function onLayerChange() {
  // 触发重新过滤
}

// 创建弹窗
const createModalVisible = ref(false)
const createForm = reactive({
  name: '',
  category: 'basic',
  layer: 'INPUT',
  dataType: 'STRING',
  expression: '',
  dependencies: [] as string[],
  description: '',
})

function showCreateModal() {
  Object.assign(createForm, {
    name: '', category: 'basic', layer: 'INPUT', dataType: 'STRING',
    expression: '', dependencies: [], description: '',
  })
  createModalVisible.value = true
}

function handleCreate() {
  if (!createForm.name.trim()) {
    message.warning('请输入变量名称')
    return
  }
  variables.value.push({
    id: `var_${Date.now()}`,
    ...createForm,
    version: 1,
    status: 'DRAFT',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  })
  createModalVisible.value = false
  message.success('变量已创建')
}

// 依赖图
const depGraphVisible = ref(false)
const depGraphData = ref<any>(null)

function showDependencyGraph(record: any) {
  depGraphData.value = {
    name: record.name,
    layer: record.layer,
    dependencies: record.dependencies || [],
    usedBy: [], // 后续从 API 获取
  }
  depGraphVisible.value = true
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.page-header h3 { margin: 0; }
.more-text { color: rgba(0, 0, 0, 0.45); font-size: 12px; }

.dep-graph-container {
  padding: 12px;
}
.dep-graph {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dep-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 13px;
}
.dep-node.current {
  background: #e6f4ff;
  border: 1px solid #91caff;
  font-weight: 600;
}
.dep-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dep-section h4 {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  margin: 8px 0 4px;
}
</style>
