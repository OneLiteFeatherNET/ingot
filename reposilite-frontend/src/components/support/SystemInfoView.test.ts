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
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SystemInfoView from './SystemInfoView.vue'
import { configurationStoreKey } from '../../store/configuration'
import { ADMIN_NODES } from '../../store/nodeAccess'

const BLANK = { render: () => null }
type Page = Awaited<ReturnType<typeof mountView>>
type PanelOf = ReturnType<Page['findAll']>[number]

// The client and the permissions are swapped per test through this holder. The view reads them through the session.
const session = vi.hoisted((): { client: unknown, can: (node: string) => boolean } => ({ client: null, can: () => true }))

vi.mock('../../store/session', () => ({
  useSession: () => ({
    client: { get value() { return session.client } },
    can: (node: string) => session.can(node),
  })
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
// The address is resolved before the mount: installing a router starts its own navigation, which would replace a pending one.
// The store of the settings is replaced per test: the reset reads the settings when none are read, then resets them.
const fakeStore = (domains: string[] = ['ingot']) => ({
  domains: ref(domains),
  fetchConfiguration: vi.fn(() => Promise.resolve(true)),
  factoryReset: vi.fn(() => Promise.resolve(true)),
})

const mountView = async (
  client: { support?: Record<string, unknown> },
  address = '/settings/system-info',
  store = fakeStore(),
) => {
  session.client = { support: { availability: vi.fn(() => Promise.resolve({ data: { enabled: true } })), ...client.support } }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings/system-info', name: 'system-info', component: BLANK },
      { path: '/settings/:domain?', name: 'settings', component: BLANK },
      { path: '/:pathMatch(.*)*', component: BLANK },
    ],
  })
  await router.push(address)
  await router.isReady()
  return mount(SystemInfoView, { global: { plugins: [router], provide: { [configurationStoreKey]: store } } })
}

const tabsOf = (wrapper: Page) => wrapper.findAll('[role="tab"]')

afterEach(() => {
  session.client = null
  session.can = () => true
})

describe('SystemInfoView', () => {
  it('renders every section of the page from the system information', async () => {
    // when: the page is opened
    const wrapper = await mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: FIXTURE })) } })
    await flushPromises()

    // then: each section has its heading
    const headings = wrapper.findAll('h2').map(heading => heading.text())
    expect(headings).toEqual(expect.arrayContaining([
      'Ingot', 'Java', 'Operating system', 'Database', 'Storage', 'Cluster', 'Plugins', 'Environment', 'System properties', 'Configuration'
    ]))
  })

  it('shows the withheld values as written, never a value that was not sent', async () => {
    const wrapper = await mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: FIXTURE })) } })
    await flushPromises()

    expect(wrapper.text()).toContain('[withheld]')
    expect(wrapper.text()).toContain('mariadb')
    expect(wrapper.text()).toContain('db.example:3306')
  })

  it('tells the visitor that the pages are turned off when the server answers 404', async () => {
    const wrapper = await mountView({
      support: { systemInfo: vi.fn(() => Promise.reject(Object.assign(new Error('Not Found'), { response: { status: 404 } }))) }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('turned off')
  })

  it('shows the tier label of a plugin next to its version', async () => {
    // given: a plugin that declares the official tier
    const plugins = [{ name: 'checksum', version: '2.0.0', dependencies: [], tier: 'official' }]
    const wrapper = await mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: { ...FIXTURE, plugins } })) } })
    await flushPromises()

    // then: the card names the plugin, its version and the label as text
    const card = cardsIn(wrapper).find(item => item.text().includes('checksum'))
    expect(card?.text()).toContain('2.0.0')
    expect(card?.text()).toContain('Official')
  })

  it('shows Community for a plugin without a tier', async () => {
    // given: a plugin that sends no tier
    const plugins = [{ name: 'metrics', version: '1.2.3', dependencies: [] }]
    const wrapper = await mountView({ support: { systemInfo: vi.fn(() => Promise.resolve({ data: { ...FIXTURE, plugins } })) } })
    await flushPromises()

    // then: the card shows the community label
    const card = cardsIn(wrapper).find(item => item.text().includes('metrics'))
    expect(card?.text()).toContain('Community')
  })
})

