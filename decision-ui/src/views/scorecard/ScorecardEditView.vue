<template>
  <div>
    <a-page-header :title="isEdit ? '编辑评分卡' : '新建评分卡'" @back="$router.back()" />
    <a-card>
      <a-form layout="vertical" style="max-width: 800px">
        <a-form-item label="评分卡名称" required>
          <a-input v-model:value="form.name" placeholder="请输入评分卡名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="请输入描述" />
        </a-form-item>
        <a-form-item label="评分卡定义">
          <a-textarea v-model:value="form.content" :rows="12" placeholder="请输入评分卡 JSON 定义" style="font-family: monospace" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary">保存</a-button>
            <a-button @click="$router.back()">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isEdit = computed(() => !!route.params.id)

const form = reactive({
  name: '',
  description: '',
  content: '',
})
</script>
