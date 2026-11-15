/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { Mock } from 'vitest'
import { mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import TimeSeriesChart from './TimeSeriesChart.vue'
import type { Series } from './seriesSummary'

// The option the chart is given. Only the fields the tests read are typed.
type ChartOption = {
  animation: boolean
  legend: { show: boolean }
  series: { name: string }[]
}

type FakeChart = {
  setOption: Mock<(option: ChartOption) => void>
  resize: Mock<() => void>
  dispose: Mock<() => void>
}

// ECharts draws on a canvas, which the test environment does not have. Its init is replaced by a double that records
// the options it is given. The double is created for each test, so no chart is shared between tests.
const echarts: { init: ((...args: unknown[]) => FakeChart) | null } = vi.hoisted(() => ({ init: null }))

vi.mock('echarts/core', () => ({
  init: (...args: unknown[]) => echarts.init?.(...args),
  use: () => {}
}))

const fakeChart = (): FakeChart => ({
  setOption: vi.fn<(option: ChartOption) => void>(),
  resize: vi.fn<() => void>(),
  dispose: vi.fn<() => void>()
})

const series: Series[] = [{ name: 'downloads', data: [[Date.UTC(2026, 0, 1), 3], [Date.UTC(2026, 0, 2), 5]] }]

let chart: FakeChart
let wrapper: VueWrapper | undefined

beforeEach(() => {
  chart = fakeChart()
  echarts.init = vi.fn(() => chart)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('TimeSeriesChart animation', () => {
  it('turns the animation off in the chart option, so the chart never animates', () => {
    wrapper = mount(TimeSeriesChart, { props: { series } })

    const option = chart.setOption.mock.calls[0][0]
    expect(option.animation, 'the animation flag of the first option').toBe(false)
  })

  it('keeps the animation off when the series change, so a redraw does not animate either', async () => {
    wrapper = mount(TimeSeriesChart, { props: { series } })

    await wrapper.setProps({ series: [...series, { name: 'uploads', data: [] }] })

    const latest = chart.setOption.mock.calls.at(-1)?.[0]
    expect(latest?.animation, 'the animation flag of the redrawn option').toBe(false)
  })
})

describe('TimeSeriesChart legend', () => {
  const instances: Series[] = [
    { name: 'ingot-0', data: [[Date.UTC(2026, 0, 1), 100]] },
    { name: 'ingot-1', data: [[Date.UTC(2026, 0, 1), 150]] }
  ]

  it('shows a legend when the chart draws two instances, so the lines can be told apart', () => {
    wrapper = mount(TimeSeriesChart, { props: { series: instances } })

    const option = chart.setOption.mock.calls[0][0]
    expect(option.legend.show, 'two series need a legend that is shown').toBe(true)
  })

  it('names each series after its instance, so the legend lists the instances', () => {
    wrapper = mount(TimeSeriesChart, { props: { series: instances } })

    const option = chart.setOption.mock.calls[0][0]
    expect(option.series.map(line => line.name), 'the series carry the instance names').toEqual(['ingot-0', 'ingot-1'])
  })

  it('shows no legend for a single series, because the heading already names it', () => {
    wrapper = mount(TimeSeriesChart, { props: { series: [instances[0]] } })

    const option = chart.setOption.mock.calls[0][0]
    expect(option.legend.show, 'one series needs no legend').toBe(false)
  })
})
