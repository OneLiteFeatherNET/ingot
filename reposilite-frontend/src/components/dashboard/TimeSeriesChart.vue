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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import useTheme from '../../store/theme'
import { categorical, chrome } from '../../store/palette'

// ECharts ships every chart type and component separately so a build only pays for what it
// draws. The dashboard needs one chart type and three components, which keeps this bundle
// far below the full distribution.
use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  series: {
    type: Array,
    required: true
  },
  /**
   * Appended to every value in the axis and the tooltip. A chart carries one unit and one
   * scale: two measures that do not share a unit belong in two charts, because a single
   * axis makes their relative size look like a fact when it is an accident of units.
   */
  unit: {
    type: String,
    default: ''
  },
  height: {
    type: String,
    default: '220px'
  }
})

const { theme } = useTheme()
const container = ref(null)
let chart = null

// One series is its own subject and the surrounding heading already names it, so a legend
// would only repeat the title. Several series have to be told apart, and then the legend is
// what keeps identity off colour alone.
const multiSeries = computed(() => props.series.length > 1)

const formatValue = (value) => {
  const rounded = Math.abs(value) >= 100 ? Math.round(value) : Math.round(value * 10) / 10
  return props.unit ? `${rounded} ${props.unit}` : `${rounded}`
}

const buildOption = () => {
  const ink = chrome(theme.isDark)

  return {
    color: categorical(theme.isDark),
    animation: false,
    backgroundColor: 'transparent',
    grid: {
      left: 4,
      right: 12,
      top: 8,
      bottom: multiSeries.value ? 28 : 4,
      containLabel: true
    },
    legend: multiSeries.value
      ? {
          bottom: 0,
          icon: 'roundRect',
          itemGap: 18,
          itemWidth: 10,
          itemHeight: 10,
          textStyle: { color: ink.text, fontSize: 11 }
        }
      : { show: false },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: ink.baseline } },
      valueFormatter: formatValue
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: ink.axis, hideOverlap: true, fontSize: 11 },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: ink.axis, fontSize: 11, formatter: formatValue },
      splitLine: { lineStyle: { color: ink.grid } }
    },
    series: props.series.map(series => ({
      name: series.name,
      type: 'line',
      smooth: false,
      showSymbol: false,
      lineStyle: { width: 2 },
      // Only a lone series gets a fill. Stacked translucent areas hide each other, and the
      // overlap reads as a value of its own.
      areaStyle: multiSeries.value ? undefined : { opacity: 0.14 },
      data: series.data
    }))
  }
}

const render = () => {
  // notMerge, because a redraw after a theme switch has to drop the previous colours
  // instead of layering the new option on top of them.
  chart?.setOption(buildOption(), true)
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
