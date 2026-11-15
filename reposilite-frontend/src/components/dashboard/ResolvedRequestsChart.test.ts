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
import ResolvedRequestsChart from './ResolvedRequestsChart.vue'

const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))
vi.mock('../../helpers/toast', () => ({ createErrorToast: vi.fn() }))
vi.mock('./TimeSeriesChart.vue', async () => {
  const { h: createElement } = await import('vue')
  return { default: { render: () => createElement('div', { class: 'chart-stub' }) } }
})

const clientWith = (allResolved) => ({
  statistics: { allResolved }
})

describe('ResolvedRequestsChart', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows an error card rather than the disabled message when the request fails', async () => {
    session.client = ref(clientWith(() => Promise.reject(new Error('unreachable'))))
    const wrapper = mount(ResolvedRequestsChart)

    await flushPromises()

    expect(wrapper.text(), 'a failed request should say so').toContain('Cannot load resolved request statistics')
    expect(wrapper.text(), 'a failed request must not be reported as statistics being switched off').not.toContain('disabled')
    wrapper.unmount()
  })

  it('shows the disabled message when the server reports statistics as switched off', async () => {
    session.client = ref(clientWith(() => Promise.resolve({ data: { statisticsEnabled: false, repositories: [] } })))
    const wrapper = mount(ResolvedRequestsChart)

    await flushPromises()

    expect(wrapper.text(), 'an explicit off flag should keep its own message').toContain('disabled for this instance')
    wrapper.unmount()
  })
})