const withInfo = (data: object = FIXTURE) => ({ support: { systemInfo: vi.fn(() => Promise.resolve({ data })) } })
const panelsOf = (wrapper: Page) => wrapper.findAll('[role="tabpanel"]')
const filterOf = (panel: PanelOf) => panel.find('input[type="search"]')

describe('SystemInfoView tabs', () => {
  it('shows four tabs in the order Overview, Plugins, Environment and Configuration, with Overview selected', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const tabs = tabsOf(wrapper)
    expect(tabs.map(tab => tab.text())).toEqual(['Overview', 'Plugins', 'Environment', 'Configuration'])
    expect(tabs[0].attributes('aria-selected'), 'Overview is selected by default').toBe('true')
  })

  it('draws the six overview cards, each labelled by its heading', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const cards = panelsOf(wrapper)[0].findAll('article')
    expect(cards.map(card => card.attributes('aria-labelledby'))).toEqual([
      'support-ingot', 'support-java', 'support-os', 'support-database', 'support-storage', 'support-cluster',
    ])
  })

  it('keeps the Java input arguments behind a disclosure labelled Input arguments', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    // The Overview holds the only disclosure of the cards; the note of the header is a disclosure too, and comes first.
    const disclosure = panelsOf(wrapper)[0].find('details')
    expect(disclosure.find('summary').text()).toBe('Input arguments')
    expect(disclosure.attributes('open'), 'the disclosure is closed by default').toBeUndefined()
    expect(disclosure.text()).toContain('-Xmx512m')
  })

  it('shows the filter on Environment and Configuration only', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const panels = panelsOf(wrapper)
    expect(filterOf(panels[0]).exists(), 'Overview has no filter').toBe(false)
    expect(filterOf(panels[1]).exists(), 'Plugins has no filter').toBe(false)
    expect(filterOf(panels[2]).exists(), 'Environment has the filter').toBe(true)
    expect(filterOf(panels[3]).exists(), 'Configuration has the filter').toBe(true)
  })

  it('narrows the environment and system property rows to the typed text', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    await filterOf(environment).setValue('puid')

    expect(environment.findAll('[role="rowheader"]').map(term => term.text())).toEqual(['PUID'])
  })

  it('says that no entry matches when the filter keeps no row', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    await filterOf(environment).setValue('no-such-row')

    expect(environment.text()).toContain('No key matches')
  })

  it('narrows the shared configuration with the same filter as the local one', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()
    const configuration = panelsOf(wrapper)[3]

    await filterOf(configuration).setValue('no-such-row')

    expect(configuration.text(), 'the shared keys are hidden by a query that matches none').not.toContain('secretKey')
    expect(configuration.text(), 'the note that the filter skips the shared block is gone').not.toContain('The filter does not apply')
  })

  it('opens the tab of a section anchor and scrolls the section into view', async () => {
    // jsdom has no layout, so the scroll is replaced for the test and put back afterwards.
    const scrolled: string[] = []
    const prototype = Element.prototype as { scrollIntoView?: unknown }
    const original = prototype.scrollIntoView
    prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this.id)
    }
    try {
      const wrapper = await mountView(withInfo(), '/settings/system-info#support-properties')
      await flushPromises()

      expect(tabsOf(wrapper)[2].attributes('aria-selected'), 'the system properties are on Environment').toBe('true')
      expect(scrolled).toEqual(['support-properties'])
    } finally {
      prototype.scrollIntoView = original
    }
  })

  it('lets a section anchor win over a tab parameter that disagrees with it', async () => {
    const scrolled: string[] = []
    const prototype = Element.prototype as { scrollIntoView?: unknown }
    const original = prototype.scrollIntoView
    prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this.id)
    }
    try {
      const wrapper = await mountView(withInfo(), '/settings/system-info?tab=plugins#support-cluster')
      await flushPromises()

      expect(tabsOf(wrapper)[0].attributes('aria-selected'), 'the cluster card is on Overview').toBe('true')
      expect(scrolled).toEqual(['support-cluster'])
    } finally {
      prototype.scrollIntoView = original
    }
  })

  it('shows the failure of the system information in the Overview, and keeps the tabs', async () => {
    const wrapper = await mountView({ support: { systemInfo: vi.fn(() => Promise.reject(new Error('The instance did not answer.'))) } })
    await flushPromises()

    expect(wrapper.text()).toContain('The instance did not answer.')
    expect(tabsOf(wrapper), 'the four tabs stay, as the design keeps the Overview for the message').toHaveLength(4)
  })
})

