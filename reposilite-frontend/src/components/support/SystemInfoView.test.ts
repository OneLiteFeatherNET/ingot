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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SystemInfoView from './SystemInfoView.vue'

const BLANK = { render: () => null }

// The client is swapped per test through this holder. The view reads it through the session.
const session = vi.hoisted((): { client: unknown } => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

const FIXTURE = {
  instance: 'ingot-node-1',
  generatedAt: '2026-11-10T10:00:00Z',
  redactionRuleVersion: 1,
  ingot: { version: '1.2.3', buildTime: 'unknown', commit: 'unknown' },
  jvm: { vendor: 'Eclipse Adoptium', version: '21.0.4', uptimeMillis: 5000, heapUsedMb: 120.5, heapMaxMb: 512, processors: 4, threads: 30, inputArguments: ['-Xmx512m', '-Dfoo.password=[withheld]'] },
  operatingSystem: { name: 'Linux', version: '6.8', architecture: 'amd64', container: true },
  database: { kind: 'mariadb', endpoint: 'db.example:3306' },
  storage: [{ repository: 'releases', kind: 's3', region: 'eu-central-1' }],
  cluster: null,
  plugins: [{ name: 'metrics', version: '1.2.3', dependencies: ['status'] }],
  environment: { PUID: '1000', PLANTED_ENV: '[withheld]' },
  systemProperties: { 'ingot.supportBundle.enabled': 'true' },
  configuration: {
    local: { hostname: '0.0.0.0', keyPassword: '[withheld]' },
    shared: { storage: { type: 's3', secretKey: '********' } }
  }
}

// The page reads its tab and its fragment from the address, so every mount gets a memory router at the given address.
const mountView = (client: unknown, address = '/settings/system-info') => {
  session.client = client
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings/system-info', name: 'system-info', component: BLANK },
      { path: '/:pathMatch(.*)*', component: BLANK },
    ],
  })
  void router.push(address)
  return mount(SystemInfoView, { global: { plugins: [router] } })
}

const tabsOf = (wrapper: ReturnType<typeof mountView>) => wrapper.findAll('[role="tab"]')

afterEach(() => {
  session.client = null
})

describe('SystemInfoView', () => {
  it('renders every section of the page from the system information', async () => {
    // when: the page is opened
    const wrapper = mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: FIXTURE })), availability: vi.fn() } })
    await flushPromises()

    // then: each section has its heading
    const headings = wrapper.findAll('h2').map(heading => heading.text())
    expect(headings).toEqual(expect.arrayContaining([
      'Ingot', 'Java', 'Operating system', 'Database', 'Storage', 'Cluster', 'Plugins', 'Environment', 'System properties', 'Configuration'
    ]))
  })

  it('shows the withheld values as written, never a value that was not sent', async () => {
    const wrapper = mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: FIXTURE })) } })
    await flushPromises()

    expect(wrapper.text()).toContain('[withheld]')
    expect(wrapper.text()).toContain('mariadb')
    expect(wrapper.text()).toContain('db.example:3306')
  })

  it('tells the visitor that the pages are turned off when the server answers 404', async () => {
    const wrapper = mountView({
      support: { systemInfo: vi.fn(() => Promise.reject(Object.assign(new Error('Not Found'), { response: { status: 404 } }))) }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('turned off')
  })

  it('shows the tier label of a plugin next to its version', async () => {
    // given: a plugin that declares the official tier
    const plugins = [{ name: 'checksum', version: '2.0.0', dependencies: [], tier: 'official' }]
    const wrapper = mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: { ...FIXTURE, plugins } })) } })
    await flushPromises()

    // then: the row names the plugin, its version and the label as text
    const row = wrapper.findAll('li').find(item => item.text().includes('checksum'))
    expect(row?.text()).toContain('2.0.0')
    expect(row?.text()).toContain('Official')
  })

  it('shows Community for a plugin without a tier', async () => {
    // given: a plugin that sends no tier
    const plugins = [{ name: 'metrics', version: '1.2.3', dependencies: [] }]
    const wrapper = mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: { ...FIXTURE, plugins } })) } })
    await flushPromises()

    // then: the row shows the community label
    const row = wrapper.findAll('li').find(item => item.text().includes('metrics'))
    expect(row?.text()).toContain('Community')
  })
})

