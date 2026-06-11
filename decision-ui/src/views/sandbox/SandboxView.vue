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
      <a-col :span="10">
        <a-card title="输入数据" size="small">
          <a-form layout="vertical">
            <a-row :gutter="8">
              <a-col :span="12">
                <a-form-item label="策略ID">
                  <a-input v-model:value="form.strategyId" placeholder="FLOW_CREDIT_MAIN" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="渠道">
                  <a-select v-model:value="form.channel">
                    <a-select-option value="APP">APP</a-select-option>
                    <a-select-option value="WEB">WEB</a-select-option>
                    <a-select-option value="API">API</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="JSON 输入 (applicant)">
              <a-textarea
                v-model:value="form.inputData"
                :rows="10"
                placeholder='{"customerId":"CUST_00001","age":35,"income":15000,...}'
                style="font-family: monospace; font-size: 12px"
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" block :loading="executing" @click="execute">
                <template #icon><PlayCircleOutlined /></template> 执行决策
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>

      <a-col :span="14">
        <a-card title="执行结果" size="small">
          <template v-if="!result">
            <a-empty description="执行决策后结果将在此展示" />
          </template>
          <template v-else>
            <a-descriptions :column="3" size="small" bordered style="margin-bottom: 12px">
              <a-descriptions-item label="TraceID">{{ result.traceId }}</a-descriptions-item>
              <a-descriptions-item label="决策ID">{{ result.decisionId }}</a-descriptions-item>
              <a-descriptions-item label="耗时">{{ result.durationMs }}ms</a-descriptions-item>
              <a-descriptions-item label="结果">
                <a-tag :color="resultColor(result.result)">{{ result.result }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="评分">{{ result.score ?? '-' }}</a-descriptions-item>
              <a-descriptions-item label="拒绝码">{{ result.rejectCode || '-' }}</a-descriptions-item>
            </a-descriptions>

            <a-descriptions v-if="result.rejectReason" :column="1" size="small" bordered style="margin-bottom: 12px">
              <a-descriptions-item label="拒绝原因">{{ result.rejectReason }}</a-descriptions-item>
            </a-descriptions>

            <a-tabs size="small">
              <a-tab-pane key="response" tab="完整响应">
                <a-textarea :value="JSON.stringify(result.rawResponse, null, 2)" :rows="12" readonly style="font-family: monospace; font-size: 11px" />
              </a-tab-pane>
              <a-tab-pane key="request" tab="原始请求">
                <a-textarea :value="JSON.stringify(result.request, null, 2)" :rows="12" readonly style="font-family: monospace; font-size: 11px" />
              </a-tab-pane>
            </a-tabs>
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
import axios from 'axios'

const form = reactive({
  strategyId: 'FLOW_CREDIT_MAIN',
  channel: 'APP',
  inputData: '{\n  "customerId": "CUST_00001",\n  "age": 35,\n  "income": 15000,\n  "loanAmount": 200000,\n  "loanPurpose": "CONSUMPTION",\n  "loanTerm": 12,\n  "productType": "P001"\n}',
})

const executing = ref(false)
const result = ref<any>(null)

function resultColor(r: string): string {
  const map: Record<string, string> = { PASS: 'green', REJECT: 'red', REVIEW: 'orange', MANUAL: 'blue' }
  return map[r] || 'default'
}

async function execute() {
  if (!form.strategyId.trim()) { message.warning('请输入策略ID'); return }
  let applicant: any
  try {
    applicant = JSON.parse(form.inputData)
  } catch {
    message.error('JSON 格式错误')
    return
  }

  executing.value = true
  try {
    const resp = await axios.post('/api/v1/decision/execute', {
      strategyId: form.strategyId,
      channel: form.channel,
      applicant,
      metadata: { testMode: true },
    })
    const data = resp.data
    result.value = {
      traceId: data.traceId || '-',
      decisionId: data.decisionId || '-',
      result: data.result,
      score: data.score,
      rejectReason: data.rejectReason,
      rejectCode: data.rejectCode,
      durationMs: data.durationMs,
      rawResponse: data,
      request: { strategyId: form.strategyId, channel: form.channel, applicant },
    }
    message.success('执行完成')
  } catch (err: any) {
    message.error(err?.response?.data?.message || err?.message || '执行失败')
  } finally { executing.value = false }
}

function loadSample() {
  form.strategyId = 'FLOW_CREDIT_MAIN'
  form.inputData = JSON.stringify({
    customerId: 'CUST_00001',
    age: 35,
    income: 18000,
    loanAmount: 300000,
    loanPurpose: 'CONSUMPTION',
    loanTerm: 12,
    productType: 'P001',
    channel: 'APP',
  }, null, 2)
}

function clearAll() {
  form.strategyId = ''
  form.inputData = '{\n  \n}'
  result.value = null
}
</script>
