<template>
  <div class="publish-center">
    <a-row :gutter="16">
      <!-- 灰度发布 -->
      <a-col :span="12">
        <a-card title="灰度发布" :bordered="false" :body-style="{ padding: 0 }">
          <template #extra>
            <a-button type="primary" size="small" @click="showGrayscaleModal = true">新建灰度发布</a-button>
          </template>
          <a-table :columns="gsColumns" :data-source="grayscales" :loading="gsLoading" row-key="configId" size="middle" :pagination="false" />
        </a-card>
      </a-col>

      <!-- 发布历史 -->
      <a-col :span="12">
        <a-card title="发布历史" :bordered="false" :body-style="{ padding: 0 }">
          <a-table :columns="histColumns" :data-source="histories" :loading="histLoading" row-key="recordId" size="middle" :pagination="false" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 新建灰度发布弹窗 -->
    <a-modal v-model:open="showGrayscaleModal" title="新建灰度发布" @ok="handleCreateGrayscale" @cancel="resetGrayscaleForm" :confirm-loading="creating">
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="目标类型" required>
          <a-select v-model:value="gsForm.targetType" placeholder="选择目标类型">
            <a-select-option value="RULE">规则集</a-select-option>
            <a-select-option value="SCORECARD">评分卡</a-select-option>
            <a-select-option value="DECISION_TABLE">决策表</a-select-option>
            <a-select-option value="DECISION_TREE">决策树</a-select-option>
            <a-select-option value="FLOW">决策流</a-select-option>
            <a-select-option value="VARIABLE">变量</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目标 ID" required>
          <a-input v-model:value="gsForm.targetId" placeholder="如 RS_BLACKLIST" />
        </a-form-item>
        <a-form-item label="目标版本">
          <a-input-number v-model:value="gsForm.targetVersion" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="灰度比例" required>
          <a-slider v-model:value="gsForm.percentage" :min="1" :max="100" :marks="{ 0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%' }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getAllGrayscaleConfigs, getAllApprovalRecords, startGrayscale } from '@/api/publish'

/* ========== 灰度发布 ========== */
interface GrayscaleRow {
  configId: string
  targetType: string
  targetId: string
  targetVersion: number
  percentage: number
  previousPercentage: number
  operator: string
  startedAt: string
  updatedAt: string
  grayscaleStatus: string
}

const grayscales = ref<GrayscaleRow[]>([])
const gsLoading = ref(false)

const gsColumns = [
  { title: '目标类型', dataIndex: 'targetType', key: 'targetType', width: 100, customRender: ({ text }: any) => targetTypeLabel(text) },
  { title: '目标 ID', dataIndex: 'targetId', key: 'targetId', width: 140, ellipsis: true },
  { title: '版本', dataIndex: 'targetVersion', key: 'targetVersion', width: 60, align: 'center' as const },
  { title: '当前比例', dataIndex: 'percentage', key: 'percentage', width: 90, customRender: ({ text }: any) => `${text}%` },
  { title: '状态', dataIndex: 'grayscaleStatus', key: 'grayscaleStatus', width: 90 },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 90, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
]

function targetTypeLabel(t: string): string {
  const m: Record<string, string> = { RULE: '规则集', SCORECARD: '评分卡', DECISION_TABLE: '决策表', DECISION_TREE: '决策树', FLOW: '决策流', VARIABLE: '变量' }
  return m[t] || t
}

function gsColor(s: string): string {
  const m: Record<string, string> = { GRAYSCALE: 'processing', IN_PROGRESS: 'processing', RELEASED: 'success', FULL: 'success', NOT_STARTED: 'default', PAUSED: 'warning', ROLLED_BACK: 'error' }
  return m[s] || 'default'
}

async function fetchGrayscales() {
  gsLoading.value = true
  try {
    grayscales.value = await getAllGrayscaleConfigs()
  } catch (e: any) {
    message.error('灰度数据加载失败')
  } finally {
    gsLoading.value = false
  }
}

/* ========== 新建灰度发布 ========== */
const showGrayscaleModal = ref(false)
const creating = ref(false)
const gsForm = ref({ targetType: 'RULE', targetId: '', targetVersion: 1, percentage: 10 })

function resetGrayscaleForm() {
  gsForm.value = { targetType: 'RULE', targetId: '', targetVersion: 1, percentage: 10 }
}

async function handleCreateGrayscale() {
  if (!gsForm.value.targetId) {
    message.warning('请输入目标 ID')
    return
  }
  creating.value = true
  try {
    await startGrayscale(gsForm.value.targetId, gsForm.value.targetType, gsForm.value.percentage)
    message.success('灰度发布创建成功')
    showGrayscaleModal.value = false
    resetGrayscaleForm()
    await fetchGrayscales()
  } catch (e: any) {
    message.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

/* ========== 发布历史 ========== */
interface HistoryRow {
  recordId: string
  targetType: string
  targetId: string
  targetVersion: number
  action: string
  operator: string
  comment?: string
  operatedAt: string
}

const histories = ref<HistoryRow[]>([])
const histLoading = ref(false)

const histColumns = [
  { title: '目标类型', dataIndex: 'targetType', key: 'targetType', width: 100, customRender: ({ text }: any) => targetTypeLabel(text) },
  { title: '目标 ID', dataIndex: 'targetId', key: 'targetId', width: 140, ellipsis: true },
  { title: '版本', dataIndex: 'targetVersion', key: 'targetVersion', width: 60, align: 'center' as const },
  { title: '操作', dataIndex: 'action', key: 'action', width: 90, customRender: ({ text }: any) => actionLabel(text) },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 110, ellipsis: true },
  { title: '备注', dataIndex: 'comment', key: 'comment', width: 180, ellipsis: true },
  { title: '操作时间', dataIndex: 'operatedAt', key: 'operatedAt', width: 160 },
]

function actionLabel(a: string): string {
  const m: Record<string, string> = { SUBMIT: '提交审批', APPROVE: '审批通过', REJECT: '审批驳回', WITHDRAW: '撤回' }
  return m[a] || a
}

async function fetchHistories() {
  histLoading.value = true
  try {
    histories.value = await getAllApprovalRecords()
  } catch (e: any) {
    message.error('历史数据加载失败')
  } finally {
    histLoading.value = false
  }
}

onMounted(() => {
  fetchGrayscales()
  fetchHistories()
})
</script>

<style scoped>
.publish-center { padding: 12px; }
.ant-card { margin-bottom: 16px; }
.ant-table { font-size: 13px; }
</style>