const DOWNLOAD = 'Download support bundle'
const withAvailability = (availability: () => Promise<unknown>) => ({
  support: { systemInfo: vi.fn(() => Promise.resolve({ data: FIXTURE })), availability: vi.fn(availability) },
})
const partsIn = (wrapper: Page) => Array.from((wrapper.element as Element).querySelectorAll('*'), (element) => element.getAttribute('data-ingot-part')).filter(Boolean)
const buttonNamed = (wrapper: Page, name: string) => wrapper.findAll('button').find((button) => button.text().includes(name))

describe('SystemInfoView support action', () => {
  it('offers the download in the header, outside every tab, while the support pages are on', async () => {
    const wrapper = await mountView(withAvailability(() => Promise.resolve({ data: { enabled: true } })))
    await flushPromises()

    expect(buttonNamed(wrapper, DOWNLOAD), 'the download action is drawn').toBeDefined()
    expect(panelsOf(wrapper).some((panel) => panel.text().includes(DOWNLOAD)), 'no tab holds the action').toBe(false)
  })

  it('keeps the note on what is removed in a disclosure in the header', async () => {
    const wrapper = await mountView(withAvailability(() => Promise.resolve({ data: { enabled: true } })))
    await flushPromises()

    const disclosure = wrapper.find('details')
    expect(disclosure.find('summary').text()).toBe('What is removed')
    expect(disclosure.text()).toContain('Secrets are removed or masked')
    expect(disclosure.text()).toContain('Read the zip before you attach it to a public issue.')
  })

  it('says the pages are turned off, and draws no tab and no action, when the availability check answers off', async () => {
    const wrapper = await mountView(withAvailability(() => Promise.resolve({ data: { enabled: false } })))
    await flushPromises()

    expect(wrapper.text()).toContain('The support pages are turned off on this instance.')
    expect(tabsOf(wrapper)).toHaveLength(0)
    expect(buttonNamed(wrapper, DOWNLOAD), 'no download action').toBeUndefined()
  })

  it('says the bundle cannot be checked, and draws no tab and no action, when the availability check fails', async () => {
    const wrapper = await mountView(withAvailability(() => Promise.reject(new Error('The check did not answer.'))))
    await flushPromises()

    expect(wrapper.text()).toContain('The support bundle cannot be checked right now.')
    expect(tabsOf(wrapper)).toHaveLength(0)
    expect(buttonNamed(wrapper, DOWNLOAD), 'no download action').toBeUndefined()
  })
})

describe('SystemInfoView anchors of the merged page', () => {
  it('carries the name of the page and the old support name as its alias', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    expect(wrapper.attributes('data-ingot-component')).toBe('system-info-view')
    expect(wrapper.attributes('data-ingot-component-alias'), 'the old name keeps working as an alias').toBe('support-view')
  })

  it('has the parts of the support action, the note and the filter where the design puts them', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    expect(partsIn(wrapper)).toEqual(expect.arrayContaining(['support-action', 'support-note', 'filter']))
  })
})

