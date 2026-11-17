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

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null }, isManager: { value: false } }) }))

// The chart fetches its statistics from the server, so it is replaced by a marker here.
vi.mock('./ResolvedRequestsChart.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'resolved-requests' }) } }
})

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// A fresh store per test, holding the statistics domain as the instance serves it.
const mountAnalytics = async () => {
  const store = await loadedStore({
    server: { statistics: { enabled: true, resolvedRequestsInterval: 'DAILY' } },
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

  it('names the metrics endpoint and the setting that turns it on', async () => {
    const view = await mountAnalytics()

    expect(view.text(), 'the Prometheus plugin is bundled, so the page says how to turn it on').toContain('INGOT_METRICS_ENABLED')
  })

  it('keeps the analytics view marker that the anchor registry names', async () => {
    const view = await mountAnalytics()

    expect(view.find('[data-ingot-component="analytics-view"]').exists()).toBe(true)
  })
})
