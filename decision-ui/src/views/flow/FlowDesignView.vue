<template>
  <div>
    <a-page-header :title="isEdit ? '设计决策流' : '新建决策流'" @back="$router.back()" />
    <div class="flow-designer">
      <div class="designer-toolbar">
        <a-space>
          <a-input v-model:value="flowName" placeholder="决策流名称" style="width: 240px" />
          <a-button type="primary">保存</a-button>
          <a-button>校验</a-button>
          <a-button>测试运行</a-button>
        </a-space>
      </div>
      <div class="designer-body">
        <!-- DAG 画布占位 - AntV X6 将在 Section 6.2.4 集成 -->
        <div ref="canvasRef" class="flow-canvas">
          <a-empty description="DAG 画布将在可视化编辑器阶段实现" />
        </div>
        <div class="designer-sidebar">
          <a-card title="节点类型" :bordered="false" size="small">
            <a-space direction="vertical" :size="8" style="width: 100%">
              <a-tag v-for="nt in nodeTypes" :key="nt.type" :color="nt.color" class="node-tag">
                {{ nt.label }}
              </a-tag>
            </a-space>
          </a-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isEdit = computed(() => !!route.params.id)

const flowName = ref('')
const canvasRef = ref<HTMLElement>()

const nodeTypes = [
  { type: 'START', label: '开始', color: 'green' },
  { type: 'DATA_PREP', label: '数据准备', color: 'blue' },
  { type: 'RULE_SET', label: '规则集', color: 'orange' },
  { type: 'SCORECARD', label: '评分卡', color: 'purple' },
  { type: 'MODEL', label: '模型', color: 'cyan' },
  { type: 'DECISION', label: '决策', color: 'red' },
  { type: 'AB_SPLIT', label: 'AB分流', color: 'geekblue' },
  { type: 'ACTION', label: '动作', color: 'magenta' },
  { type: 'END', label: '结束', color: 'default' },
]
</script>

<style scoped>
.flow-designer {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}
.designer-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}
.designer-body {
  display: flex;
  height: 600px;
}
.flow-canvas {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}
.designer-sidebar {
  width: 180px;
  border-left: 1px solid #f0f0f0;
  background: #fafafa;
  padding: 8px;
}
.node-tag {
  width: 100%;
  text-align: center;
  cursor: grab;
}
</style>