describe('SystemInfoView factory reset', () => {
  const RESET = 'Factory reset'
  const zoneOf = (wrapper: Page) => panelsOf(wrapper)[0].find('section[aria-labelledby="system-danger-title"]')
  // The dialog is teleported to the body, so its button is looked up in the document, not in the page.
  const confirmOf = () => Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.includes('Reset all to defaults'))

  it('shows the factory reset at the end of the Overview for a visitor who may edit settings', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const zone = zoneOf(wrapper)
    expect(zone.exists(), 'the danger zone is in the Overview panel').toBe(true)
    expect(zone.find('#system-danger-title').text()).toBe('Danger zone')
    expect(zone.findAll('button').map((button) => button.text())).toContain(RESET)
    expect(panelsOf(wrapper)[0].element.lastElementChild, 'the zone is the last block of the Overview').toBe(zone.element)
  })

  it('hides the factory reset from a visitor without the settings node, and keeps the Overview', async () => {
    session.can = (node) => node !== ADMIN_NODES.settings
    const wrapper = await mountView(withInfo())
    await flushPromises()

    expect(zoneOf(wrapper).exists(), 'no danger zone without the settings node').toBe(false)
    expect(buttonNamed(wrapper, RESET), 'no factory reset button').toBeUndefined()
    expect(wrapper.find('#support-ingot').exists(), 'the system information is still shown').toBe(true)
  })

  it('opens the reset dialog from the factory reset button', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    await buttonNamed(wrapper, RESET)!.trigger('click')
    await flushPromises()

    expect(document.getElementById('factory-reset-title')?.textContent?.trim(), 'the dialog asks before it resets').toBe('Reset all to defaults?')
    expect(confirmOf(), 'the dialog offers the reset').toBeDefined()
    wrapper.unmount()
  })

  it('reads the settings first when none are read yet, then resets every setting through the store', async () => {
    const store = fakeStore([])
    const wrapper = await mountView(withInfo(), '/settings/system-info', store)
    await flushPromises()

    await buttonNamed(wrapper, RESET)!.trigger('click')
    await flushPromises()
    confirmOf()!.click()
    await flushPromises()

    expect(store.fetchConfiguration, 'the settings are read before the reset').toHaveBeenCalledOnce()
    expect(store.factoryReset).toHaveBeenCalledOnce()
    expect(wrapper.find('#support-ingot').exists(), 'the page stays after the reset').toBe(true)
    wrapper.unmount()
  })

  it('does not reset anything when the settings cannot be read', async () => {
    const store = fakeStore([])
    store.fetchConfiguration.mockResolvedValueOnce(false)
    const wrapper = await mountView(withInfo(), '/settings/system-info', store)
    await flushPromises()

    await buttonNamed(wrapper, RESET)!.trigger('click')
    await flushPromises()
    confirmOf()!.click()
    await flushPromises()

    expect(store.factoryReset, 'no reset without the settings').not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('SystemInfoView layout of the cards', () => {
  it('gives every card and every value cell a min-w-0, so a long value cannot push its card wider', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const overview = panelsOf(wrapper)[0]
    for (const card of overview.findAll('article')) {
      expect(card.classes(), `the card ${card.attributes('aria-labelledby')} may shrink below its content`).toContain('min-w-0')
    }
    for (const value of overview.findAll('dd')) {
      expect(value.classes(), `the value "${value.text()}" may shrink below its content`).toContain('min-w-0')
    }
  })

  it('wraps a value anywhere it does not fit, so an unbroken hostname or instance name stays inside its card', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    for (const value of panelsOf(wrapper)[0].findAll('dd')) {
      expect(value.classes(), `the value "${value.text()}" wraps anywhere`).toContain('wrap-anywhere')
    }
  })

  it('gives the environment values the same min-w-0 and wrap as the cards', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    for (const value of panelsOf(wrapper)[2].findAll('dd')) {
      expect(value.classes(), `the environment value "${value.text()}" may shrink`).toContain('min-w-0')
      expect(value.classes(), `the environment value "${value.text()}" wraps anywhere`).toContain('wrap-anywhere')
    }
  })
})

