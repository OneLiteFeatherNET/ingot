<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import useTheme from '../../store/theme'

// ECharts ships every chart type and component separately so a build only pays for what it
// draws. The dashboard needs one chart type and three components, which keeps this bundle
// far below the full distribution.
use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  series: {
    type: Array,
    required: true
  },
  // 'line' draws the plain series, 'area' fills the space below it.
  variant: {
    type: String,
    default: 'line'
  },
  height: {
    type: String,
    default: '320px'
  }
})

const { theme } = useTheme()

// The dashboard sits on a white or near-black surface, so the axes and the legend have to
// follow the theme. The series colours are picked to stay distinguishable on both.
const PALETTE = ['#4f46e5', '#0891b2', '#e11d48', '#16a34a', '#d97706', '#7c3aed', '#0ea5e9', '#65a30d']

const container = ref(null)
let chart = null

const axisColor = () => (theme.isDark ? '#9ca3af' : '#6b7280')
const splitLineColor = () => (theme.isDark ? '#374151' : '#e5e7eb')

const buildOption = () => ({
  color: PALETTE,
  animation: false,
  backgroundColor: 'transparent',
  grid: {
    left: 8,
    right: 16,
    top: 8,
    // Room for the legend, which sits below the plot and would otherwise print on top of
    // the axis labels.
    bottom: 32,
    containLabel: true
  },
  legend: {
    bottom: 0,
    icon: 'roundRect',
    itemGap: 20,
    textStyle: { color: axisColor() }
  },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' }
  },
  xAxis: {
    type: 'time',
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: axisColor(), hideOverlap: true },
    splitLine: { show: false }
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: axisColor() },
    splitLine: { lineStyle: { color: splitLineColor() } }
  },
  series: props.series.map(series => ({
    name: series.name,
    type: 'line',
    smooth: false,
    showSymbol: false,
    areaStyle: props.variant === 'area' ? { opacity: 0.2 } : undefined,
    data: series.data
  }))
})

const render = () => {
  if (chart === null) {
    return
  }
  // notMerge, because a redraw after a theme switch has to drop the previous colours
  // instead of layering the new option on top of them.
  chart.setOption(buildOption(), true)
}

const resize = () => chart?.resize()

onMounted(() => {
  chart = init(container.value, null, { renderer: 'canvas' })
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(() => props.series, render, { deep: true })
watch(() => theme.isDark, render)
</script>

<template>
  <div
    ref="container"
    class="w-full"
    :style="{ height }"
  />
</template>
