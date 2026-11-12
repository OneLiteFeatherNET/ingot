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
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import ResourceTrends from './ResourceTrends.vue'

const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))
vi.mock('../../helpers/toast', () => ({ createErrorToast: vi.fn() }))
// The chart draws on a canvas, which jsdom does not have. The card around it is what is tested.
// The stub writes the series it was given into an attribute, so a test can read what each chart draws.
vi.mock('./TimeSeriesChart.vue', async () => {
  const { h: createElement, defineComponent } = await import('vue')
  return {
    __esModule: true,
    default: defineComponent({
      props: {
        series: { type: Array, default: () => [] },
        unit: { type: String, default: '' }
      },
      setup: (props) => () => createElement('div', {
        class: 'chart-stub',
        'data-series': JSON.stringify(props.series)
      })
    })
  }
})

const clientWith = (snapshots) => ({
  status: { instance: vi.fn(), snapshots }
})

const seriesOf = (chart) => JSON.parse(chart.attributes('data-series'))

const clusterMember = (id, samples) => ({
  id,
  version: '1.3.0',
  uptimeStart: 0,
  usedMemory: 0,
  maxMemory: 512,
  usedThreads: 0,
  maxThreads: 20,
  failuresCount: 0,
  health: 'up',
  stale: false,
  interval: 60000,
  samples
})

describe('ResourceTrends', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('keeps two chart columns from 1024 pixels as before, and adds a third from 2560 pixels', async () => {
    session.client = ref(clientWith(() => Promise.resolve({ data: [] })))
    const wrapper = mount(ResourceTrends)
    await flushPromises()

    const grid = Array.from(wrapper.element.classList)
    expect(grid, 'two columns from 1024 pixels, as before').toContain('min-[1024px]:grid-cols-2')
    expect(grid, 'three columns from 2560 pixels').toContain('min-[2560px]:grid-cols-3')
    wrapper.unmount()
  })

  it('shows an error on both charts instead of placeholders when snapshots fail', async () => {
    session.client = ref(clientWith(() => Promise.reject(new Error('unreachable'))))
    const wrapper = mount(ResourceTrends)

    await flushPromises()

    expect(wrapper.find('.skeleton-bars').exists(), 'placeholders must end once the request has failed').toBe(false)
    expect(wrapper.findAll('[role="alert"]'), 'each chart card should report its own error').toHaveLength(2)
    wrapper.unmount()
  })

  it('reloads the snapshots from a chart card retry', async () => {
    const snapshots = vi.fn()
      .mockRejectedValueOnce(new Error('unreachable'))
      .mockResolvedValueOnce({ data: [] })
    session.client = ref(clientWith(snapshots))
    const wrapper = mount(ResourceTrends)
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(snapshots, 'retry should request the snapshots again').toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('keeps one memory series named Used memory when there is no cluster', async () => {
    session.client = ref(clientWith(() => Promise.resolve({
      data: [{ at: 1, memory: 100, threads: 3 }]
    })))
    const wrapper = mount(ResourceTrends)
    await flushPromises()

    const [memory] = wrapper.findAll('.chart-stub')
    expect(seriesOf(memory), 'without a bus the chart keeps its single series').toEqual([
      { name: 'Used memory', data: [[1, 100]] }
    ])
    wrapper.unmount()
  })

  it('draws one memory series per instance from the cluster, never a sum', async () => {
    session.client = ref(clientWith(() => Promise.resolve({ data: [] })))
    const cluster = {
      instances: [
        clusterMember('ingot-0', [{ at: 1, memory: 100, threads: 3 }]),
        clusterMember('ingot-1', [{ at: 2, memory: 50, threads: 5 }])
      ]
    }
    const wrapper = mount(ResourceTrends, { props: { cluster } })
    await flushPromises()

    const [memory] = wrapper.findAll('.chart-stub')
    expect(seriesOf(memory), 'each instance keeps its own series with its own sample times').toEqual([
      { name: 'ingot-0', data: [[1, 100]] },
      { name: 'ingot-1', data: [[2, 50]] }
    ])
    wrapper.unmount()
  })

  it('draws one thread series per instance from the cluster, never a sum', async () => {
    session.client = ref(clientWith(() => Promise.resolve({ data: [] })))
    const cluster = {
      instances: [
        clusterMember('ingot-0', [{ at: 1, memory: 100, threads: 3 }]),
        clusterMember('ingot-1', [{ at: 2, memory: 50, threads: 5 }])
      ]
    }
    const wrapper = mount(ResourceTrends, { props: { cluster } })
    await flushPromises()

    const [, threads] = wrapper.findAll('.chart-stub')
    expect(seriesOf(threads), 'the thread chart draws the threads of each instance').toEqual([
      { name: 'ingot-0', data: [[1, 3]] },
      { name: 'ingot-1', data: [[2, 5]] }
    ])
    wrapper.unmount()
  })

  it('shows the empty message when the cluster has no samples yet', async () => {
    session.client = ref(clientWith(() => Promise.resolve({ data: [] })))
    const cluster = { instances: [clusterMember('ingot-0', [])] }
    const wrapper = mount(ResourceTrends, { props: { cluster } })
    await flushPromises()

    expect(wrapper.text(), 'no sample anywhere means there is nothing to draw yet').toContain('No snapshots recorded yet')
    wrapper.unmount()
  })
})
