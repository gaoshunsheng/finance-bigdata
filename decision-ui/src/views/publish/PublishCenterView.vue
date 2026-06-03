<template>
  <div>
    <a-page-header title="发布中心" />

    <a-tabs v-model:activeKey="activeTab">
      <!-- 待审批 -->
      <a-tab-pane key="pending" tab="待审批">
        <template #tab>
          待审批 <a-badge :count="pendingList.length" :offset="[6, -2]" />
        </template>
        <a-table :columns="pendingColumns" :data-source="pendingList" :pagination="{ pageSize: 20 }" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-space>
                <a-popconfirm title="确认通过审批?" @confirm="handleApprove(record)">
                  <a-button type="link" size="small" style="color: #52c41a">通过</a-button>
                </a-popconfirm>
                <a-button type="link" size="small" @click="openRejectModal(record)">驳回</a-button>
                <a-button type="link" size="small" @click="handleDiff(record)">版本对比</a-button>
              </a-space>
            </template>
          </template>
          <template #emptyText><a-empty description="暂无待审批记录" /></template>
        </a-table>
      </a-tab-pane>

      <!-- 灰度发布 -->
      <a-tab-pane key="grayscale" tab="灰度发布">
        <a-alert
          message="灰度发布支持按百分比逐步提升流量，建议步骤: 5% → 25% → 50% → 100%"
          type="info"
          show-icon
          style="margin-bottom: 12px"
        />
        <a-table :columns="grayscaleColumns" :data-source="grayscaleList" :pagination="{ pageSize: 20 }" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'percentage'">
              <a-progress :percent="record.percentage" :size="'small'" :stroke-color="'#1677ff'" />
            </template>
            <template v-if="column.key === 'status'">
              <a-tag :color="grayscaleStatusColor(record.status)">{{ record.status }}</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="handleRampUp(record)" :disabled="record.status !== 'IN_PROGRESS'">
                  提升
                </a-button>
                <a-button type="link" size="small" @click="handlePauseGrayscale(record)" :disabled="record.status !== 'IN_PROGRESS'">
                  暂停
                </a-button>
                <a-button type="link" size="small" @click="handleResumeGrayscale(record)" :disabled="record.status !== 'PAUSED'">
                  恢复
                </a-button>
                <a-popconfirm title="确认回滚? 灰度比例将归零" @confirm="handleRollback(record)">
                  <a-button type="link" size="small" danger>回滚</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
          <template #emptyText><a-empty description="暂无灰度发布记录" /></template>
        </a-table>
      </a-tab-pane>

      <!-- 版本对比 -->
      <a-tab-pane key="diff" tab="版本对比">
        <a-form layout="inline" style="margin-bottom: 16px">
          <a-form-item label="目标类型">
            <a-select v-model:value="diffForm.targetType" style="width: 120px">
              <a-select-option value="RULE">规则</a-select-option>
              <a-select-option value="SCORECARD">评分卡</a-select-option>
              <a-select-option value="TABLE">决策表</a-select-option>
              <a-select-option value="FLOW">决策流</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="目标ID">
            <a-input v-model:value="diffForm.targetId" placeholder="输入目标ID" style="width: 200px" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="loadDiff">查看差异</a-button>
          </a-form-item>
        </a-form>
        <a-empty v-if="!diffData.length" description="选择目标后查看版本差异" />
        <div v-else class="diff-list">
          <div v-for="(d, i) in diffData" :key="i" :class="['diff-item', `diff-${d.type.toLowerCase()}`]">
            <a-tag :color="diffTypeColor(d.type)">{{ d.type }}</a-tag>
            <span class="diff-field">{{ d.field }}</span>
            <span v-if="d.oldValue" class="diff-old">{{ d.oldValue }}</span>
            <span v-if="d.newValue" class="diff-new">{{ d.newValue }}</span>
          </div>
        </div>
      </a-tab-pane>

      <!-- 发布历史 -->
      <a-tab-pane key="history" tab="发布历史">
        <a-table :columns="historyColumns" :data-source="historyList" :pagination="{ pageSize: 20 }" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            </template>
          </template>
          <template #emptyText><a-empty description="暂无发布历史" /></template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- 驳回弹窗 -->
    <a-modal v-model:open="rejectModalVisible" title="驳回审批" @ok="handleReject">
      <a-form layout="vertical">
        <a-form-item label="驳回原因" required>
          <a-textarea v-model:value="rejectReason" :rows="3" placeholder="请输入驳回原因" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { statusColor, statusLabel } from '@/utils'

