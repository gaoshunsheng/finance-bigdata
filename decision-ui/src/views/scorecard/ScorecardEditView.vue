<template>
  <div>
    <a-page-header :title="isEdit ? '编辑评分卡' : '新建评分卡'" @back="$router.back()" />

    <a-form layout="vertical" style="max-width: 1000px">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="评分卡名称" required>
            <a-input v-model:value="form.name" placeholder="请输入评分卡名称" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="描述">
            <a-input v-model:value="form.description" placeholder="请输入描述" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="visual" tab="可视化编辑">
        <ScorecardEditor v-model="scorecardModel" />
      </a-tab-pane>
      <a-tab-pane key="json" tab="JSON 编辑">
        <a-textarea
          v-model:value="jsonContent"
          :rows="18"
          placeholder="评分卡 JSON 定义"
          style="font-family: 'Fira Code', Consolas, monospace; font-size: 13px"
        />
      </a-tab-pane>
    </a-tabs>

    <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #f0f0f0">
      <a-space>
        <a-button type="primary" @click="handleSave">保存</a-button>
        <a-button @click="$router.back()">取消</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ScorecardEditor from '@/components/scorecard/ScorecardEditor.vue'
import type { ScorecardModel } from '@/types/scorecard'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('visual')

const form = reactive({ name: '', description: '' })
const scorecardModel = reactive<ScorecardModel>({
  initialScore: 600,
  characteristics: [],
  cutoff: 500,
})

const jsonContent = ref('{}')
watch(scorecardModel, (v) => {
  jsonContent.value = JSON.stringify(v, null, 2)
}, { deep: true })

function handleSave() {
  if (!form.name.trim()) {
    message.warning('请输入评分卡名称')
    return
  }
  message.success(isEdit.value ? '评分卡已更新' : '评分卡已创建')
  router.push('/scorecard')
}
</script>
