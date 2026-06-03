<template>
  <div class="scorecard-editor">
    <!-- 基础配置 -->
    <a-row :gutter="16">
      <a-col :span="8">
        <a-form-item label="初始分数">
          <a-input-number v-model:value="model.initialScore" :min="-1000" :max="1000" style="width: 100%" />
        </a-form-item>
      </a-col>
      <a-col :span="8">
        <a-form-item label="Cutoff 阈值">
          <a-input-number v-model:value="model.cutoff" :min="-1000" :max="1000" style="width: 100%" />
        </a-form-item>
      </a-col>
      <a-col :span="8">
        <a-form-item label="特征数量">
          <a-tag color="blue">{{ model.characteristics.length }} 个特征</a-tag>
        </a-form-item>
      </a-col>
    </a-row>

    <!-- 特征列表 -->
    <div class="characteristic-section">
      <div class="section-header">
        <span class="section-title">特征列表</span>
        <a-button type="primary" size="small" @click="addCharacteristic">
          <template #icon><PlusOutlined /></template> 添加特征
        </a-button>
      </div>

      <a-collapse v-model:activeKey="activeChars" accordion>
        <a-collapse-panel
          v-for="(char, cIdx) in model.characteristics"
          :key="`char-${cIdx}`"
          :header="char.name || `特征 ${cIdx + 1}`"
        >
          <template #extra>
            <a-space @click.stop>
              <a-button size="small" type="text" danger @click="removeCharacteristic(cIdx)">
                <DeleteOutlined />
              </a-button>
            </a-space>
          </template>

          <a-row :gutter="16" style="margin-bottom: 12px">
            <a-col :span="8">
              <a-form-item label="特征名称">
                <a-input v-model:value="char.name" placeholder="如: 年龄" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="关联字段">
                <a-input v-model:value="char.field" placeholder="如: age" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-button type="dashed" size="small" @click="addBin(char)" style="margin-top: 28px">
                <PlusOutlined /> 添加分箱
              </a-button>
            </a-col>
          </a-row>

          <!-- 分箱表格 -->
          <a-table
            :columns="binColumns"
            :data-source="char.bins"
            :pagination="false"
            size="small"
            bordered
            row-key="_idx"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'label'">
                <a-input v-model:value="record.label" placeholder="标签" size="small" />
              </template>
              <template v-else-if="column.key === 'range'">
                <a-space size="small">
                  <a-input-number v-model:value="record.from" placeholder="从" size="small" style="width: 80px" />
                  <span>~</span>
                  <a-input-number v-model:value="record.to" placeholder="到" size="small" style="width: 80px" />
                </a-space>
              </template>
              <template v-else-if="column.key === 'score'">
                <a-input-number v-model:value="record.score" :min="-500" :max="500" size="small" style="width: 80px" />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button size="small" type="text" danger @click="removeBin(char, index)">
                  <DeleteOutlined />
                </a-button>
              </template>
            </template>
          </a-table>
        </a-collapse-panel>
      </a-collapse>
    </div>

    <!-- 实时评分预览 -->
    <a-card title="评分预览" size="small" style="margin-top: 16px">
      <a-row :gutter="16">
        <a-col :span="12">
          <div class="preview-input">
            <div v-for="char in model.characteristics" :key="char.field" class="preview-field">
              <label>{{ char.name || char.field }}:</label>
              <a-input-number
                v-model:value="previewValues[char.field]"
                :placeholder="`输入 ${char.name}`"
                style="width: 150px"
                size="small"
                @change="calcPreview"
              />
            </div>
          </div>
        </a-col>
        <a-col :span="12">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="初始分">{{ model.initialScore }}</a-descriptions-item>
            <a-descriptions-item v-for="char in model.characteristics" :key="char.field" :label="char.name">
              {{ previewBinScore[char.field] ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="总分">
              <span :style="{ color: previewTotal >= (model.cutoff || 0) ? '#52c41a' : '#ff4d4f', fontWeight: 600 }">
                {{ previewTotal }} {{ previewTotal >= (model.cutoff || 0) ? '✓ 通过' : '✗ 未通过' }}
              </span>
            </a-descriptions-item>
          </a-descriptions>
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { Characteristic, Bin, ScorecardModel } from '@/types/scorecard'

const props = defineProps<{ modelValue: ScorecardModel }>()
const emit = defineEmits<{ (e: 'update:modelValue', val: ScorecardModel): void }>()

const model = reactive<ScorecardModel>(
  props.modelValue || {
    initialScore: 600,
    characteristics: [],
    cutoff: 500,
  },
)

watch(() => props.modelValue, (v) => Object.assign(model, v), { deep: true })
watch(model, (v) => emit('update:modelValue', v), { deep: true })

const activeChars = ref<string[]>([])
const previewValues = reactive<Record<string, number>>({})
const previewBinScore = reactive<Record<string, number>>({})

const binColumns = [
  { title: '标签', key: 'label', width: 120 },
  { title: '范围', key: 'range', width: 200 },
  { title: '分数', key: 'score', width: 100 },
  { title: '操作', key: 'action', width: 60 },
]

function addCharacteristic() {
  const idx = model.characteristics.length + 1
  model.characteristics.push({
    name: `特征${idx}`,
    field: `field_${idx}`,
    bins: [{ from: 0, to: 100, score: 0, label: '默认' }],
  })
  activeChars.value = [`char-${model.characteristics.length - 1}`]
}

function removeCharacteristic(idx: number) {
  model.characteristics.splice(idx, 1)
}

function addBin(char: Characteristic) {
  char.bins.push({ from: 0, to: 0, score: 0, label: '' })
}

function removeBin(char: Characteristic, idx: number) {
  char.bins.splice(idx, 1)
}

/** 实时评分计算 */
function calcPreview() {
  let total = model.initialScore
  for (const char of model.characteristics) {
    const val = previewValues[char.field]
    if (val === undefined || val === null) {
      previewBinScore[char.field] = 0
      continue
    }
    const matchedBin = char.bins.find(b => {
      const fromOk = b.from === undefined || b.from === null || val >= b.from
      const toOk = b.to === undefined || b.to === null || val < b.to
      return fromOk && toOk
    })
    const score = matchedBin?.score || 0
    previewBinScore[char.field] = score
    total += score
  }
  // Store total in a way that's accessible
  previewBinScore.__total = total
}

const previewTotal = ref(0)
watch(() => previewBinScore.__total, (v) => { previewTotal.value = v || model.initialScore })
</script>

<style scoped>
.scorecard-editor {
  max-width: 1000px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  font-weight: 600;
  font-size: 14px;
}
.preview-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.preview-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-field label {
  min-width: 80px;
  font-size: 13px;
  text-align: right;
}
</style>