const withInfo = (data = FIXTURE) => ({ support: { systemInfo: vi.fn(() => Promise.resolve({ data })) } })
const panelsOf = (wrapper: ReturnType<typeof mountView>) => wrapper.findAll('[role="tabpanel"]')
const filterOf = (panel: ReturnType<typeof panelsOf>[number]) => panel.find('[data-ingot-part="filter"]')

describe('SystemInfoView tabs', () => {
  it('shows four tabs in the order Overview, Plugins, Environment and Configuration, with Overview selected', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()

    const tabs = tabsOf(wrapper)
    expect(tabs.map(tab => tab.text())).toEqual(['Overview', 'Plugins', 'Environment', 'Configuration'])
    expect(tabs[0].attributes('aria-selected'), 'Overview is selected by default').toBe('true')
  })

  it('draws the six overview cards, each labelled by its heading', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()

    const cards = panelsOf(wrapper)[0].findAll('article')
    expect(cards.map(card => card.attributes('aria-labelledby'))).toEqual([
      'support-ingot', 'support-java', 'support-os', 'support-database', 'support-storage', 'support-cluster',
    ])
  })

  it('keeps the Java input arguments behind a disclosure labelled Input arguments', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()

    const disclosure = wrapper.find('details')
    expect(disclosure.find('summary').text()).toBe('Input arguments')
    expect(disclosure.attributes('open'), 'the disclosure is closed by default').toBeUndefined()
    expect(disclosure.text()).toContain('-Xmx512m')
  })

  it('shows the filter on Environment and Configuration only', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()

    const panels = panelsOf(wrapper)
    expect(filterOf(panels[0]).exists(), 'Overview has no filter').toBe(false)
    expect(filterOf(panels[1]).exists(), 'Plugins has no filter').toBe(false)
    expect(filterOf(panels[2]).exists(), 'Environment has the filter').toBe(true)
    expect(filterOf(panels[3]).exists(), 'Configuration has the filter').toBe(true)
  })

  it('narrows the environment and system property rows to the typed text', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    await filterOf(environment).setValue('puid')

    expect(environment.findAll('dt').map(term => term.text())).toEqual(['PUID'])
  })

  it('says that no entry matches when the filter keeps no row', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    await filterOf(environment).setValue('no-such-row')

    expect(environment.text()).toContain('No entry matches the filter.')
  })

  it('shows the shared configuration whole, with a note that the filter does not apply to it', async () => {
    const wrapper = mountView(withInfo())
    await flushPromises()
    const configuration = panelsOf(wrapper)[3]

    await filterOf(configuration).setValue('no-such-row')

    expect(configuration.text()).toContain('The filter does not apply to the shared configuration.')
    expect(configuration.text(), 'the shared block is not narrowed').toContain('secretKey')
  })

  it('opens the tab of a section anchor and scrolls the section into view', async () => {
    const scrolled: string[] = []
    const original = Element.prototype.scrollIntoView
    Element.prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this.id)
    }
    try {
      const wrapper = mountView(withInfo(), '/settings/system-info#support-properties')
      await flushPromises()

      expect(tabsOf(wrapper)[2].attributes('aria-selected'), 'the system properties are on Environment').toBe('true')
      expect(scrolled).toEqual(['support-properties'])
    } finally {
      Element.prototype.scrollIntoView = original
    }
  })

  it('lets a section anchor win over a tab parameter that disagrees with it', async () => {
    const wrapper = mountView(withInfo(), '/settings/system-info?tab=plugins#support-cluster')
    await flushPromises()

    expect(tabsOf(wrapper)[0].attributes('aria-selected'), 'the cluster card is on Overview').toBe('true')
  })

  it('marks the failure of the system information with the error state', async () => {
    const wrapper = mountView({ support: { systemInfo: vi.fn(() => Promise.reject(new Error('The instance did not answer.'))) } })
    await flushPromises()

    expect(wrapper.find('[data-ingot-state="error"]').text()).toBe('The instance did not answer.')
  })
})
