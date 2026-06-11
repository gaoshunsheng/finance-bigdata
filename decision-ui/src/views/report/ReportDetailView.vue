<template>
  <div>
    <a-page-header title="日志详情" @back="$router.back()" />
    <a-spin :spinning="loading">
      <template v-if="report">
        <a-descriptions bordered :column="2" size="small">
          <a-descriptions-item label="ID">{{ report.id }}</a-descriptions-item>
          <a-descriptions-item label="操作类型">
            <a-tag :color="actionColor(report.action)">{{ report.action }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="目标类型">{{ report.target_type }}</a-descriptions-item>
          <a-descriptions-item label="目标ID">{{ report.target_id }}</a-descriptions-item>
          <a-descriptions-item label="操作人">{{ report.operator }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ report.target_version ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{ report.comment || '-' }}</a-descriptions-item>
          <a-descriptions-item label="时间">{{ report.operated_at }}</a-descriptions-item>
          <a-descriptions-item label="快照" :span="2" v-if="report.after_snapshot">
            <a-typography-paragraph code :content="report.after_snapshot" style="max-height:300px;overflow:auto;margin:0" />
          </a-descriptions-item>
        </a-descriptions>
      </template>
      <a-empty v-else-if="!loading" description="未找到日志记录" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { get } from '@/api/request'

const route = useRoute()
const loading = ref(false)
const report = ref<any>(null)

function actionColor(r: string): string {
  const map: Record<string, string> = { CREATE: 'blue', APPROVE: 'green', REJECT: 'red', PUBLISH: 'purple', SUBMIT: 'orange', WITHDRAW: 'default' }
  return map[r] || 'default'
}

onMounted(async () => {
  const id = route.params.traceId as string
  if (!id) return
  loading.value = true
  try {
    report.value = await get(`/logs/${id}`)
  } catch {
    message.info('日志查询接口暂不可用')
  } finally { loading.value = false }
})
</script>
