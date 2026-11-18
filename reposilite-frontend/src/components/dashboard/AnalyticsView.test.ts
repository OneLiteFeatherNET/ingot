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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AnalyticsView from './AnalyticsView.vue'
import { configurationStoreKey } from '../../store/configuration'
import { loadedStore } from '../repositories/fixtures/repositories'
import statisticsSchema from '../settings/fixtures/statistics.schema.json'
import { ADMIN_NODES } from '../../store/nodeAccess'

// The visitor's permission is replaced per test. By default the visitor holds every node, as the earlier tests assume.
const holder = vi.hoisted((): { can: (node: string) => boolean } => ({ can: () => true }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { value: null }, isManager: { value: false }, can: (node: string) => holder.can(node) }),
}))

// The chart fetches its statistics from the server, so it is replaced by a marker here.
vi.mock('./ResolvedRequestsChart.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'resolved-requests' }) } }
})

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  holder.can = () => true
})

// A fresh store per test, holding the statistics domain as the instance serves it.
const mountAnalytics = async () => {
  const store = await loadedStore({
    server: { statistics: { enabled: true, resolvedRequestsInterval: 'DAILY' } } as never,
    // The fixture serves one schema for every domain, so the statistics schema is the one of this domain.
    schema: statisticsSchema as never,
  })
  wrapper = mount(AnalyticsView, {
    global: { provide: { [configurationStoreKey]: store } },
    attachTo: document.body,
  })
  await flushPromises()
  return wrapper
}

describe('AnalyticsView', () => {
  it('shows the usage statistics settings form, the place where what is collected is set', async () => {
    const view = await mountAnalytics()

    expect(view.find('[data-test="statistics-settings"]').exists(), 'the statistics form lives on analytics').toBe(true)
    expect(view.text(), 'the switch of the statistics domain is in the form').toContain('Enable statistics')
  })

  it('draws no traffic chart, which moved to the overview', async () => {
    const view = await mountAnalytics()

    expect(view.find('[data-test="resolved-requests"]').exists(), 'the chart is on the overview now').toBe(false)
    expect(view.text(), 'the traffic heading moved with the chart').not.toContain('Traffic')
  })

  it('draws the metrics heading through the shared section header, labelling the section', async () => {
    const view = await mountAnalytics()

    const section = view.get('[data-test="metrics-endpoint"]')
    expect(section.get('[data-test="section-header"] h2').text()).toBe('Metrics endpoint')
    expect(section.attributes('aria-labelledby'), 'the section is labelled by its heading').toBe(section.get('h2').attributes('id'))
  })

  it('names the metrics endpoint and the setting that turns it on', async () => {
    const view = await mountAnalytics()

    expect(view.text(), 'the Prometheus plugin is bundled, so the page says how to turn it on').toContain('INGOT_METRICS_ENABLED')
  })
})

// The four principals of Analytics. `settings` tells whether the visitor holds the settings node, which edits the form.
const principals = [
  { name: 'the dashboard node only', nodes: [ADMIN_NODES.dashboard], settings: false },
  { name: 'the settings node only', nodes: [ADMIN_NODES.settings], settings: true },
  { name: 'both nodes', nodes: [ADMIN_NODES.dashboard, ADMIN_NODES.settings], settings: true },
  { name: 'neither node', nodes: [ADMIN_NODES.console], settings: false },
]

const visitorWith = (nodes: string[]) => {
  holder.can = (node: string) => nodes.includes(node)
}

describe('AnalyticsView for either node', () => {
  it.each(principals.filter(principal => principal.settings))('lets $name edit the usage statistics form', async ({ nodes }) => {
    visitorWith(nodes)
    const view = await mountAnalytics()

    const fields = view.find('[data-test="statistics-settings"]').findAll('input, select, textarea')

    expect(fields.length, 'the form has fields the visitor can change').toBeGreaterThan(0)
    expect(fields.filter(field => field.attributes('disabled') !== undefined).length, 'no field is disabled').toBe(0)
  })

  it.each(principals.filter(principal => !principal.settings))('shows no field to $name, only the permission line', async ({ nodes }) => {
    visitorWith(nodes)
    const view = await mountAnalytics()

    const section = view.find('[data-test="statistics-settings"]')

    expect(section.findAll('input, select, textarea').length, 'no field is shown without the settings node').toBe(0)
    expect(section.text(), 'the line that says who may change the settings').toContain('Changing these settings needs the settings permission.')
  })

  it.each(principals.filter(principal => principal.nodes.some(node => node === ADMIN_NODES.dashboard || node === ADMIN_NODES.settings)))(
    'shows the metrics endpoint to $name',
    async ({ nodes }) => {
      visitorWith(nodes)
      const view = await mountAnalytics()

      expect(view.find('[data-test="metrics-endpoint"]').exists(), 'the metrics line is static, so both nodes see it').toBe(true)
    },
  )
})
