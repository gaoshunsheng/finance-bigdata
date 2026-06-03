<template>
  <div>
    <a-page-header title="沙箱测试" />
    <a-row :gutter="16">
      <a-col :span="12">
        <a-card title="输入数据">
          <a-form layout="vertical">
            <a-form-item label="目标类型">
              <a-select v-model:value="form.targetType" style="width: 200px">
                <a-select-option value="RULE">规则</a-select-option>
                <a-select-option value="SCORECARD">评分卡</a-select-option>
                <a-select-option value="TABLE">决策表</a-select-option>
                <a-select-option value="TREE">决策树</a-select-option>
                <a-select-option value="FLOW">决策流</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="目标ID">
              <a-input v-model:value="form.targetId" placeholder="请输入目标ID" />
            </a-form-item>
            <a-form-item label="JSON 输入">
              <a-textarea v-model:value="form.inputData" :rows="10" placeholder='{"age": 25, "income": 50000}' style="font-family: monospace" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary">执行测试</a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="执行结果">
          <a-empty description="执行结果将在此展示" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

const form = reactive({
  targetType: 'RULE',
  targetId: '',
  inputData: '{\n  \n}',
})
</script>
