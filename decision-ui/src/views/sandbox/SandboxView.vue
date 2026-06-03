<template>
  <div>
    <a-page-header title="沙箱测试">
      <template #extra>
        <a-space>
          <a-button @click="loadSample">加载示例数据</a-button>
          <a-button @click="clearAll">清空</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-row :gutter="16">
      <!-- 左侧: 输入 -->
      <a-col :span="10">
        <a-card title="输入数据" size="small">
          <a-form layout="vertical">
            <a-row :gutter="8">
              <a-col :span="12">
                <a-form-item label="目标类型">
                  <a-select v-model:value="form.targetType">
                    <a-select-option value="RULE">规则</a-select-option>
                    <a-select-option value="SCORECARD">评分卡</a-select-option>
                    <a-select-option value="TABLE">决策表</a-select-option>
                    <a-select-option value="TREE">决策树</a-select-option>
                    <a-select-option value="FLOW">决策流</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="目标ID">
                  <a-input v-model:value="form.targetId" placeholder="目标ID" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="版本 (可选)">
              <a-input-number v-model:value="form.version" placeholder="留空用最新" style="width: 100%" />
            </a-form-item>
            <a-form-item label="JSON 输入">
              <a-textarea
                v-model:value="form.inputData"
                :rows="12"
                placeholder='{"age": 25, "income": 50000}'
                style="font-family: 'Fira Code', Consolas, monospace; font-size: 12px"
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" block :loading="executing" @click="execute">
                <template #icon><PlayCircleOutlined /></template> 执行测试
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>

      <!-- 右侧: 结果 -->
      <a-col :span="14">
        <a-card title="执行结果" size="small">
          <template v-if="!result">
            <a-empty description="执行测试后结果将在此展示" />
          </template>
          <template v-else>
            <!-- 概要 -->
            <a-descriptions :column="4" size="small" bordered style="margin-bottom: 16px">
              <a-descriptions-item label="TraceID">{{ result.traceId }}</a-descriptions-item>
              <a-descriptions-item label="结果">
                <a-tag :color="resultColor(result.result)">{{ result.result }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="得分">{{ result.score ?? '-' }}</a-descriptions-item>
              <a-descriptions-item label="耗时">{{ result.durationMs }}ms</a-descriptions-item>
            </a-descriptions>

            <!-- 执行轨迹 -->
            <a-divider orientation="left" style="font-size: 13px">执行轨迹</a-divider>
            <a-timeline>
              <a-timeline-item
                v-for="(entry, idx) in result.trace"
                :key="idx"
                :color="traceColor(entry)"
              >
                <div class="trace-entry">
                  <div class="trace-header">
                    <a-tag size="small">{{ entry.nodeType }}</a-tag>
                    <span class="trace-name">{{ entry.nodeName }}</span>
                    <span class="trace-duration">{{ entry.durationMs }}ms</span>
                  </div>
                  <div class="trace-detail" v-if="expandedTrace[idx]">
                    <a-descriptions :column="1" size="small">
                      <a-descriptions-item label="输入">
                        <code class="trace-json">{{ JSON.stringify(entry.input, null, 0) }}</code>
                      </a-descriptions-item>
                      <a-descriptions-item label="输出">
                        <code class="trace-json">{{ JSON.stringify(entry.output, null, 0) }}</code>
                      </a-descriptions-item>
                      <a-descriptions-item v-if="entry.details" label="详情">
                        <code class="trace-json">{{ JSON.stringify(entry.details, null, 0) }}</code>
                      </a-descriptions-item>
                    </a-descriptions>
                  </div>
                  <a-button type="link" size="small" @click="toggleTrace(idx)">
                    {{ expandedTrace[idx] ? '收起' : '展开' }}
                  </a-button>
                </div>
              </a-timeline-item>
            </a-timeline>
          </template>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { PlayCircleOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { SandboxResult, TraceEntry } from '@/types/experiment'

const form = reactive({
  targetType: 'RULE',
  targetId: '',
  version: undefined as number | undefined,
  inputData: '{\n  "age": 28,\n  "income": 85000,\n  "loanAmount": 200000,\n  "overdueCount": 0\n}',
})

const executing = ref(false)
const result = ref<SandboxResult | null>(null)
const expandedTrace = ref<Record<number, boolean>>({})

function resultColor(r: string): string {
  const map: Record<string, string> = { APPROVE: 'green', REJECT: 'red', MANUAL: 'orange' }
  return map[r] || 'default'
}

function traceColor(entry: TraceEntry): string {
  const output = JSON.stringify(entry.output)
  if (output.includes('REJECT')) return 'red'
  if (output.includes('APPROVE')) return 'green'
  return 'blue'
}

function toggleTrace(idx: number) {
  expandedTrace.value[idx] = !expandedTrace.value[idx]
}

async function execute() {
  if (!form.targetId) {
    message.warning('请输入目标ID')
    return
  }
  executing.value = true
  try {
    // 模拟执行 (后续连接后端API)
    await new Promise(r => setTimeout(r, 800))
    result.value = {
      traceId: `trace_${Date.now().toString(36)}`,
      result: 'APPROVE',
      score: 720,
      durationMs: 42,
      trace: [
        { nodeId: 'start', nodeType: 'START', nodeName: '开始', startTime: 0, endTime: 1, durationMs: 1, input: {}, output: {} },
        { nodeId: 'prep', nodeType: 'DATA_PREP', nodeName: '数据准备', startTime: 1, endTime: 5, durationMs: 4, input: { age: 28 }, output: { age: 28, income: 85000 } },
        { nodeId: 'rule1', nodeType: 'RULE_SET', nodeName: '准入规则', startTime: 5, endTime: 15, durationMs: 10, input: { age: 28, overdueCount: 0 }, output: { result: 'PASS' }, details: { matchedRule: 'age >= 18 AND overdueCount == 0' } },
        { nodeId: 'score1', nodeType: 'SCORECARD', nodeName: '信用评分', startTime: 15, endTime: 35, durationMs: 20, input: { age: 28, income: 85000 }, output: { score: 720 }, details: { characteristics: [{ name: '年龄', bin: '25-35', score: 85 }, { name: '收入', bin: '50k+', score: 120 }] } },
        { nodeId: 'end', nodeType: 'END', nodeName: '结束', startTime: 35, endTime: 42, durationMs: 7, input: { score: 720 }, output: { result: 'APPROVE' } },
      ],
    }
    expandedTrace.value = {}
    message.success('执行完成')
  } finally {
    executing.value = false
  }
}

function loadSample() {
  form.targetType = 'FLOW'
  form.targetId = 'credit-approval-flow'
  form.inputData = JSON.stringify({
    name: '张三',
    age: 28,
    income: 85000,
    loanAmount: 200000,
    overdueCount: 0,
    hasCreditCard: true,
    employmentType: 'FULL_TIME',
  }, null, 2)
}

function clearAll() {
  form.targetId = ''
  form.version = undefined
  form.inputData = '{\n  \n}'
  result.value = null
  expandedTrace.value = {}
}
</script>

<style scoped>
.trace-entry {
  font-size: 13px;
}
.trace-header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.trace-name {
  font-weight: 500;
}
.trace-duration {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
.trace-detail {
  margin: 4px 0;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
}
.trace-json {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.65);
  word-break: break-all;
}
</style>
