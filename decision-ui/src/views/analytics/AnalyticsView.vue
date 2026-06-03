<template>
  <div>
    <a-page-header title="分析看板">
      <template #extra>
        <a-range-picker v-model:value="dateRange" @change="onDateChange" />
      </template>
    </a-page-header>

    <!-- 顶部统计卡片 -->
    <a-row :gutter="[16, 16]">
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="今日决策量" :value="12580" :value-style="{ color: '#1677ff' }">
            <template #prefix><ThunderboltOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="通过率" value="72.5" suffix="%" :value-style="{ color: '#52c41a' }">
            <template #prefix><CheckCircleOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="拒绝率" value="18.3" suffix="%" :value-style="{ color: '#ff4d4f' }">
            <template #prefix><CloseCircleOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small">
          <a-statistic title="P99 耗时" :value="186" suffix="ms" :value-style="{ color: '#fa8c16' }">
            <template #prefix><ClockCircleOutlined /></template>
          </a-statistic>
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :span="16">
        <a-card title="决策趋势 (近7天)" size="small">
          <div ref="trendChartRef" style="height: 320px"></div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="决策结果分布" size="small">
          <div ref="pieChartRef" style="height: 320px"></div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :span="12">
        <a-card title="规则命中率 Top 10" size="small">
          <div ref="ruleHitChartRef" style="height: 300px"></div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="评分卡得分分布" size="small">
          <div ref="scoreChartRef" style="height: 300px"></div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 渠道分析 -->
    <a-card title="渠道分析" size="small" style="margin-top: 16px">
      <a-table :columns="channelColumns" :data-source="channelData" :pagination="false" size="small" row-key="channel">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'passRate'">
            <a-progress :percent="record.passRate" :size="'small'" :stroke-color="record.passRate > 70 ? '#52c41a' : '#fa8c16'" />
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import {
  ThunderboltOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'
import * as echarts from 'echarts'

const dateRange = ref<any>(null)

const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
const ruleHitChartRef = ref<HTMLElement>()
const scoreChartRef = ref<HTMLElement>()

let charts: echarts.ECharts[] = []

function onDateChange() {
  // 重新加载数据
}

// 渠道分析表格
const channelColumns = [
  { title: '渠道', dataIndex: 'channel', key: 'channel' },
  { title: '决策量', dataIndex: 'total', key: 'total', width: 100 },
  { title: '通过率', key: 'passRate', width: 200 },
  { title: '拒绝率', dataIndex: 'rejectRate', key: 'rejectRate', width: 100 },
  { title: '人工率', dataIndex: 'manualRate', key: 'manualRate', width: 100 },
  { title: '平均耗时', dataIndex: 'avgDuration', key: 'avgDuration', width: 100 },
]

const channelData = [
  { channel: 'APP', total: 8520, passRate: 75.2, rejectRate: 16.8, manualRate: 8.0, avgDuration: '142ms' },
  { channel: 'WEB', total: 2460, passRate: 68.5, rejectRate: 22.1, manualRate: 9.4, avgDuration: '198ms' },
  { channel: 'API', total: 1200, passRate: 81.3, rejectRate: 14.2, manualRate: 4.5, avgDuration: '89ms' },
  { channel: '线下', total: 400, passRate: 45.0, rejectRate: 30.0, manualRate: 25.0, avgDuration: '350ms' },
]

onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  charts.forEach(c => c.dispose())
  window.removeEventListener('resize', handleResize)
})

function handleResize() {
  charts.forEach(c => c.resize())
}

function initCharts() {
  // 决策趋势折线图
  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    charts.push(chart)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['通过', '拒绝', '人工'] },
      grid: { left: 50, right: 20, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      },
      yAxis: { type: 'value', name: '决策量' },
      series: [
        { name: '通过', type: 'line', smooth: true, data: [8500, 8200, 8800, 9100, 8600, 7800, 8200], itemStyle: { color: '#52c41a' } },
        { name: '拒绝', type: 'line', smooth: true, data: [2100, 2300, 1900, 1800, 2200, 2400, 2100], itemStyle: { color: '#ff4d4f' } },
        { name: '人工', type: 'line', smooth: true, data: [900, 800, 1000, 1100, 950, 850, 900], itemStyle: { color: '#fa8c16' } },
      ],
    })
  }

  // 决策结果饼图
  if (pieChartRef.value) {
    const chart = echarts.init(pieChartRef.value)
    charts.push(chart)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        avoidLabelOverlap: true,
        label: { show: true, formatter: '{b}: {d}%' },
        data: [
          { value: 72.5, name: '通过', itemStyle: { color: '#52c41a' } },
          { value: 18.3, name: '拒绝', itemStyle: { color: '#ff4d4f' } },
          { value: 9.2, name: '人工', itemStyle: { color: '#fa8c16' } },
        ],
      }],
    })
  }

  // 规则命中率排行
  if (ruleHitChartRef.value) {
    const chart = echarts.init(ruleHitChartRef.value)
    charts.push(chart)
    const rules = ['年龄准入', '黑名单检查', '征信查询次数', '逾期记录', '收入校验', '负债率检查', '多头借贷', '设备指纹', 'IP风控', '手机号验证']
    const hitRates = [98.2, 2.1, 85.6, 12.3, 92.1, 78.5, 15.8, 45.2, 8.9, 88.3]
    chart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 100, right: 20, top: 10, bottom: 30 },
      xAxis: { type: 'value', max: 100, name: '命中率%' },
      yAxis: { type: 'category', data: rules.reverse(), inverse: false },
      series: [{
        type: 'bar',
        data: hitRates.reverse(),
        itemStyle: {
          color: (params: any) => params.value > 80 ? '#52c41a' : params.value > 50 ? '#fa8c16' : '#ff4d4f',
        },
        label: { show: true, position: 'right', formatter: '{c}%', fontSize: 11 },
      }],
    })
  }

  // 评分分布图
  if (scoreChartRef.value) {
    const chart = echarts.init(scoreChartRef.value)
    charts.push(chart)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 20, bottom: 40 },
      xAxis: {
        type: 'category',
        data: ['<400', '400-450', '450-500', '500-550', '550-600', '600-650', '650-700', '700-750', '750-800', '>800'],
        axisLabel: { fontSize: 10, rotate: 30 },
      },
      yAxis: { type: 'value', name: '人数' },
      series: [{
        type: 'bar',
        data: [120, 280, 450, 680, 1200, 1850, 2100, 1680, 920, 350],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#1677ff' },
            { offset: 1, color: '#69b1ff' },
          ]),
        },
      }],
      markLine: {
        data: [{ xAxis: '600-650', name: 'Cutoff', label: { formatter: 'Cutoff' } }],
      },
    })
  }
}
</script>