describe('SystemInfoView support action placement', () => {
  // The disclosure of the header is the first one on the page: the tabs come after it.
  const noteOf = (wrapper: Page) => wrapper.find('details')

  it('draws the download button after the disclosure in the DOM', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const disclosure = noteOf(wrapper).element
    const button = buttonNamed(wrapper, DOWNLOAD)!.element
    expect(disclosure.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING, 'the button follows the disclosure').toBeTruthy()
  })

  it('does not place the button in a row with the disclosure, so toggling the disclosure cannot move it sideways', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    const header = noteOf(wrapper).element.parentElement!
    const button = buttonNamed(wrapper, DOWNLOAD)!.element
    expect(button.parentElement, 'the button is a direct child of the header column').toBe(header)
    expect(header.className, 'the header stacks its children in a column').toMatch(/\bflex-col\b/)
    expect(header.className, 'the header is not a wrapping row').not.toMatch(/\bflex-wrap\b/)
  })

  it('makes the disclosure summary only as wide as its label, so the focus outline hugs the text', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()

    expect(noteOf(wrapper).find('summary').classes(), 'the summary is fit to its content').toContain('w-fit')
  })
})

describe('SystemInfoView uptime', () => {
  it('shows the uptime of the JVM in minutes and seconds, not as raw milliseconds', async () => {
    // given: a JVM that has been up for 700926 milliseconds
    const wrapper = await mountView(withInfo({ ...FIXTURE, jvm: { ...FIXTURE.jvm, uptimeMillis: 700926 } }))
    await flushPromises()

    // then: the value of the Uptime row reads as a duration
    const term = panelsOf(wrapper)[0].findAll('dt').find(item => item.text() === 'Uptime')!
    expect(term.element.nextElementSibling?.textContent).toBe('11 min 40 s')
  })
})

