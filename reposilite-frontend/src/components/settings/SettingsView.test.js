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
    name: { type: 'string', title: 'Name' }
  }
}

const SERVER = {
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', redeployment: false },
      { id: 'snapshots', visibility: 'PRIVATE', redeployment: true }
    ]
  },
  ingot: { name: 'ingot' }
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
    await pressButton('Back to repositories')

    expect(router.currentRoute.value.name).toBe('settings')
    expect(router.currentRoute.value.params.domain).toBe('maven')
  })

  it('selects an entry in the view itself when the router has no entry route yet', async () => {
    const plainRouter = createRouter({
      history: createMemoryHistory(),
      routes: [{ name: 'Index', path: '/:qualifier(.*)', component: { render: () => null } }]
    })
    await mountSettings({ router: plainRouter })

    await itemButtons()[1].trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('snapshots')
  })

  it('adds a new entry, selects it and replaces the address with the new entry', async () => {
    const { router, store } = await mountSettings({ props: { domain: 'maven' } })
    await pressButton('New repository')

    await typeIn('#new-entry-id', 'mirror')
    document.getElementById('new-entry-form').dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(repositoryIds(store)).toEqual(['releases', 'snapshots', 'mirror'])
    expect(router.currentRoute.value.params.entry).toBe('mirror')
    // The router hands the new address back to the view as props; the test does the same.
    await wrapper.setProps({ collection: 'repositories', entry: 'mirror' })
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('mirror')
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