const activeTab = ref('pending')

// --- 待审批 ---
const pendingList = ref<any[]>([])
const pendingColumns = [
  { title: '目标名称', dataIndex: 'targetName', key: 'targetName' },
  { title: '类型', dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
  { title: '提交人', dataIndex: 'operator', key: 'operator', width: 100 },
  { title: '提交时间', dataIndex: 'operatedAt', key: 'operatedAt', width: 180 },
  { title: '操作', key: 'action', width: 220 },
]

function handleApprove(record: any) {
  message.success(`已通过: ${record.targetName}`)
}

const rejectModalVisible = ref(false)
const rejectReason = ref('')
let rejectingRecord: any = null

function openRejectModal(record: any) {
  rejectingRecord = record
  rejectReason.value = ''
  rejectModalVisible.value = true
}

function handleReject() {
  if (!rejectReason.value.trim()) {
    message.warning('请输入驳回原因')
    return
  }
  message.success(`已驳回: ${rejectingRecord?.targetName}`)
  rejectModalVisible.value = false
}

function handleDiff(record: any) {
  activeTab.value = 'diff'
  diffForm.targetId = record.targetId
  diffForm.targetType = record.targetType
}

// --- 灰度 ---
const grayscaleList = ref<any[]>([])
const grayscaleColumns = [
  { title: '目标名称', dataIndex: 'targetName', key: 'targetName' },
  { title: '类型', dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: '灰度比例', key: 'percentage', width: 200 },
  { title: '状态', key: 'status', width: 120 },
  { title: '操作', key: 'action', width: 280 },
]

function grayscaleStatusColor(status: string): string {
  const map: Record<string, string> = {
    NOT_STARTED: 'default',
    IN_PROGRESS: 'processing',
    FULL: 'success',
    PAUSED: 'warning',
    ROLLED_BACK: 'error',
  }
  return map[status] || 'default'
}

function handleRampUp(record: any) {
  const steps = [5, 25, 50, 100]
  const next = steps.find(s => s > record.percentage)
  if (next) {
    record.percentage = next
    message.success(`已提升到 ${next}%`)
    if (next === 100) record.status = 'FULL'
  }
}

function handlePauseGrayscale(record: any) {
  record.status = 'PAUSED'
  message.info('灰度已暂停')
}

function handleResumeGrayscale(record: any) {
  record.status = 'IN_PROGRESS'
  message.info('灰度已恢复')
}

function handleRollback(record: any) {
  record.percentage = 0
  record.status = 'ROLLED_BACK'
  message.warning('已回滚')
}

// --- 版本对比 ---
const diffForm = reactive({ targetType: 'RULE', targetId: '' })
const diffData = ref<Array<{ type: string; field: string; oldValue?: string; newValue?: string }>>([])

function diffTypeColor(type: string): string {
  const map: Record<string, string> = { ADDED: 'green', REMOVED: 'red', MODIFIED: 'orange', UNCHANGED: 'default' }
  return map[type] || 'default'
}

function loadDiff() {
  if (!diffForm.targetId) {
    message.warning('请输入目标ID')
    return
  }
  diffData.value = []
  message.info('暂无版本数据 (需连接后端API)')
}

// --- 发布历史 ---
const historyList = ref<any[]>([])
const historyColumns = [
  { title: '目标名称', dataIndex: 'targetName', key: 'targetName' },
  { title: '类型', dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: '版本', dataIndex: 'toVersion', key: 'toVersion', width: 80 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 100 },
  { title: '操作时间', dataIndex: 'operatedAt', key: 'operatedAt', width: 180 },
]
</script>

<style scoped>
.diff-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.diff-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 13px;
}
.diff-added { background: #f6ffed; }
.diff-removed { background: #fff2f0; }
.diff-modified { background: #fffbe6; }
.diff-field { font-family: monospace; font-weight: 500; }
.diff-old { text-decoration: line-through; color: #ff4d4f; }
.diff-new { color: #52c41a; font-weight: 500; }
</style>
