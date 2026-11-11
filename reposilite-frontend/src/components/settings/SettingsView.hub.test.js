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
import SettingsView from './SettingsView.vue'
import audit from './fixtures/audit.schema.json'
import authentication from './fixtures/authentication.schema.json'
import cleanup from './fixtures/cleanup.schema.json'
import frontend from './fixtures/frontend.schema.json'
import javadoc from './fixtures/javadoc.schema.json'
import maven from './fixtures/maven.schema.json'
import statistics from './fixtures/statistics.schema.json'
import web from './fixtures/web.schema.json'

// Whether the visitor holds the settings node; each test sets it before it mounts the view.
const session = vi.hoisted(() => ({ mayEdit: true }))
vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { value: null }, can: () => session.mayEdit })
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

const fakeClient = (names = Object.keys(SCHEMAS)) => {
  const update = vi.fn()
  return {
    update,
    settings: {
      domains: async () => ({ data: names }),
      schema: async (domain) => ({ data: SCHEMAS[domain] }),
      fetch: async (domain) => ({ data: domain === 'maven' ? { repositories: sixRepositories() } : {} }),
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
})

const mountHub = async ({ names, props = {} } = {}) => {
  const client = fakeClient(names)
  const store = createConfigurationStore(ref(client))
  await store.fetchConfiguration()
  const router = namedRouter()
  await router.push('/settings')
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', ...props },
    global: { plugins: [router], provide: { [configurationStoreKey]: store } },
    attachTo: document.body
  })
  await flushPromises()
  return { store, router, client }
}

const hub = () => wrapper.get('[data-test="settings-hub"]')
const group = (heading) => hub().get(`[role="group"][aria-label="${heading}"]`)
const cardTitles = (heading) => group(heading).findAll('[data-test="settings-card"] h3').map(card => card.text())

describe('the settings hub at /settings', () => {
  it('groups the domains under Repository and System, with the Repositories page first in Repository', async () => {
    await mountHub()

    expect(hub().findAll('[role="group"]').map(entry => entry.attributes('aria-label'))).toEqual(expect.arrayContaining(['Repository', 'System']))
    expect(cardTitles('Repository'), 'the page of the repositories, then the domains of the group').toEqual(['Repositories', SCHEMAS.maven.title, SCHEMAS.javadoc.title, SCHEMAS.cleanup.title])
    expect(cardTitles('System'), 'the other domains, plugin domains included').toEqual([SCHEMAS.web.title, SCHEMAS.frontend.title, SCHEMAS.statistics.title, SCHEMAS.audit.title, 'Acme'])
  })

  it('shows no card for the domains that are edited under Access', async () => {
    await mountHub()

    const titles = hub().findAll('[data-test="settings-card"] h3').map(card => card.text())
    expect(titles).not.toContain(SCHEMAS.login.title)
    expect(titles).not.toContain(SCHEMAS.authentication.title)
  })

  it('shows the saved value of a collection domain, such as "6 repositories"', async () => {
    await mountHub()

    expect(group('Repository').text()).toContain('6 repositories')
  })

  it('marks a domain with a draft change as unsaved and keeps its saved value', async () => {
    const { store } = await mountHub()
    store.setDomainData('maven', { repositories: sixRepositories().slice(0, 2) })
    await flushPromises()

    const mavenCard = group('Repository').findAll('[data-test="settings-card"]').find(card => card.find('h3').text() === SCHEMAS.maven.title)
    expect(mavenCard.text()).toContain('Unsaved changes')
    expect(mavenCard.text(), 'the card shows the saved value, not the draft').toContain('6 repositories')
    expect(group('System').text(), 'a domain without a change is not marked').not.toContain('Unsaved changes')
  })

  it('shows only the groups the visitor may edit', async () => {
    session.mayEdit = false
    await mountHub()

    expect(wrapper.find('[data-test="settings-hub"] [role="group"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="settings-card"]').exists()).toBe(false)
  })

  it('ends with a line that links sign in and security to Access', async () => {
    const { router } = await mountHub()

    const link = hub().get('[data-test="settings-hub-access"] a')
    expect(link.text()).toContain('Access')
    expect(link.attributes('href')).toBe(router.resolve({ name: 'access' }).href)
  })

  it('opens a domain from its card without writing the draft', async () => {
    const { store, router, client } = await mountHub()
    const before = JSON.stringify(store.configurations.value)

    await group('Repository').findAll('[data-test="settings-card"]').find(card => card.find('h3').text() === SCHEMAS.maven.title).trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven')
    expect(JSON.stringify(store.configurations.value), 'the draft is unchanged').toBe(before)
    expect(store.hasUnsavedChanges.value).toBe(false)
    expect(client.update, 'nothing is saved').not.toHaveBeenCalled()
  })

  it('shows no hub when the address names a domain', async () => {
    await mountHub({ props: { domain: 'web' } })

    expect(wrapper.find('[data-test="settings-hub"]').exists()).toBe(false)
  })

  it('forwards to the one domain a visitor sees instead of showing the hub', async () => {
    await mountHub({ names: ['maven', 'login'] })

    expect(wrapper.find('[data-test="settings-hub"]').exists()).toBe(false)
    expect(wrapper.emitted('update:domain')?.at(-1), 'the address is replaced with the only domain').toEqual(['maven'])
  })
})
