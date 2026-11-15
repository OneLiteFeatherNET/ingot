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

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AnalyticsView from './AnalyticsView.vue'

// The chart fetches its statistics from the server, so it is replaced by a marker here.
vi.mock('./ResolvedRequestsChart.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'resolved-requests' }) } }
})

describe('AnalyticsView', () => {
  it('renders the resolved requests chart under a Traffic heading', () => {
    const wrapper = mount(AnalyticsView)

    expect(wrapper.find('[data-test="resolved-requests"]').exists(), 'the usage chart lives on analytics').toBe(true)
    expect(wrapper.find('#traffic-heading').text()).toBe('Traffic')
  })

  it('does not show the instance status or the resources', () => {
    const wrapper = mount(AnalyticsView)

    expect(wrapper.text(), 'instance status stays on the dashboard').not.toContain('Instance status')
    expect(wrapper.text(), 'resources stay on the dashboard').not.toContain('Resources')
  })
})