describe('SystemInfoView environment and configuration as trees', () => {
  // Twelve Kubernetes service variables and the other variables: more than 20 keys, so the groups start collapsed.
  const SERVICE_KEYS = ['ADDR', 'PORT', 'PROTO', 'ADDR_IPV6', 'ADDR_ZONE', 'PORT_NAME', 'TLS', 'WEIGHT', 'ID', 'NAME', 'MODE', 'LIMIT']
  const longEnvironment = Object.fromEntries([
    ...SERVICE_KEYS.map((suffix): [string, string] => [`INGOT_DASHBOARD_PORT_8080_TCP_${suffix}`, '[withheld]']),
    ['PUID', '1000'],
    ['PGID', '1000'],
    ['JAVA_HOME', '/usr/lib/jvm/temurin'],
    ['TZ', 'Europe/Berlin'],
    ['HOSTNAME', 'ingot-0'],
    ['LANG', 'C.UTF-8'],
    ['KUBERNETES_SERVICE_HOST', '10.0.0.1'],
    ['KUBERNETES_SERVICE_PORT', '443'],
    ['REPOSILITE_OPTS', '--port=8080'],
  ])
  const longLocal = Object.fromEntries([
    ['basePath', '/'],
    ['bypassExternalCache', 'false'],
    ['cachedLogSize', '512'],
    ['compressionStrategy', 'gzip'],
    ['database', 'sqlite'],
    ['databaseConnectionTimeout', '30'],
    ['databaseKeepalive', 'true'],
    ['databaseMaxLifetime', '1800'],
    ['databaseThreadPool', '10'],
    ['hostname', '0.0.0.0'],
    ['keyPassword', '[withheld]'],
    ['port', '8080'],
    ['proxyTimeout', '15'],
    ['releaseOnly', 'false'],
    ['retentionDays', '30'],
    ['snapshotDays', '7'],
    ['storageRoot', '/app/data'],
    ['tokenTtl', '3600'],
    ['uploadLimit', '100'],
    ['webhookSecret', '[withheld]'],
    ['zone', 'eu'],
  ])
  const treeOf = (panel: PanelOf) => panel.find('[role="table"]')
  // A group is a button that reports whether it is open. Shared is the one native disclosure left.
  const groupNamed = (panel: PanelOf, label: string) => panel.findAll('button[aria-expanded]').find((item) => item.text().includes(label))
  const rowNamed = (panel: PanelOf, key: string) => panel.findAll('[role="rowheader"]').find((term) => term.text() === key)

  it('groups the Kubernetes service variables under one collapsed group that shows its key count', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, environment: longEnvironment }))
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    const group = groupNamed(environment, 'INGOT_DASHBOARD_PORT_8080_TCP')
    expect(group?.text(), 'the group names its prefix and counts its 12 keys').toContain('12 keys')
    expect(group?.attributes('aria-expanded'), 'the group is collapsed above 20 keys').toBe('false')
  })

  it('opens the group of a typed key, so the matching variable is visible', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, environment: longEnvironment }))
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    await filterOf(environment).setValue('TCP_TLS')

    expect(groupNamed(environment, 'INGOT_DASHBOARD_PORT_8080_TCP')?.attributes('aria-expanded'), 'the group opens for the match').toBe('true')
    expect(environment.findAll('mark').map((mark) => mark.text())).toContain('TCP_TLS')
  })

  it('keeps the keys of the environment in the page flow, with no scroll box of its own', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, environment: longEnvironment }))
    await flushPromises()

    expect(treeOf(panelsOf(wrapper)[2]).classes(), 'the table does not scroll inside the page').not.toContain('overflow-y-auto')
    expect(treeOf(panelsOf(wrapper)[2]).attributes('tabindex'), 'the table is not a second tab stop').toBeUndefined()
  })

  it('gives each section of the environment its own Expand all and Collapse all in its header', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, environment: longEnvironment }))
    await flushPromises()
    const environment = panelsOf(wrapper)[2]

    const expandAll = environment.findAll('button').filter((button) => button.text() === 'Expand all')
    expect(expandAll, 'Environment and System properties each have one').toHaveLength(2)
    await expandAll[0].trigger('click')
    expect(groupNamed(environment, 'INGOT_DASHBOARD_PORT_8080_TCP')?.attributes('aria-expanded'), 'Expand all opens the groups of its section').toBe('true')
  })

  it('keeps the filter at the top of the Configuration tab, in a sticky header', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, configuration: { local: longLocal, shared: FIXTURE.configuration.shared } }))
    await flushPromises()

    expect(filterOf(panelsOf(wrapper)[3]).element.closest('.sticky'), 'the filter stays in view while the page scrolls').not.toBeNull()
  })

  it('shows the local configuration as a tree of camel case groups, with Local and Shared as sections', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, configuration: { local: longLocal, shared: FIXTURE.configuration.shared } }))
    await flushPromises()
    const configuration = panelsOf(wrapper)[3]

    expect(configuration.findAll('h2').map((heading) => heading.text()), 'Local and Shared are section headings').toEqual(expect.arrayContaining(['Local', 'Shared']))
    expect(configuration.text(), 'the Local section counts its 21 keys').toContain('21 keys')
    expect(groupNamed(configuration, 'database')?.text(), 'the database group counts its five keys').toContain('5 keys')
  })

  it('shows the shared configuration as a tree of dotted keys, not as raw JSON', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()
    const configuration = panelsOf(wrapper)[3]

    expect(configuration.find('pre').exists(), 'no JSON block is left').toBe(false)
    expect(rowNamed(configuration, 'storage.type')?.element.nextElementSibling?.textContent?.trim(), 'the nested key is a row with its value').toBe('s3')
    expect(groupNamed(configuration, 'storage')?.text(), 'the storage group counts its two keys').toContain('2 keys')
  })

  it('shows the withheld shared value as written, in its row', async () => {
    const wrapper = await mountView(withInfo())
    await flushPromises()
    const configuration = panelsOf(wrapper)[3]

    expect(rowNamed(configuration, 'storage.secretKey')?.element.nextElementSibling?.textContent?.trim()).toBe('********')
  })

  it('does not merge basePath and bypassExternalCache into one group', async () => {
    const wrapper = await mountView(withInfo({ ...FIXTURE, configuration: { local: longLocal, shared: FIXTURE.configuration.shared } }))
    await flushPromises()
    const configuration = panelsOf(wrapper)[3]

    expect(configuration.findAll('[role="rowheader"]').find((term) => term.text() === 'basePath'), 'basePath is a leaf of its own').toBeDefined()
    expect(configuration.findAll('[role="rowheader"]').find((term) => term.text() === 'bypassExternalCache'), 'bypassExternalCache is a leaf of its own').toBeDefined()
    expect(groupNamed(configuration, 'bypass'), 'no group named after the shared first letter').toBeUndefined()
  })
})

