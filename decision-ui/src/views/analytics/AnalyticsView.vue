<template>
  <div>
    <a-page-header title="分析看板" />
    <a-row :gutter="[16, 16]">
      <a-col :span="8">
        <a-card title="今日决策量">
          <a-statistic title="总决策" :value="0" />
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="通过率">
          <a-statistic title="通过" :value="0" suffix="%" />
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card title="平均耗时">
          <a-statistic title="P99" :value="0" suffix="ms" />
        </a-card>
      </a-col>
    </a-row>
    <a-card title="决策趋势" style="margin-top: 16px">
      <a-empty description="ECharts 图表将在分析看板阶段实现" />
    </a-card>
  </div>
</template>
