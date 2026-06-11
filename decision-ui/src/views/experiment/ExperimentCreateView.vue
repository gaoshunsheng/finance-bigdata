<template>
  <div>
    <a-page-header title="新建实验" @back="$router.back()" />
    <a-form :model="form" :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }" style="max-width: 800px">
      <a-form-item label="实验名称" required>
        <a-input v-model:value="form.name" placeholder="请输入实验名称" />
      </a-form-item>
      <a-form-item label="流量键" required>
        <a-input v-model:value="form.trafficKey" placeholder="如 channel、region" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="form.description" :rows="2" placeholder="实验描述（可选）" />
      </a-form-item>
      <a-form-item label="终止条件">
        <a-input v-model:value="form.terminationCondition" placeholder="如 maxDuration=7d" />
      </a-form-item>

      <a-divider>实验分组</a-divider>
      <div v-for="(group, idx) in form.groups" :key="idx" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
        <a-input v-model:value="group.name" placeholder="分组名称" style="width:160px" />
        <a-input-number v-model:value="group.ratio" :min="0" :max="100" :step="5" placeholder="流量%" style="width:120px" addon-after="%" />
        <a-input v-model:value="group.strategyId" placeholder="绑定策略ID" style="width:200px" />
        <a-checkbox v-model:checked="group.isControl">对照组</a-checkbox>
        <a-button type="link" danger @click="form.groups.splice(idx, 1)" :disabled="form.groups.length <= 1">删除</a-button>
      </div>
      <a-button type="dashed" block @click="addGroup" style="margin-bottom:16px">+ 添加分组</a-button>

      <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
        <a-space>
          <a-button type="primary" :loading="saving" @click="handleSave">创建实验</a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { createExperiment } from '@/api/admin'

const router = useRouter()
const saving = ref(false)

const form = reactive({
  name: '',
  trafficKey: '',
  description: '',
  terminationCondition: '',
  groups: [
    { name: '对照组', ratio: 50, strategyId: '', isControl: true },
    { name: '实验组', ratio: 50, strategyId: '', isControl: false },
  ] as { name: string; ratio: number; strategyId: string; isControl: boolean }[],
})

function addGroup() {
  form.groups.push({ name: `实验组${form.groups.length}`, ratio: 0, strategyId: '', isControl: false })
}

async function handleSave() {
  if (!form.name.trim()) { message.warning('请输入实验名称'); return }
  if (!form.trafficKey.trim()) { message.warning('请输入流量键'); return }

  const totalRatio = form.groups.reduce((s, g) => s + g.ratio, 0)
  if (totalRatio !== 100) { message.warning(`分组流量比例之和必须为 100%，当前 ${totalRatio}%`); return }

  saving.value = true
  try {
    const content = JSON.stringify({
      trafficKey: form.trafficKey,
      groups: form.groups,
      enabled: false,
      terminationCondition: form.terminationCondition || undefined,
    })
    await createExperiment({ name: form.name, content, description: form.description } as any)
    message.success('创建成功')
    router.push('/experiment')
  } catch {
    message.error('创建失败')
  } finally { saving.value = false }
}
</script>
