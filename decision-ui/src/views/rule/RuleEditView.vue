<template>
  <div>
    <a-page-header :title="isEdit ? '编辑规则' : '新建规则'" @back="$router.back()" />
    <a-spin :spinning="loading">
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
            <a-form-item label="规则类型">
              <a-radio-group v-model:value="form.isRuleSet" @change="onTypeChange">
                <a-radio :value="true">规则集 (多规则)</a-radio>
                <a-radio :value="false">单条规则</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="visual" tab="可视化编辑">
          <!-- 规则集模式: 列出所有子规则 -->
          <template v-if="form.isRuleSet">
            <a-card v-for="(rule, idx) in ruleEditors" :key="idx" size="small" :title="`规则 ${idx+1}: ${rule.name || rule.ruleId || ''}`" style="margin-bottom:12px">
              <template #extra>
                <a-space>
                  <a-button size="small" @click="moveRuleUp(idx)" :disabled="idx===0">上移</a-button>
                  <a-button size="small" @click="moveRuleDown(idx)" :disabled="idx===ruleEditors.length-1">下移</a-button>
                  <a-popconfirm title="删除此规则?" @confirm="removeRule(idx)">
                    <a-button size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
              <a-form layout="vertical" size="small">
                <a-row :gutter="8">
                  <a-col :span="8"><a-form-item label="规则ID"><a-input v-model:value="rule.ruleId" size="small" /></a-form-item></a-col>
                  <a-col :span="8"><a-form-item label="名称"><a-input v-model:value="rule.name" size="small" /></a-form-item></a-col>
                  <a-col :span="8"><a-form-item label="优先级"><a-input-number v-model:value="rule.priority" size="small" :min="0" style="width:100%" /></a-form-item></a-col>
                </a-row>
              </a-form>
              <ConditionBuilder v-model="rule._tree" :fields="availableFields" :grouped-fields="conditionGroupedOptions" />
              <ActionEditor v-model="rule._actions" />
            </a-card>
            <a-button type="dashed" block @click="addRule" style="margin-top:8px"><template #icon><PlusOutlined /></template> 添加规则</a-button>
          </template>

          <!-- 单规则模式 -->
          <template v-else>
            <ConditionBuilder v-model="singleRuleTree" :fields="availableFields" :grouped-fields="conditionGroupedOptions" />
            <ActionEditor v-model="singleRuleActions" />
          </template>
        </a-tab-pane>

        <a-tab-pane key="json" tab="JSON 编辑">
          <a-alert message="直接编辑规则 JSON，可视化编辑的修改会自动同步到此" type="warning" show-icon style="margin-bottom:12px" />
          <a-textarea v-model:value="jsonContent" :rows="20" style="font-family:monospace;font-size:13px" />
        </a-tab-pane>
      </a-tabs>

      <div style="margin-top:16px;padding-top:16px;border-top:1px solid #f0f0f0">
        <a-space>
          <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import ConditionBuilder from '@/components/rule/ConditionBuilder.vue'
import type { ConditionTreeNode, ConditionField } from '@/components/rule/ConditionBuilder.vue'
import ActionEditor from '@/components/rule/ActionEditor.vue'
import { getRule, createRule, updateRule } from '@/api/admin'
import { useVariableOptions } from '@/composables/useVariableOptions'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const activeTab = ref('basic')
const loading = ref(false)
const saving = ref(false)

const { variables, groupedOptions, load: loadVariables, getFieldType } = useVariableOptions()

const form = reactive({ name: '', description: '', hitPolicy: 'FIRST_HIT' as string, isRuleSet: true })
const jsonContent = ref('{}')

const availableFields = computed<ConditionField[]>(() =>
  variables.value.map(v => ({
    name: v.value,
    label: v.label,
    type: getFieldType(v.value) as 'NUMBER' | 'STRING' | 'BOOLEAN',
  }))
)

const conditionGroupedOptions = computed(() =>
  groupedOptions.value.map(g => ({
    layer: g.layer,
    label: g.label,
    options: g.options.map(o => ({ value: o.value, label: o.label, dataType: o.dataType })),
  }))
)

// Rule editors for rule set mode
interface RuleEditor {
  ruleId: string; name: string; priority: number; _tree: ConditionTreeNode; _actions: any[]
}
const ruleEditors = ref<RuleEditor[]>([])
const singleRuleTree = ref<ConditionTreeNode>({ id: 'root', type: 'AND', children: [] })
const singleRuleActions = ref<any[]>([])

function addRule() {
  ruleEditors.value.push({
    ruleId: `R_${Date.now().toString(36).toUpperCase()}`,
    name: '',
    priority: 100 - ruleEditors.value.length * 10,
    _tree: { id: `rt_${Date.now()}`, type: 'AND', children: [] },
    _actions: [],
  })
}
function removeRule(idx: number) { ruleEditors.value.splice(idx, 1) }
function moveRuleUp(idx: number) { if (idx>0) { const t=ruleEditors.value[idx]; ruleEditors.value[idx]=ruleEditors.value[idx-1]; ruleEditors.value[idx-1]=t } }
function moveRuleDown(idx: number) { if (idx<ruleEditors.value.length-1) { const t=ruleEditors.value[idx]; ruleEditors.value[idx]=ruleEditors.value[idx+1]; ruleEditors.value[idx+1]=t } }
function onTypeChange() {
  if (form.isRuleSet && ruleEditors.value.length===0) addRule()
}

