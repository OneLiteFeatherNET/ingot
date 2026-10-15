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
import { defineComponent, h, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter, useRoute, useRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'

// Session and toast are replaced, so that the store and the view touch neither the network nor the DOM.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const MAVEN_SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      description: 'List of Maven repositories.',
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' },
          redeployment: { type: 'boolean', title: 'Redeployment' }
        },
        additionalProperties: false
      }
    }
  }
}

const INGOT_SCHEMA = {
  type: 'object',
  title: 'Ingot',
  properties: {
    name: { type: 'string', title: 'Name' },
    // A collection without a guided flow: the maven repositories are created through the wizard.
    mirrors: {
      title: 'Mirrors',
      type: 'array',
      items: {
        type: 'object',
        title: 'Mirror',
        properties: { id: { type: 'string', title: 'Id' } },
        additionalProperties: false
      }
    }
  }
}

const SERVER = {
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', redeployment: false },
      { id: 'snapshots', visibility: 'PRIVATE', redeployment: true }
    ]
  },
  ingot: { name: 'ingot', mirrors: [{ id: 'central' }] }
}

const SCHEMAS = { maven: MAVEN_SCHEMA, ingot: INGOT_SCHEMA }

const fakeClient = () => ({
  server: SERVER,
  settings: {
    domains: async () => ({ data: Object.keys(SERVER) }),
    schema: async (domain) => ({ data: SCHEMAS[domain] }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(SERVER[domain])) }),
    update: async () => ({ data: {} })
  }
})

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } },
    { name: 'home', path: '/', component: { render: () => null } }
  ]
})

const loadedStore = async () => {
  const store = createConfigurationStore(ref(fakeClient()))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  return store
}

let wrapper

const mountSettings = async ({ props = {}, router = namedRouter(), store } = {}) => {
  const configuration = store ?? await loadedStore()
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', ...props },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store: configuration }
}

const itemButtons = () => wrapper.findAll('li button')
const itemIds = () => itemButtons().map(button => button.find('span').text())
const pressButton = async (name) => {
  await wrapper.findAll('button').find(button => button.text() === name).trigger('click')
  await flushPromises()
}
const typeIn = async (selector, value) => {
  const input = document.querySelector(selector)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}