// The Plugins tab draws one card per plugin. The tier, origin, dependencies and settings come from the system information.
const plugin = (name: string, tier: string, origin: string, extra: Record<string, unknown> = {}) => ({
  name, version: '1.0.0', dependencies: [], tier, origin, settingsDomain: null, ...extra,
})
const PLUGINS = [
  plugin('zeta', 'community', 'plugins-directory'),
  plugin('alpha', 'core', 'built-in', { version: '2.0.0', dependencies: ['status'], settingsDomain: 'audit' }),
  plugin('beta', 'experimental', 'plugin-store', { version: '0.9.0' }),
  plugin('gamma', 'official', 'bundled-image', { dependencies: ['alpha', 'zeta'], settingsDomain: 'gamma_settings' }),
]
const pluginsTab = async (plugins: ReturnType<typeof plugin>[]) => {
  const wrapper = await mountView(withInfo({ ...FIXTURE, plugins }), '/settings/system-info?tab=plugins')
  await flushPromises()
  return wrapper
}
const pluginSection = (wrapper: Page) => wrapper.find('[aria-labelledby="support-plugins"]')
const cardsIn = (wrapper: Page) => pluginSection(wrapper).findAll('article')
const titlesIn = (wrapper: Page) => cardsIn(wrapper).map(card => card.find('h3').text())
const cardNamed = (wrapper: Page, name: string) => cardsIn(wrapper).find(card => card.find('h3').text() === name)!
// The value of a row of the card's key and value list, read from the element that follows its term.
const valueOf = (card: PanelOf, label: string) =>
  card.findAll('dt').find(term => term.text() === label)?.element.nextElementSibling?.textContent?.trim()

