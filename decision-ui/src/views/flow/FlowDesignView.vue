<template>
  <div>
    <a-page-header :title="isEdit ? '设计决策流' : '新建决策流'" @back="$router.back()" />

    <a-form layout="inline" style="margin-bottom: 12px">
      <a-form-item label="决策流名称">
        <a-input v-model:value="form.name" placeholder="请输入决策流名称" style="width: 240px" />
      </a-form-item>
      <a-form-item label="描述">
        <a-input v-model:value="form.description" placeholder="请输入描述" style="width: 300px" />
      </a-form-item>
    </a-form>

    <FlowDesigner v-model="flowModel" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import FlowDesigner from '@/components/flow/FlowDesigner.vue'
import type { FlowModel } from '@/types/flow'

const route = useRoute()
const isEdit = computed(() => !!route.params.id)

const form = reactive({ name: '', description: '' })
const flowModel = ref<FlowModel>({ nodes: [], edges: [] })
</script>