const repositoryIds = (store) => store.configurations.value.maven.repositories.map(entry => entry.id)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('SettingsView page', () => {
  it('shows the title Settings and a single line that says the configuration is shared', async () => {
    await mountSettings()

    expect(wrapper.get('h1').text()).toBe('Settings')
    expect(wrapper.text()).toContain('Shared configuration for all instances.')
  })

  it('names a single domain as a section title, without tabs', async () => {
    const store = await loadedStore()
    store.domains.value = ['maven']
    await mountSettings({ store })

    expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
    expect(wrapper.get('h2').text()).toBe('Maven')
  })

  it('shows one secondary tab per domain when there is more than one', async () => {
    await mountSettings()

    expect(wrapper.findAll('[role="tab"]').map(tab => tab.text())).toEqual(['Maven', 'Ingot'])
  })

  it('puts the factory reset in a danger zone at the end of the page', async () => {
    await mountSettings()

    const zone = wrapper.get('section[aria-labelledby="settings-danger-title"]')
    expect(zone.text()).toContain('Danger zone')
    expect(zone.findAll('button').map(button => button.text())).toContain('Factory reset')
    const collection = wrapper.get('section[aria-labelledby="repositories-title"]')
    expect(collection.element.compareDocumentPosition(zone.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('hides the action bar while nothing is unsaved', async () => {
    await mountSettings()

    expect(wrapper.text()).not.toContain('unsaved change')
  })
})

describe('SettingsView collections', () => {
  it('lists the entries of a collection under the collection title', async () => {
    await mountSettings()

    expect(wrapper.get('[aria-labelledby="repositories-title"] h2').text()).toBe('Repositories')
    expect(itemIds()).toEqual(['releases', 'snapshots'])
  })

  it('pushes the entry address when an entry is selected', async () => {
    const { router } = await mountSettings({ props: { domain: 'maven' } })

    await itemButtons()[1].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settings-entry')
    expect(router.currentRoute.value.params).toEqual({ domain: 'maven', collection: 'repositories', entry: 'snapshots' })
  })

  it('selects the entry named by the address', async () => {
    await mountSettings({ props: { domain: 'maven', collection: 'repositories', entry: 'snapshots' } })

    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('snapshots')
    expect(itemButtons()[1].attributes('aria-current')).toBe('true')
  })

  it('says the repository was not found when the address names no entry, and links back to the list', async () => {
    const { router } = await mountSettings({ props: { domain: 'maven', collection: 'repositories', entry: 'ghost' } })

    expect(wrapper.text()).toContain('Repository not found')
    const back = wrapper.findAll('a').find(link => link.text() === 'Back to repositories')
    expect(back?.attributes('href'), 'the not-found link goes to the settings route of the domain').toBe('/settings/maven')

    await back.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settings')
    expect(router.currentRoute.value.params.domain).toBe('maven')
  })

  it('adds a new entry, selects it and replaces the address with the new entry', async () => {
    const { router, store } = await mountSettings({ props: { domain: 'ingot' } })
    await pressButton('New mirror')

    await typeIn('#new-entry-id', 'backup')
    document.getElementById('new-entry-form').dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(store.configurations.value.ingot.mirrors.map(entry => entry.id)).toEqual(['central', 'backup'])
    expect(router.currentRoute.value.params.entry).toBe('backup')
    // The router hands the new address back to the view as props; the test does the same.
    await wrapper.setProps({ collection: 'mirrors', entry: 'backup' })
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('backup')
  })

  it('removes an entry only after its id is typed exactly, then returns to the list', async () => {
    const { router, store } = await mountSettings({ props: { domain: 'maven', collection: 'repositories', entry: 'snapshots' } })
    await pressButton('Delete repository')

    await typeIn('#delete-entry-confirm-input', 'snapshot')
    expect(document.getElementById('delete-entry-confirm').hasAttribute('disabled')).toBe(true)
    await typeIn('#delete-entry-confirm-input', 'snapshots')
    document.getElementById('delete-entry-confirm').click()
    await flushPromises()

    expect(repositoryIds(store)).toEqual(['releases'])
    expect(router.currentRoute.value.name).toBe('settings')
  })
})

describe('SettingsView unsaved changes', () => {
  it('counts an edit in the action bar and drops it when the edit is discarded', async () => {
    const { store } = await mountSettings({ props: { domain: 'maven', collection: 'repositories', entry: 'releases' } })

    await wrapper.get('select').setValue('PRIVATE')
    await flushPromises()

    expect(wrapper.text()).toContain('1 unsaved change')
    expect(store.hasUnsavedChanges.value).toBe(true)

    await pressButton('Discard')

    expect(store.configurations.value.maven.repositories[0].visibility).toBe('PUBLIC')
    expect(wrapper.text()).not.toContain('unsaved change')
  })

  it('keeps the edit in the store when the view is closed, so the bar comes back with the draft', async () => {
    const { store } = await mountSettings({ props: { domain: 'maven', collection: 'repositories', entry: 'releases' } })
    await wrapper.get('select').setValue('PRIVATE')
    wrapper.unmount()
    wrapper = undefined

    await mountSettings({ store, props: { domain: 'maven', collection: 'repositories', entry: 'releases' } })

    expect(wrapper.text()).toContain('1 unsaved change')
  })
})

describe('SettingsView driven by the router', () => {
  // The page as IndexPage runs it: the view is shown on the settings routes only, the route params go
  // in as props, and a domain the view reports is written back with a replace. The view keeps no
  // selection of its own.
  const RouteHost = defineComponent({
    setup() {
      const route = useRoute()
      const router = useRouter()
      return () => (route.name?.startsWith('settings')
        ? h(SettingsView, {
          selectedTab: 'Settings',
          domain: route.params.domain,
          collection: route.params.collection,
          entry: route.params.entry,
          'onUpdate:domain': (domain) => router.replace({ name: 'settings', params: { domain } })
        })
        : null)
    }
  })

  // The paths are visited in order, so the history holds them the way a user would have made them.
  const mountAt = async (paths) => {
    const router = namedRouter()
    for (const path of paths) {
      await router.push(path)
    }
    await router.isReady()
    const store = await loadedStore()
    wrapper = mount(RouteHost, {
      global: {
        plugins: [router],
        provide: { [configurationStoreKey]: store }
      }
    })
    await flushPromises()
    return { router, store }
  }

  const clickButtonText = async (text) => {
    await wrapper.findAll('button').find(button => button.text() === text).trigger('click')
    await flushPromises()
  }

  it('selects the entry the address names when the page is opened', async () => {
    await mountAt(['/settings/maven/repositories/snapshots'])

    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('snapshots')
  })

  it('pushes the address of an entry that is selected', async () => {
    const { router } = await mountAt(['/', '/settings/maven'])

    await itemButtons()[0].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven/repositories/releases')
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('releases')
  })

  it('returns to the previous selection with the back button', async () => {
    const { router } = await mountAt(['/', '/settings/maven/repositories/releases'])
    await itemButtons()[1].trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('snapshots')

    router.back()
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven/repositories/releases')
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('releases')
  })

  it('writes the domain into an address that names none, without adding a history entry', async () => {
    const { router } = await mountAt(['/', '/settings'])

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven')
    router.back()
    await flushPromises()
    expect(router.currentRoute.value.fullPath, 'the address was replaced, so back leaves the settings page').toBe('/')
  })

  it('writes a domain picked in the tabs into the address without adding a history entry', async () => {
    const { router } = await mountAt(['/', '/settings/maven'])
    await wrapper.findAll('[role="tab"]').find(tab => tab.text() === 'Ingot').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/ingot')
    router.back()
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('corrects an address that names a domain the server does not have, once, to the first domain', async () => {
    const { router } = await mountAt(['/', '/settings/ghost'])

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven')
    expect(itemIds(), 'the first domain is the one on screen').toEqual(['releases', 'snapshots'])
  })

  it('leads the not-found pane back to the domain list through the router', async () => {
    const { router } = await mountAt(['/', '/settings/maven/repositories/ghost'])
    expect(wrapper.text()).toContain('Repository not found')

    await wrapper.findAll('a').find(link => link.text() === 'Back to repositories').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven')
  })

  it('returns to the domain list when the selected entry is deleted', async () => {
    const { router, store } = await mountAt(['/', '/settings/maven/repositories/snapshots'])
    await clickButtonText('Delete repository')
    await typeIn('#delete-entry-confirm-input', 'snapshots')
    document.getElementById('delete-entry-confirm').click()
    await flushPromises()

    expect(repositoryIds(store)).toEqual(['releases'])
    expect(router.currentRoute.value.fullPath).toBe('/settings/maven')
  })
})
