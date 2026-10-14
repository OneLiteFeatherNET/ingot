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
vi.mock('./TimeSeriesChart.vue', async () => {
  const { h: createElement } = await import('vue')
  return { default: { render: () => createElement('div', { class: 'chart-stub' }) } }
})

const clientWith = (snapshots) => ({
  status: { instance: vi.fn(), snapshots }
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
})
