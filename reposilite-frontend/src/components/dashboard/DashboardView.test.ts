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

  it('draws no level one heading, because Overview names the page', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.findAll('h1'), 'the status block is a section of Overview').toEqual([])
  })

  it('draws the three sections as level two headings, each labelled by its own title', () => {
    const wrapper = mount(DashboardView)

    const sections = wrapper.findAll('section')
    expect(sections.map(section => section.attributes('aria-labelledby')), 'each section names its heading').toEqual([
      'instance-status-heading',
      'resources-heading',
      'traffic-heading'
    ])
    expect(wrapper.findAll('h2').map(heading => heading.text())).toEqual(['Instance status', 'Resources', 'Traffic'])
  })

  it('draws every section heading in the title large size', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.findAll('h2').map(heading => heading.classes('md-title-large')), 'every section heading has the same size').toEqual([true, true, true])
  })

  it('draws the getting started card before the sections', () => {
    const wrapper = mount(DashboardView)

    const html = wrapper.html()
    expect(html.indexOf('getting-started'), 'the checklist comes first').toBeLessThan(html.indexOf('instance-status'))
  })

  it('renders the resolved requests chart under a Traffic heading, after the resources', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.find('[data-test="resolved-requests"]').exists(), 'the traffic chart is part of the overview').toBe(true)
    expect(wrapper.find('#traffic-heading').text()).toBe('Traffic')
    const html = wrapper.html()
    expect(html.indexOf('resources'), 'traffic comes after the resources').toBeLessThan(html.indexOf('resolved-requests'))
  })
})
