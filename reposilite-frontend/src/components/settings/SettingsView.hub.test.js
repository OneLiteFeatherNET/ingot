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

import { stubRoutes } from './fixtures/routes'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import { ADMIN_NODES } from '../../store/nodeAccess'
import SettingsView from './SettingsView.vue'
import SettingsReviewSheet from './SettingsReviewSheet.vue'
import audit from './fixtures/audit.schema.json'
import authentication from './fixtures/authentication.schema.json'
import cleanup from './fixtures/cleanup.schema.json'
import frontend from './fixtures/frontend.schema.json'
import javadoc from './fixtures/javadoc.schema.json'
import maven from './fixtures/maven.schema.json'
import statistics from './fixtures/statistics.schema.json'
import web from './fixtures/web.schema.json'

// What the visitor holds; each test sets it before it mounts the view. The dashboard node is set on its own.
const session = vi.hoisted(() => ({ mayEdit: true, mayDashboard: true }))
vi.mock('../../store/session', () => ({
  useSession: () => ({
    client: { value: null },
    can: (node) => (node === ADMIN_NODES.dashboard ? session.mayDashboard : session.mayEdit)
  })
}))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

// The fixture server: the schemas the backend serves, plus a plugin domain the registry does not name.
const login = { type: 'object', title: 'Login', description: 'Sign in providers', properties: {} }

const SCHEMAS = {
  maven,
  javadoc,
  cleanup,
  web,
  frontend,
  statistics,
  audit,
  authentication,
  login,
  acme: { type: 'object', title: 'Acme', description: 'A plugin domain', properties: {} }
}

const sixRepositories = () => Array.from({ length: 6 }, (_, index) => ({ id: `repo-${index}` }))

// The fake client counts every call it answers, so a test can assert which requests a page makes. `hang` makes the
// domain list never arrive, so a test can look at the page while it loads.
const fakeClient = (names = Object.keys(SCHEMAS), { hang = false } = {}) => {
  const update = vi.fn()
  const calls = []
  return {
    update,
    calls,
    settings: {
      domains: async () => {
        calls.push('domains')
        if (hang) return new Promise(() => {})
        return { data: names }
      },
      schema: async (domain) => {
        calls.push(`schema:${domain}`)
        return { data: SCHEMAS[domain] }
      },
      fetch: async (domain) => {
        calls.push(`fetch:${domain}`)
        return { data: domain === 'maven' ? { repositories: sixRepositories() } : {} }
      },
      update
    }
  }
}

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: stubRoutes()
})

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  session.mayEdit = true
  session.mayDashboard = true
})

// Mounts the view at an address. The configuration is loaded before the mount unless `loaded` is false, in which case the
// view loads it itself, as it does when a visitor opens the page.
const mountAt = async ({ path = '/settings', names, props = {}, loaded = true, hang = false } = {}) => {
  const client = fakeClient(names, { hang })
  const store = createConfigurationStore(ref(client))
  if (loaded) await store.fetchConfiguration()
  const router = namedRouter()
  await router.push(path)
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', ...props },
    global: { plugins: [router], provide: { [configurationStoreKey]: store } },
    attachTo: document.body
  })
  await flushPromises()
  return { store, router, client }
}

const overview = () => wrapper.get('[data-test="settings-hub"]')
const attention = () => wrapper.get('[data-test="settings-attention"]')
const treeEntries = () => wrapper.get('nav[aria-label="Administration"]')

