<template>
  <div>
    <a-page-header :title="isEdit ? '编辑规则' : '新建规则'" @back="$router.back()" />

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="basic" tab="基本信息">
        <a-form layout="vertical" style="max-width: 800px">
          <a-form-item label="规则名称" required>
            <a-input v-model:value="form.name" placeholder="请输入规则名称" />
          </a-form-item>
          <a-form-item label="规则描述">
            <a-textarea v-model:value="form.description" :rows="3" placeholder="请输入规则描述" />
          </a-form-item>
          <a-form-item label="命中策略">
            <a-radio-group v-model:value="form.hitPolicy">
              <a-radio value="FIRST_HIT">首次命中</a-radio>
              <a-radio value="ALL">全部执行</a-radio>
              <a-radio value="PRIORITY">优先级</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="标签">
            <a-select v-model:value="form.tags" mode="tags" placeholder="输入标签后回车" style="width: 400px" />
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="visual" tab="可视化编辑">
        <a-alert message="通过条件树配置规则逻辑，支持 AND/OR/NOT 嵌套组合" type="info" show-icon style="margin-bottom: 12px" />
        <ConditionBuilder
          v-model="conditionTree"
          :fields="availableFields"
        />
        <ActionEditor v-model="actions" />
      </a-tab-pane>

      <a-tab-pane key="json" tab="JSON 编辑">
        <a-alert message="直接编辑规则 JSON 定义，适用于高级用户" type="warning" show-icon style="margin-bottom: 12px" />
        <a-textarea
          v-model:value="jsonContent"
          :rows="18"
          placeholder="请输入规则 JSON 定义"
          style="font-family: 'Fira Code', Consolas, monospace; font-size: 13px"
        />
      </a-tab-pane>
    </a-tabs>

    <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #f0f0f0">
      <a-space>
        <a-button type="primary" @click="handleSave">保存</a-button>
        <a-button @click="handleSaveAndTest">保存并测试</a-button>
        <a-button @click="$router.back()">取消</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ConditionBuilder from '@/components/rule/ConditionBuilder.vue'
import type { ConditionTreeNode, ConditionField } from '@/components/rule/ConditionBuilder.vue'
import ActionEditor from '@/components/rule/ActionEditor.vue'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('basic')

const form = reactive({
  name: '',
  description: '',
  hitPolicy: 'FIRST_HIT',
  tags: [] as string[],
})

const conditionTree = ref<ConditionTreeNode>({
  id: 'root',
  type: 'AND',
  children: [],
})

const actions = ref<Array<{ type: string; reason?: string; properties?: Record<string, any> }>>([])

const jsonContent = ref('')

/** 可用字段列表 (后续从变量引擎获取) */
const availableFields = ref<ConditionField[]>([
  { name: 'age', label: '年龄', type: 'NUMBER' },
  { name: 'income', label: '月收入', type: 'NUMBER' },
  { name: 'loanAmount', label: '贷款金额', type: 'NUMBER' },
  { name: 'overdueCount', label: '逾期次数', type: 'NUMBER' },
  { name: 'creditScore', label: '信用评分', type: 'NUMBER' },
  { name: 'employmentType', label: '就业类型', type: 'STRING' },
  { name: 'education', label: '学历', type: 'STRING' },
  { name: 'province', label: '省份', type: 'STRING' },
  { name: 'hasCreditCard', label: '有信用卡', type: 'BOOLEAN' },
  { name: 'hasHouse', label: '有房产', type: 'BOOLEAN' },
  { name: 'applyDate', label: '申请日期', type: 'DATE' },
])

function handleSave() {
  if (!form.name.trim()) {
    message.warning('请输入规则名称')
    return
  }
  message.success(isEdit.value ? '规则已更新' : '规则已创建')
  router.push('/rule')
}

function handleSaveAndTest() {
  if (!form.name.trim()) {
    message.warning('请输入规则名称')
    return
  }
  message.success('规则已保存，跳转到沙箱测试')
  router.push('/sandbox')
}
</script>