describe('SystemInfoView plugin cards', () => {
  it('draws the plugins heading through the shared section header, labelling the section', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    const section = pluginSection(wrapper)
    expect(section.get('[data-test="section-header"] h2').text()).toBe('Plugins')
    expect(section.attributes('aria-labelledby'), 'the section is labelled by its heading').toBe(section.get('h2').attributes('id'))
  })

  it('draws each plugin as a card titled with its name', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    expect(titlesIn(wrapper), 'one card per plugin, named by the plugin').toHaveLength(4)
    expect(titlesIn(wrapper)).toEqual(expect.arrayContaining(['alpha', 'beta', 'gamma', 'zeta']))
  })

  it('orders the cards Core, Official, Community and Experimental, and by name within each group', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    expect(titlesIn(wrapper)).toEqual(['alpha', 'gamma', 'zeta', 'beta'])
  })

  it('shows the version and the tier badge in the header of each card', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    const alpha = cardNamed(wrapper, 'alpha')
    expect(alpha.text()).toContain('2.0.0')
    expect(alpha.findAll('span').map(badge => badge.text())).toContain('Core')
  })

  it('names where each plugin comes from in words, never a file path', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    expect(valueOf(cardNamed(wrapper, 'alpha'), 'Origin')).toBe('Built into the server')
    expect(valueOf(cardNamed(wrapper, 'gamma'), 'Origin')).toBe('Bundled with the image')
    expect(valueOf(cardNamed(wrapper, 'zeta'), 'Origin')).toBe('Plugins directory')
    expect(valueOf(cardNamed(wrapper, 'beta'), 'Origin')).toBe('Plugin store')
    for (const card of cardsIn(wrapper)) {
      expect(card.text(), `the card ${card.find('h3').text()} shows no jar file`).not.toMatch(/\.jar|\\|\/plugins\//)
    }
  })

  it('lists the dependencies of a plugin by name, and None when it has none', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    expect(valueOf(cardNamed(wrapper, 'gamma'), 'Depends on')).toBe('alpha, zeta')
    expect(valueOf(cardNamed(wrapper, 'beta'), 'Depends on')).toBe('None')
  })

  it('links the settings of a plugin to its settings page', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    const link = cardNamed(wrapper, 'alpha').find('a')
    expect(link.exists(), 'the card of a plugin with settings has a link').toBe(true)
    expect(link.attributes('href')).toBe('/settings/audit')
    expect(link.text()).toBe('Open settings')
  })

  it('shows no settings link for a plugin without settings', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    const card = cardNamed(wrapper, 'zeta')
    expect(card.find('a').exists(), 'the card of a plugin without settings has no link').toBe(false)
    expect(valueOf(card, 'Settings')).toBe('None')
  })

  it('shows the description of a plugin as plain text under its name', async () => {
    const described = plugin('checksum', 'official', 'plugin-store', { description: 'Checks <b>sums</b> of uploads' })
    const wrapper = await pluginsTab([described])

    const card = cardNamed(wrapper, 'checksum')
    const description = card.find('header + p')
    expect(description.exists(), 'the card shows the description right under the header with the name').toBe(true)
    expect(description.text(), 'the description is shown as it was sent').toBe('Checks <b>sums</b> of uploads')
    expect(description.find('b').exists(), 'the description is never read as markup').toBe(false)
    expect(description.classes(), 'the description uses the existing supporting text style').toEqual(
      expect.arrayContaining(['md-body-medium', 'text-on-surface-variant']),
    )
  })

  it('adds no element for a plugin without a description', async () => {
    const wrapper = await pluginsTab([plugin('metrics', 'community', 'plugins-directory')])

    const card = cardNamed(wrapper, 'metrics')
    expect(Array.from(card.element.children).map(child => child.tagName), 'the card holds only its header and its list').toEqual(['HEADER', 'DL'])
  })

  it('adds no element for a plugin with an empty description', async () => {
    const wrapper = await pluginsTab([plugin('metrics', 'community', 'plugins-directory', { description: '' })])

    const card = cardNamed(wrapper, 'metrics')
    expect(Array.from(card.element.children).map(child => child.tagName), 'an empty description draws nothing').toEqual(['HEADER', 'DL'])
  })

  it('lays the cards out in one column on phones, two from medium and three from expanded widths', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    // The first grid of the section is the card grid; the key and value lists of the cards come after it.
    const grid = pluginSection(wrapper).find('.grid')
    expect(grid.classes()).toEqual(expect.arrayContaining(['grid-cols-1', 'medium:grid-cols-2', 'expanded:grid-cols-3']))
    for (const card of cardsIn(wrapper)) {
      expect(card.classes(), `the card ${card.find('h3').text()} may shrink below its content`).toContain('min-w-0')
      expect(card.classes(), `the card ${card.find('h3').text()} wraps its values`).toContain('wrap-anywhere')
    }
  })

  it('shows no filter for twelve plugins or fewer', async () => {
    const wrapper = await pluginsTab(PLUGINS)

    expect(pluginSection(wrapper).find('input[type="search"]').exists()).toBe(false)
  })

  it('shows a filter labelled Filter plugins above more than twelve plugins', async () => {
    const many = Array.from({ length: 13 }, (_, index) => plugin(`metrics-${index}`, 'community', 'plugins-directory'))
    const wrapper = await pluginsTab(many)

    const input = pluginSection(wrapper).find('input[type="search"]')
    expect(input.exists(), 'the filter exists').toBe(true)
    expect(pluginSection(wrapper).findAll('label').map(label => label.text())).toContain('Filter plugins')
  })

  it('narrows the cards to the plugins whose name contains the typed text', async () => {
    const many = [
      ...Array.from({ length: 12 }, (_, index) => plugin(`metrics-${index}`, 'community', 'plugins-directory')),
      plugin('checksum', 'official', 'plugin-store'),
    ]
    const wrapper = await pluginsTab(many)

    await pluginSection(wrapper).find('input[type="search"]').setValue('checksum')

    expect(titlesIn(wrapper)).toEqual(['checksum'])
  })
})