describe('the Overview page at /settings', () => {
  it('draws no card grid: the Administration pages are listed only in the tree', async () => {
    await mountAt()

    expect(wrapper.find('[data-test="settings-card"]').exists(), 'no card of a page or domain').toBe(false)
    expect(overview().findAll('a').map(link => link.attributes('href')), 'the only link of the content is the dashboard link').toEqual(['/dashboard'])
  })

  it('marks the Overview entry of the tree as current, and no other entry', async () => {
    await mountAt()

    expect(treeEntries().findAll('[aria-current="page"]').map(link => link.text()), 'the hub marks only its Overview entry').toEqual(['Overview'])
  })

  it('lists Overview first in the tree, before the first group', async () => {
    await mountAt()

    expect(treeEntries().findAll('a')[0].text(), 'the first entry of the tree').toBe('Overview')
  })

  it('draws the page header as Overview with the shared configuration line', async () => {
    await mountAt()

    expect(wrapper.get('h1').text()).toBe('Overview')
    expect(wrapper.text()).toContain('Shared configuration for all instances.')
  })

  it('reads "Nothing needs attention." when no domain has a draft', async () => {
    await mountAt()

    expect(attention().text()).toContain('Nothing needs attention.')
  })

  it('lists a domain with a draft under Needs attention, linking to its page, with the status in text', async () => {
    const { store } = await mountAt()
    store.setDomainData('maven', { repositories: sixRepositories().slice(0, 2) })
    await flushPromises()

    const row = attention().get('[data-test="attention-row"]')
    expect(row.attributes('href'), 'the Repositories row links to the Repositories page').toBe('/settings/repositories')
    expect(row.attributes('aria-label')).toBe('Repositories, Unsaved changes')
    expect(row.text()).toContain('Unsaved changes')
  })

  it('opens the review of all drafts from Needs attention without saving anything', async () => {
    const { store, client } = await mountAt()
    store.setDomainData('maven', { repositories: sixRepositories().slice(0, 2) })
    await flushPromises()

    const review = attention().findAll('button').find(button => button.text() === 'Review changes')
    await review.trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(SettingsReviewSheet).props('open'), 'the review sheet opens').toBe(true)
    expect(client.update, 'nothing is saved by opening the review').not.toHaveBeenCalled()
  })

  it('opens a Needs attention row without writing the draft', async () => {
    const { store, router, client } = await mountAt()
    store.setDomainData('maven', { repositories: sixRepositories().slice(0, 2) })
    await flushPromises()
    const before = JSON.stringify(store.configurations.value)

    await attention().get('[data-test="attention-row"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/repositories')
    expect(JSON.stringify(store.configurations.value), 'the draft is unchanged').toBe(before)
    expect(client.update, 'nothing is saved').not.toHaveBeenCalled()
  })

  it('draws At a glance with the counts of the saved collections, rendered after the saved settings load', async () => {
    await mountAt()

    const glance = overview().get('dl')
    expect(glance.findAll('dt').map(term => term.text()), 'the labels of the tree').toEqual(expect.arrayContaining(['Repositories', 'Cleanup policies']))
    expect(glance.findAll('dd').map(value => value.text()), 'the saved counts').toEqual(expect.arrayContaining(['6', '0']))
  })

  it('draws no count before the saved settings have loaded', async () => {
    await mountAt({ loaded: false, hang: true })

    expect(wrapper.find('dl').exists(), 'no count is drawn while the settings load').toBe(false)
    expect(wrapper.text()).toContain('Loading configuration')
  })

  it('draws the dashboard link to a visitor with the dashboard node', async () => {
    await mountAt()

    const link = wrapper.get('[data-test="settings-hub-dashboard"]')
    expect(link.attributes('href')).toBe('/dashboard')
  })

  it('draws no dashboard link without the dashboard node', async () => {
    session.mayDashboard = false
    await mountAt()

    expect(wrapper.find('[data-test="settings-hub-dashboard"]').exists()).toBe(false)
  })

  it('ends with no line that sends sign in and security to Access, since the tree lists it', async () => {
    await mountAt()

    expect(wrapper.find('[data-test="settings-hub-access"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Sign-in and security live in')
  })

  it('draws the Administration tree beside the Overview, with the entries of the other Administration pages', async () => {
    await mountAt()

    const entries = treeEntries().findAll('a').map(link => link.text())
    expect(entries, 'the page entries of the tree').toEqual(expect.arrayContaining(['Repositories', 'Audit log']))
    expect(entries, 'the settings domains of the tree, under the label of the frontend').toEqual(expect.arrayContaining(['Reverse proxy']))
    expect(entries, 'Maven lives on the Repositories page, not in the tree').not.toContain(SCHEMAS.maven.title)
  })

  it('shows no Overview content to a visitor who may not edit Settings', async () => {
    session.mayEdit = false
    await mountAt()

    expect(wrapper.find('[data-test="settings-hub"]').exists(), 'no Overview root').toBe(false)
    expect(wrapper.find('[data-test="settings-attention"]').exists(), 'no Needs attention').toBe(false)
  })

  it('shows no hub when the address names a domain', async () => {
    await mountAt({ path: '/settings/web', props: { domain: 'web' } })

    expect(wrapper.find('[data-test="settings-hub"]').exists()).toBe(false)
  })

  it('forwards to the one domain a visitor sees instead of showing the hub', async () => {
    await mountAt({ names: ['maven', 'login'] })

    expect(wrapper.find('[data-test="settings-hub"]').exists()).toBe(false)
    expect(wrapper.emitted('update:domain')?.at(-1), 'the address is replaced with the only domain').toEqual(['maven'])
  })
})

describe('the settings file actions', () => {
  it('draws Import settings and Download as JSON in the header of Overview', async () => {
    await mountAt()

    const header = wrapper.get('header')
    expect(header.findAll('button').map(button => button.text()), 'the two actions of the whole configuration').toEqual(expect.arrayContaining(['Import settings', 'Download as JSON']))
  })

  it('draws neither action on a domain page', async () => {
    await mountAt({ path: '/settings/javadoc', props: { domain: 'javadoc' } })

    const labels = wrapper.get('header').findAll('button').map(button => button.text())
    expect(labels, 'no Import settings').not.toContain('Import settings')
    expect(labels, 'no Download as JSON').not.toContain('Download as JSON')
  })

  it('draws neither action on the Repositories page', async () => {
    await mountAt({ path: '/settings/repositories' })

    const labels = wrapper.get('header').findAll('button').map(button => button.text())
    expect(labels).not.toContain('Import settings')
    expect(labels).not.toContain('Download as JSON')
  })

  it('draws no filled button in the page header', async () => {
    await mountAt()

    const filled = wrapper.get('header').findAll('button').filter(button => button.classes().includes('bg-primary'))
    expect(filled, 'the header has no filled button').toEqual([])
  })
})

describe('the requests of the Overview page', () => {
  it('makes only the requests the Settings page made before Overview, one domain list and one schema and one fetch per domain', async () => {
    const { client } = await mountAt({ loaded: false })

    const domains = Object.keys(SCHEMAS)
    const expected = ['domains', ...domains.map(domain => `schema:${domain}`), ...domains.map(domain => `fetch:${domain}`)]
    expect([...client.calls].sort(), 'the reads of the page, and no other').toEqual([...expected].sort())
    expect(client.update, 'no write on Overview').not.toHaveBeenCalled()
  })
})