// --- Engine JSON ↔ Visual format converters ---

function newAndNode(): ConditionTreeNode {
  return { id: `nd_${Date.now()}`, type: 'AND', children: [] }
}

function engineCondToTreeNode(cond: any): ConditionTreeNode {
  if (!cond || Object.keys(cond).length === 0) return newAndNode()
  // Logical: {operator, operands}
  if (cond.operator) {
    return {
      id: `lg_${Date.now()}`,
      type: cond.operator as any,
      children: (cond.operands || []).map((op: any) => engineCondToTreeNode(op)),
    }
  }
  // Comparison: {field, op, value}
  if (cond.field) {
    const v = cond.value
    // Ensure boolean values are actual booleans (JSON.parse produces correct types)
    let valueType = 'STRING'
    let value = v
    if (typeof v === 'boolean') { valueType = 'BOOLEAN'; value = v }
    else if (typeof v === 'number') { valueType = 'NUMBER'; value = v }
    else if (v === null || v === undefined) { valueType = 'STRING'; value = '' }
    else { valueType = 'STRING'; value = String(v) }

    const root = newAndNode()
    root.children = [{
      id: `cmp_${Date.now()}`,
      type: 'CONDITION',
      condition: { field: cond.field, operator: cond.op, value, valueType },
    }]
    return root
  }
  return newAndNode()
}

function treeNodeToEngineCond(node: ConditionTreeNode): any {
  if (!node) return null
  if (node.type === 'CONDITION' && node.condition) {
    return { field: node.condition.field, op: node.condition.operator, value: node.condition.value }
  }
  if ((node.type==='AND' || node.type==='OR' || node.type==='NOT') && node.children?.length) {
    return { operator: node.type, operands: node.children.map(c => treeNodeToEngineCond(c)).filter(Boolean) }
  }
  return null
}

// Watch visual editors → sync JSON content
watch([ruleEditors, singleRuleTree, singleRuleActions, () => form.hitPolicy, () => form.isRuleSet], () => {
  syncToJson()
}, { deep: true })

function syncToJson() {
  if (form.isRuleSet) {
    const rules = ruleEditors.value.map(r => ({
      ruleId: r.ruleId, name: r.name || undefined, priority: r.priority,
      conditions: treeNodeToEngineCond(r._tree) || {},
      actions: r._actions.map((a: any) => ({ type: a.type, reason: a.reason || undefined, code: a.code || undefined })),
    }))
    jsonContent.value = JSON.stringify({ ruleSetId: route.params.id as string || '', name: form.name, version: 1, hitPolicy: form.hitPolicy, rules }, null, 2)
  } else {
    const cond = treeNodeToEngineCond(singleRuleTree.value)
    const actions = singleRuleActions.value.map((a: any) => ({ type: a.type, reason: a.reason || undefined, code: a.code || undefined }))
    jsonContent.value = JSON.stringify({ ruleId: route.params.id as string || '', name: form.name, priority: 100, conditions: cond || {}, actions }, null, 2)
  }
}

function loadFromContent(content: any) {
  if (content.rules && Array.isArray(content.rules)) {
    // RuleSet
    form.isRuleSet = true
    form.hitPolicy = content.hitPolicy || 'FIRST_HIT'
    ruleEditors.value = content.rules.map((r: any) => ({
      ruleId: r.ruleId || `R_${Date.now().toString(36)}`,
      name: r.name || '',
      priority: r.priority || 100,
      _tree: engineCondToTreeNode(r.conditions),
      _actions: (r.actions || []).map((a: any) => ({ type: a.type, reason: a.reason || '', code: a.code || '' })),
    }))
  } else if (content.ruleId || content.conditions) {
    // Single rule
    form.isRuleSet = false
    singleRuleTree.value = engineCondToTreeNode(content.conditions)
    singleRuleActions.value = (content.actions || []).map((a: any) => ({ type: a.type, reason: a.reason || '', code: a.code || '' }))
  } else {
    form.isRuleSet = true
    addRule()
  }
}

onMounted(async () => {
  loadVariables()
  if (isEdit.value) {
    loading.value = true
    try {
      const rule = await getRule(route.params.id as string) as any
      form.name = rule.name || ''
      form.description = rule.description || ''
      const content = typeof rule.content === 'string' ? JSON.parse(rule.content) : (rule.content || {})
      loadFromContent(content)
      syncToJson()
    } catch { message.error('加载失败') }
    finally { loading.value = false }
  } else {
    form.isRuleSet = true
    addRule()
    syncToJson()
  }
})

async function handleSave() {
  if (!form.name.trim()) { message.warning('请输入名称'); return }
  saving.value = true
  try {
    let content: any
    try { content = JSON.parse(jsonContent.value) } catch { content = {} }
    content.hitPolicy = form.hitPolicy
    const payload = { name: form.name, description: form.description, content: JSON.stringify(content) }
    if (isEdit.value) { await updateRule(route.params.id as string, payload); message.success('已更新') }
    else { await createRule(payload); message.success('已创建') }
    router.push('/rule')
  } catch (err: any) { message.error(err?.message || '保存失败') }
  finally { saving.value = false }
}
</script>
