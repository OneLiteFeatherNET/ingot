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
import DashboardView from './DashboardView.vue'

// The panels fetch from the server and are not under test here, so each one is a marker.
vi.mock('./InstanceStatusPanel.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'instance-status' }) } }
})
vi.mock('./ResourceTrends.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'resources' }) } }
})
vi.mock('./ResolvedRequestsChart.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'resolved-requests' }) } }
})
vi.mock('./GettingStartedCard.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'getting-started' }) } }
})

describe('DashboardView', () => {
  it('shows the instance status and the resources', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.find('[data-test="instance-status"]').exists(), 'the dashboard is about the running instance').toBe(true)
    expect(wrapper.find('[data-test="resources"]').exists()).toBe(true)
  })

  it('has no maximum width, so the status and the resources use the full window width', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.element.classList).toContain('max-w-none')
  })

  it('gives the getting started checklist the full content width, with no reading cap that leaves a blank block', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.find('[data-test="getting-started"]').classes(), 'no 960 pixel cap on the checklist').not.toContain('max-w-[960px]')
  })

  it('no longer renders the resolved requests chart, which moved to analytics', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.find('[data-test="resolved-requests"]').exists(), 'usage over time belongs to the analytics tab').toBe(false)
    expect(wrapper.text(), 'the traffic heading moved with the chart').not.toContain('Traffic')
  })
})
