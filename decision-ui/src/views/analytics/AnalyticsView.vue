<template>
  <div>
    <a-page-header title="分析看板">
      <template #extra><a-range-picker v-model:value="dateRange" @change="loadStats" /></template>
    </a-page-header>

    <a-alert v-if="esError" message="ES 暂不可用，请先运行 decision_runner.py 生成决策日志" type="info" show-icon style="margin-bottom:12px" closable />

    <a-row :gutter="[16,16]">
      <a-col :span="6"><a-card size="small"><a-statistic title="今日决策量" :value="stats.totalDecisions" :value-style="{color:'#1677ff'}"><template #prefix><ThunderboltOutlined /></template></a-statistic></a-card></a-col>
      <a-col :span="6"><a-card size="small"><a-statistic title="通过率" :value="stats.passRate" suffix="%" :value-style="{color:'#52c41a'}"><template #prefix><CheckCircleOutlined /></template></a-statistic></a-card></a-col>
      <a-col :span="6"><a-card size="small"><a-statistic title="拒绝率" :value="stats.rejectRate" suffix="%" :value-style="{color:'#ff4d4f'}"><template #prefix><CloseCircleOutlined /></template></a-statistic></a-card></a-col>
      <a-col :span="6"><a-card size="small"><a-statistic title="P99 耗时" :value="stats.p99Latency" suffix="ms" :value-style="{color:'#fa8c16'}"><template #prefix><ClockCircleOutlined /></template></a-statistic></a-card></a-col>
    </a-row>

    <a-row :gutter="[16,16]" style="margin-top:16px">
      <a-col :span="16"><a-card title="决策趋势 (近7天)" size="small"><div ref="trendRef" style="height:320px"></div></a-card></a-col>
      <a-col :span="8"><a-card title="决策结果分布" size="small"><div ref="pieRef" style="height:320px"></div></a-card></a-col>
    </a-row>

    <a-row :gutter="[16,16]" style="margin-top:16px">
      <a-col :span="12"><a-card title="规则命中率 Top 10" size="small"><div ref="ruleRef" style="height:300px"></div></a-card></a-col>
      <a-col :span="12"><a-card title="评分分布" size="small"><div ref="scoreRef" style="height:300px"></div></a-card></a-col>
    </a-row>

    <a-card title="渠道分析" size="small" style="margin-top:16px">
      <a-table :columns="chCols" :data-source="channelData" :pagination="false" size="small" row-key="channel">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key==='passRate'"><a-progress :percent="record.passRate" size="small" :stroke-color="record.passRate>70?'#52c41a':'#fa8c16'" /></template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { ThunderboltOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts'

const dateRange = ref<any>(null)
const esError = ref(false)
const stats = reactive({ totalDecisions:0, passRate:0, rejectRate:0, p99Latency:0 })

const trendRef=ref<HTMLElement>(), pieRef=ref<HTMLElement>(), ruleRef=ref<HTMLElement>(), scoreRef=ref<HTMLElement>()
let charts: echarts.ECharts[] = []

const chCols = [
  { title:'渠道', dataIndex:'channel' }, { title:'决策量', dataIndex:'total', width:100 },
  { title:'通过率', key:'passRate', width:200 }, { title:'拒绝率', dataIndex:'rejectRate', width:100 },
  { title:'人工率', dataIndex:'manualRate', width:100 }, { title:'平均耗时', dataIndex:'avgDuration', width:100 },
]
const channelData = [
  { channel:'APP', total:0, passRate:0, rejectRate:0, manualRate:0, avgDuration:'-' },
  { channel:'WEB', total:0, passRate:0, rejectRate:0, manualRate:0, avgDuration:'-' },
  { channel:'API', total:0, passRate:0, rejectRate:0, manualRate:0, avgDuration:'-' },
]

async function loadStats() {
  try {
    const { get } = await import('@/api/request')
    const d = await get('/analytics/overview') as any
    if (d) { stats.totalDecisions=d.total||0; stats.passRate=d.passRate||0; stats.rejectRate=d.rejectRate||0; stats.p99Latency=d.p99Latency||0 }
    esError.value = false
  } catch { esError.value = true }
}

function initCharts() {
  if (trendRef.value) {
    const c = echarts.init(trendRef.value); charts.push(c)
    c.setOption({ tooltip:{trigger:'axis'}, legend:{data:['通过','拒绝','人工']}, grid:{left:50,right:20,top:40,bottom:30}, xAxis:{type:'category',data:['周一','周二','周三','周四','周五','周六','周日']}, yAxis:{type:'value'}, series:[
      {name:'通过',type:'line',smooth:true,data:[0,0,0,0,0,0,0],itemStyle:{color:'#52c41a'}},
      {name:'拒绝',type:'line',smooth:true,data:[0,0,0,0,0,0,0],itemStyle:{color:'#ff4d4f'}},
      {name:'人工',type:'line',smooth:true,data:[0,0,0,0,0,0,0],itemStyle:{color:'#fa8c16'}},
    ]})
  }
  if (pieRef.value) {
    const c = echarts.init(pieRef.value); charts.push(c)
    c.setOption({ tooltip:{trigger:'item'}, legend:{bottom:0}, series:[{type:'pie',radius:['40%','65%'],data:[
      {value:0,name:'通过',itemStyle:{color:'#52c41a'}},{value:0,name:'拒绝',itemStyle:{color:'#ff4d4f'}},{value:0,name:'人工',itemStyle:{color:'#fa8c16'}}
    ]}]})
  }
  if (ruleRef.value) {
    const c = echarts.init(ruleRef.value); charts.push(c)
    c.setOption({ tooltip:{trigger:'axis',axisPointer:{type:'shadow'}}, grid:{left:100,right:20,top:10,bottom:30}, xAxis:{type:'value',max:100}, yAxis:{type:'category',data:[]}, series:[{type:'bar',data:[]}] })
  }
  if (scoreRef.value) {
    const c = echarts.init(scoreRef.value); charts.push(c)
    c.setOption({ tooltip:{trigger:'axis'}, grid:{left:50,right:20,top:20,bottom:40}, xAxis:{type:'category',data:['<500','500-550','550-650','650-750','≥750']}, yAxis:{type:'value'}, series:[{type:'bar',data:[0,0,0,0,0],itemStyle:{color:new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#1677ff'},{offset:1,color:'#69b1ff'}])}}] })
  }
}

function handleResize() { charts.forEach(c=>c.resize()) }
onMounted(() => { loadStats(); initCharts(); window.addEventListener('resize',handleResize) })
onBeforeUnmount(() => { charts.forEach(c=>c.dispose()); window.removeEventListener('resize',handleResize) })
</script>
