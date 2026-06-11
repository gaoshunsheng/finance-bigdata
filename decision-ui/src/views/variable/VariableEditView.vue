<template>
  <div>
    <a-page-header :title="isEdit ? '编辑变量' : '新建变量'" @back="$router.back()" />
    <a-spin :spinning="loading">
      <a-form layout="vertical" style="max-width: 700px">
        <a-form-item label="变量ID" required>
          <a-input v-model:value="form.id" placeholder="var_xxx" :disabled="isEdit" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="变量显示名称" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="层级"><a-select v-model:value="form.layer">
            <a-select-option value="INPUT">L0 输入</a-select-option>
            <a-select-option value="EXTERNAL">L1 外部</a-select-option>
            <a-select-option value="CACHED">L2 缓存</a-select-option>
            <a-select-option value="DERIVED">L3 派生</a-select-option>
          </a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="数据类型"><a-select v-model:value="form.dataType">
            <a-select-option value="STRING">字符串</a-select-option>
            <a-select-option value="INTEGER">整数</a-select-option>
            <a-select-option value="DECIMAL">数值</a-select-option>
            <a-select-option value="BOOLEAN">布尔</a-select-option>
            <a-select-option value="DATE">日期</a-select-option>
          </a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="分类"><a-select v-model:value="form.category">
            <a-select-option value="credit">征信</a-select-option>
            <a-select-option value="commerce">商业</a-select-option>
            <a-select-option value="telecom">通讯</a-select-option>
            <a-select-option value="internal">内部</a-select-option>
            <a-select-option value="derived">衍生</a-select-option>
          </a-select></a-form-item></a-col>
        </a-row>
        <a-form-item v-if="form.layer==='DERIVED'" label="表达式"><a-textarea v-model:value="form.expression" :rows="3" placeholder="Aviator 表达式，如: age < 30 ? true : false" /></a-form-item>
        <a-form-item v-if="form.layer==='DERIVED'" label="依赖变量"><a-select v-model:value="form.dependencies" mode="tags" placeholder="输入依赖的 varId 后回车" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="form.description" :rows="2" /></a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
            <a-button @click="$router.back()">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getVariable, createVariable, updateVariable } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  id: '', name: '', layer: 'INPUT', dataType: 'STRING', category: 'credit',
  expression: '', dependencies: [] as string[], description: '',
})

onMounted(async () => {
  if (isEdit.value) {
    loading.value = true
    try {
      const v = await getVariable(route.params.id as string) as any
      const c = typeof v.content === 'string' ? JSON.parse(v.content) : (v.content || {})
      form.id = c.varId || v.id || ''
      form.name = c.name || v.name || ''
      form.layer = c.layer || 'INPUT'
      form.dataType = c.dataType || 'STRING'
      form.category = c.category || 'credit'
      form.expression = c.expression || ''
      form.dependencies = c.dependencies || []
      form.description = c.description || v.description || ''
    } catch { message.error('加载失败') }
    finally { loading.value = false }
  }
})

async function handleSave() {
  if (!form.id.trim()) { message.warning('请输入变量ID'); return }
  saving.value = true
  try {
    const content: any = { varId: form.id, name: form.name, layer: form.layer, dataType: form.dataType, category: form.category, description: form.description, version: 1 }
    if (form.layer === 'DERIVED') { content.expression = form.expression; content.dependencies = form.dependencies }
    const payload = { name: form.name, description: form.description, content: JSON.stringify(content) }
    if (isEdit.value) { await updateVariable(route.params.id as string, payload); message.success('已更新') }
    else { await createVariable(payload as any); message.success('已创建') }
    router.push('/variable')
  } catch (err: any) { message.error(err?.message || '保存失败') }
  finally { saving.value = false }
}
</script>
