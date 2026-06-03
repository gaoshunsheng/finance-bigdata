<template>
  <div>
    <a-page-header :title="isEdit ? '编辑规则' : '新建规则'" @back="$router.back()" />
    <a-card>
      <a-form layout="vertical" style="max-width: 800px">
        <a-form-item label="规则名称" required>
          <a-input v-model:value="form.name" placeholder="请输入规则名称" />
        </a-form-item>
        <a-form-item label="规则描述">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="请输入规则描述" />
        </a-form-item>
        <a-form-item label="规则内容">
          <a-textarea v-model:value="form.content" :rows="12" placeholder="请输入规则 JSON 定义" style="font-family: monospace" />
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
