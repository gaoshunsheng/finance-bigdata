<template>
  <div class="flow-designer-wrapper">
    <div class="designer-toolbar">
      <a-space>
        <a-input v-model:value="flowName" placeholder="决策流名称" style="width: 200px" />
        <a-divider type="vertical" />
        <span class="toolbar-label">节点:</span>
        <a-button
          v-for="nt in nodeTypes"
          :key="nt.type"
          size="small"
          :draggable="true"
          @dragstart="onDragStart($event, nt)"
        >
          <span :style="{ color: nt.color }">●</span> {{ nt.label }}
        </a-button>
      </a-space>
      <a-space>
        <a-button size="small" @click="validateFlow">
          <template #icon><CheckCircleOutlined /></template> 校验
        </a-button>
        <a-button size="small" @click="autoLayout">
          <template #icon><LayoutOutlined /></template> 自动布局
        </a-button>
        <a-button size="small" @click="clearCanvas">
          <template #icon><ClearOutlined /></template> 清空
        </a-button>
        <a-button type="primary" size="small" @click="exportFlow">
          <template #icon><SaveOutlined /></template> 保存
        </a-button>
      </a-space>
    </div>

    <div class="designer-body">
      <!-- 左侧: 节点面板 -->
      <div class="designer-sidebar">
        <div class="sidebar-section">
          <h4>节点类型</h4>
          <div class="node-palette">
            <div
              v-for="nt in nodeTypes"
              :key="nt.type"
              class="palette-item"
              :draggable="true"
              @dragstart="onDragStart($event, nt)"
            >
              <span class="palette-dot" :style="{ background: nt.color }"></span>
              <span>{{ nt.label }}</span>
            </div>
          </div>
        </div>

        <div class="sidebar-section" v-if="selectedNode">
          <h4>节点属性</h4>
          <a-form layout="vertical" size="small">
            <a-form-item label="名称">
              <a-input v-model:value="selectedNode!.name" @change="onNodePropChange" />
            </a-form-item>
            <a-form-item label="类型">
              <a-tag :color="nodeTypes.find(n => n.type === selectedNode!.type)?.color">
                {{ nodeTypes.find(n => n.type === selectedNode!.type)?.label }}
              </a-tag>
            </a-form-item>
            <a-form-item v-if="selectedNode!.type === 'RULE_SET'" label="规则集ID">
              <a-input v-model:value="selectedNode!.config.ruleId" @change="onNodePropChange" placeholder="关联规则集ID" />
            </a-form-item>
            <a-form-item v-if="selectedNode!.type === 'SCORECARD'" label="评分卡ID">
              <a-input v-model:value="selectedNode!.config.scorecardId" @change="onNodePropChange" placeholder="关联评分卡ID" />
            </a-form-item>
            <a-form-item v-if="selectedNode!.type === 'MODEL'" label="模型ID">
              <a-input v-model:value="selectedNode!.config.modelId" @change="onNodePropChange" placeholder="关联模型ID" />
            </a-form-item>
            <a-form-item v-if="selectedNode!.type === 'AB_SPLIT'" label="实验ID">
              <a-input v-model:value="selectedNode!.config.experimentId" @change="onNodePropChange" placeholder="关联实验ID" />
            </a-form-item>
            <a-form-item v-if="selectedNode!.type === 'SUB_FLOW'" label="子流程ID">
              <a-input v-model:value="selectedNode!.config.flowId" @change="onNodePropChange" placeholder="关联子流程ID" />
            </a-form-item>
            <a-form-item v-if="selectedNode!.type === 'ACTION'" label="动作配置">
              <a-textarea v-model:value="selectedNode!.config.actionJson" @change="onNodePropChange" :rows="3" placeholder='{"type": "OUTPUT", "result": "REJECT"}' />
            </a-form-item>
            <a-button size="small" danger block @click="deleteSelectedNode">删除节点</a-button>
          </a-form>
        </div>
      </div>

      <!-- 中间: 画布 -->
      <div
        ref="canvasRef"
        class="designer-canvas"
        @dragover.prevent
        @drop="onDrop"
      >
        <svg class="canvas-svg" ref="svgRef">
          <!-- 连线 -->
          <g v-for="edge in edges" :key="edge.id">
            <path
              :d="edgePath(edge)"
              stroke-width="2"
              fill="none"
              :stroke-dasharray="selectedEdgeId === edge.id ? '8,4' : 'none'"
              :stroke="selectedEdgeId === edge.id ? '#1677ff' : '#999'"
              class="edge-path"
              @click="selectEdge(edge)"
            />
            <text
              v-if="edge.label"
              :x="edgeMidpoint(edge).x"
              :y="edgeMidpoint(edge).y - 8"
              text-anchor="middle"
              font-size="11"
              fill="#666"
            >
              {{ edge.label }}
            </text>
          </g>

          <!-- 节点 -->
          <g
            v-for="node in nodes"
            :key="node.id"
            class="canvas-node"
            :transform="`translate(${node.position.x}, ${node.position.y})`"
            @mousedown="onNodeMouseDown($event, node)"
            @click.stop="selectNode(node)"
          >
            <rect
              :width="nodeWidth"
              :height="nodeHeight"
              :rx="node.type === 'START' || node.type === 'END' ? 20 : 6"
              :fill="selectedNodeId === node.id ? '#e6f4ff' : '#fff'"
              :stroke="getNodeColor(node.type)"
              stroke-width="2"
              class="node-rect"
            />
            <circle :cx="8" :cy="8" r="5" :fill="getNodeColor(node.type)" />
            <text
              :x="nodeWidth / 2"
              :y="nodeHeight / 2 - 4"
              text-anchor="middle"
              font-size="12"
              font-weight="600"
              fill="#333"
            >
              {{ node.name || node.type }}
            </text>
            <text
              :x="nodeWidth / 2"
              :y="nodeHeight / 2 + 12"
              text-anchor="middle"
              font-size="10"
              fill="#999"
            >
              {{ nodeTypeLabel(node.type) }}
            </text>

            <!-- 输出端口 (连接点) -->
            <circle
              :cx="nodeWidth"
              :cy="nodeHeight / 2"
              r="5"
              fill="#1677ff"
              class="port out-port"
              @mousedown.stop="startEdgeDraw($event, node)"
            />
            <!-- 输入端口 -->
            <circle
              :cx="0"
              :cy="nodeHeight / 2"
              r="5"
              fill="#52c41a"
              class="port in-port"
            />
          </g>

          <!-- 正在绘制的线 -->
          <line
            v-if="drawingEdge"
            :x1="drawingEdge.fromX"
            :y1="drawingEdge.fromY"
            :x2="drawingEdge.toX"
            :y2="drawingEdge.toY"
            stroke="#1677ff"
            stroke-width="2"
            stroke-dasharray="4,4"
          />
        </svg>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import {
  CheckCircleOutlined,
  LayoutOutlined,
  ClearOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { FlowNode, FlowEdge, FlowModel, NodeType } from '@/types/flow'
import { nodeTypeLabel } from '@/utils'

const props = defineProps<{ modelValue?: FlowModel }>()
const emit = defineEmits<{ (e: 'update:modelValue', val: FlowModel): void }>()

const flowName = ref('')
const canvasRef = ref<HTMLElement>()
const svgRef = ref<SVGSVGElement>()

const nodeWidth = 140
const nodeHeight = 50

const nodes = reactive<FlowNode[]>([])
const edges = reactive<FlowEdge[]>([])

const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const selectedNode = ref<FlowNode | null>(null)

// 拖拽状态
let dragging: { nodeId: string; startX: number; startY: number; offsetX: number; offsetY: number } | null = null
const drawingEdge = ref<{ fromNodeId: string; fromX: number; fromY: number; toX: number; toY: number } | null>(null)

const nodeTypes: Array<{ type: NodeType; label: string; color: string }> = [
  { type: 'START', label: '开始', color: '#52c41a' },
  { type: 'END', label: '结束', color: '#ff4d4f' },
  { type: 'DATA_PREP', label: '数据准备', color: '#1677ff' },
  { type: 'RULE_SET', label: '规则集', color: '#fa8c16' },
  { type: 'SCORECARD', label: '评分卡', color: '#722ed1' },
  { type: 'MODEL', label: '模型', color: '#13c2c2' },
  { type: 'DECISION', label: '决策', color: '#eb2f96' },
  { type: 'AB_SPLIT', label: 'AB分流', color: '#2f54eb' },
  { type: 'ACTION', label: '动作', color: '#f5222d' },
  { type: 'SUB_FLOW', label: '子流程', color: '#a0d911' },
  { type: 'SCRIPT', label: '脚本', color: '#597ef7' },
]

function getNodeColor(type: string): string {
  return nodeTypes.find(n => n.type === type)?.color || '#999'
}

let idSeq = 0
function nextId(prefix: string): string {
  return `${prefix}_${++idSeq}_${Date.now().toString(36)}`
}

// --- 拖放节点到画布 ---
function onDragStart(e: DragEvent, nt: { type: NodeType; label: string }) {
  e.dataTransfer?.setData('nodeType', JSON.stringify(nt))
}

function onDrop(e: DragEvent) {
  const data = e.dataTransfer?.getData('nodeType')
  if (!data || !canvasRef.value) return
  const nt = JSON.parse(data)
  const rect = canvasRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left - nodeWidth / 2
  const y = e.clientY - rect.top - nodeHeight / 2

  const node: FlowNode = {
    id: nextId('node'),
    type: nt.type,
    name: nt.label,
    position: { x: Math.max(0, x), y: Math.max(0, y) },
    config: {},
  }
  nodes.push(node)
}

// --- 节点拖动 ---
function onNodeMouseDown(e: MouseEvent, node: FlowNode) {
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  dragging = {
    nodeId: node.id,
    startX: e.clientX,
    startY: e.clientY,
    offsetX: node.position.x,
    offsetY: node.position.y,
  }
  e.preventDefault()
}

function onMouseMove(e: MouseEvent) {
  if (drawingEdge.value && canvasRef.value) {
    const rect = canvasRef.value.getBoundingClientRect()
    drawingEdge.value.toX = e.clientX - rect.left
    drawingEdge.value.toY = e.clientY - rect.top
    return
  }
  if (!dragging) return
  const node = nodes.find(n => n.id === dragging!.nodeId)
  if (!node) return
  node.position.x = dragging.offsetX + (e.clientX - dragging.startX)
  node.position.y = dragging.offsetY + (e.clientY - dragging.startY)
}

function onMouseUp() {
  dragging = null
  if (drawingEdge.value) {
    drawingEdge.value = null
  }
}

// --- 连线绘制 ---
function startEdgeDraw(e: MouseEvent, fromNode: FlowNode) {
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  drawingEdge.value = {
    fromNodeId: fromNode.id,
    fromX: fromNode.position.x + nodeWidth,
    fromY: fromNode.position.y + nodeHeight / 2,
    toX: e.clientX - rect.left,
    toY: e.clientY - rect.top,
  }
}

// --- 选择 ---
function selectNode(node: FlowNode) {
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  selectedNode.value = node
}

function selectEdge(edge: FlowEdge) {
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
  selectedNode.value = null
}

function deleteSelectedNode() {
  if (!selectedNode.value) return
  const id = selectedNode.value.id
  const idx = nodes.findIndex(n => n.id === id)
  if (idx >= 0) nodes.splice(idx, 1)
  // 移除关联边
  for (let i = edges.length - 1; i >= 0; i--) {
    if (edges[i].source === id || edges[i].target === id) edges.splice(i, 1)
  }
  selectedNode.value = null
  selectedNodeId.value = null
}

function onNodePropChange() {
  // 触发响应式更新
}

// --- 连线路径 ---
function edgePath(edge: FlowEdge): string {
  const from = nodes.find(n => n.id === edge.source)
  const to = nodes.find(n => n.id === edge.target)
  if (!from || !to) return ''
  const x1 = from.position.x + nodeWidth
  const y1 = from.position.y + nodeHeight / 2
  const x2 = to.position.x
  const y2 = to.position.y + nodeHeight / 2
  const cx = (x1 + x2) / 2
  return `M ${x1} ${y1} C ${cx} ${y1}, ${cx} ${y2}, ${x2} ${y2}`
}

function edgeMidpoint(edge: FlowEdge) {
  const from = nodes.find(n => n.id === edge.source)
  const to = nodes.find(n => n.id === edge.target)
  if (!from || !to) return { x: 0, y: 0 }
  return {
    x: (from.position.x + nodeWidth + to.position.x) / 2,
    y: (from.position.y + to.position.y) / 2 + nodeHeight / 2,
  }
}

// --- 操作 ---
function validateFlow() {
  if (!nodes.length) {
    message.warning('画布为空，请添加节点')
    return
  }
  const startNodes = nodes.filter(n => n.type === 'START')
  const endNodes = nodes.filter(n => n.type === 'END')
  if (startNodes.length === 0) { message.error('缺少开始节点'); return }
  if (startNodes.length > 1) { message.error('只能有一个开始节点'); return }
  if (endNodes.length === 0) { message.error('缺少结束节点'); return }

  // 检查孤立节点
  const connectedIds = new Set<string>()
  edges.forEach(e => { connectedIds.add(e.source); connectedIds.add(e.target) })
  const orphans = nodes.filter(n => !connectedIds.has(n.id) && n.type !== 'START' && n.type !== 'END')
  if (orphans.length) {
    message.warning(`存在 ${orphans.length} 个孤立节点: ${orphans.map(n => n.name).join(', ')}`)
  }

  message.success('校验通过')
}

function autoLayout() {
  // 简单网格布局
  const cols = 4
  const gapX = 200
  const gapY = 100
  nodes.forEach((n, i) => {
    n.position.x = 80 + (i % cols) * gapX
    n.position.y = 80 + Math.floor(i / cols) * gapY
  })
  message.success('已自动布局')
}

function clearCanvas() {
  nodes.length = 0
  edges.length = 0
  selectedNode.value = null
  selectedNodeId.value = null
  selectedEdgeId.value = null
}

function exportFlow() {
  const model: FlowModel = {
    nodes: nodes.map(n => ({ ...n })),
    edges: edges.map(e => ({ ...e })),
  }
  emit('update:modelValue', model)
  message.success('已导出决策流')
}

// 事件监听
onMounted(() => {
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)

  // 加载初始数据
  if (props.modelValue) {
    nodes.push(...props.modelValue.nodes)
    edges.push(...props.modelValue.edges)
  } else {
    // 默认添加开始和结束节点
    nodes.push(
      { id: 'start', type: 'START', name: '开始', position: { x: 80, y: 200 }, config: {} },
      { id: 'end', type: 'END', name: '结束', position: { x: 600, y: 200 }, config: {} },
    )
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<style scoped>
.flow-designer-wrapper {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}

.designer-body {
  display: flex;
  height: 600px;
}

.designer-sidebar {
  width: 200px;
  border-right: 1px solid #f0f0f0;
  background: #fafafa;
  overflow-y: auto;
  padding: 8px;
}

.sidebar-section {
  margin-bottom: 16px;
}

.sidebar-section h4 {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.85);
}

.node-palette {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  cursor: grab;
  font-size: 12px;
  transition: all 0.2s;
}

.palette-item:hover {
  border-color: #1677ff;
  box-shadow: 0 1px 4px rgba(22, 119, 255, 0.15);
}

.palette-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.designer-canvas {
  flex: 1;
  background:
    radial-gradient(circle, #e8e8e8 1px, transparent 1px);
  background-size: 20px 20px;
  position: relative;
  overflow: hidden;
}

.canvas-svg {
  width: 100%;
  height: 100%;
}

.canvas-node {
  cursor: move;
}

.node-rect {
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.12));
  transition: fill 0.2s;
}

.canvas-node:hover .node-rect {
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.2));
}

.port {
  opacity: 0;
  transition: opacity 0.2s;
  cursor: crosshair;
}

.canvas-node:hover .port {
  opacity: 1;
}

.edge-path {
  cursor: pointer;
}

.edge-path:hover {
  stroke: #1677ff;
  stroke-width: 3;
}
</style>
