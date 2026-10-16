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

// The dialog of the one-of renderer is a <dialog>, which jsdom does not implement.
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }

// Session and toast are replaced, so that the store and the view touch neither the network nor the DOM.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

// A repository whose storage provider is one of two branches, as in the real maven schema.
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
          storageProvider: {
            oneOf: [
              {
                type: 'object',
                title: 'File system Storage Provider',
                properties: { type: { type: 'string', const: 'fs' }, quota: { type: 'string', title: 'Quota' } },
                additionalProperties: false
              },
              {
                type: 'object',
                title: 'S3 Storage Provider',
                properties: { type: { type: 'string', const: 's3' }, bucketName: { type: 'string', title: 'Bucket' } },
                additionalProperties: false
              }
            ]
          }
        },
        additionalProperties: false
      }
    }
  }
}

const SERVER = {
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', storageProvider: { type: 's3', bucketName: 'test' } },
      { id: 'snapshots', visibility: 'PRIVATE', storageProvider: { type: 'fs' } }
    ]
  }
}

const fakeClient = (server) => ({
  settings: {
    domains: async () => ({ data: Object.keys(server) }),
    schema: async () => ({ data: MAVEN_SCHEMA }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(server[domain])) }),
    update: async () => ({ data: {} })
  }
})

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
  ]
})

const loadedStore = async (server = SERVER) => {
  const store = createConfigurationStore(ref(fakeClient(server)))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  return store
}

let wrapper

const mountEntries = async ({ props = {}, store } = {}) => {
  const router = namedRouter()
  const viewProps = { selectedTab: 'Settings', domain: 'maven', collection: 'repositories', entry: 'releases', ...props }
  // The address the view is mounted with is the one the router is on, as it is in the app.
  await router.push(viewProps.entry
    ? { name: 'settings-entry', params: { domain: viewProps.domain, collection: viewProps.collection, entry: viewProps.entry } }
    : { name: 'settings', params: { domain: viewProps.domain } })
  const configuration = store ?? await loadedStore()
  wrapper = mount(SettingsView, {
    props: viewProps,
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store: configuration }
}

// The router hands the address back to the view as props. The tests do the same after each navigation.
const followAddress = async (router) => {
  await wrapper.setProps({ entry: router.currentRoute.value.params.entry })
  await flushPromises()
}

const itemButtons = () => wrapper.findAll('li button')
const itemTitles = () => itemButtons().map(button => button.find('span').text())
const idField = () => document.querySelector('#entry-detail-id')
const typeInto = async (input, value) => {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}
const variantSelect = () => wrapper.find('select[id="#/properties/storageProvider-variant"]')
const confirmVariantSwitch = async () => {
  const yes = [...document.querySelectorAll('button')].find(button => button.textContent.trim() === 'Switch')
  yes.click()
  await flushPromises()
}
const repositoryIds = (store) => store.configurations.value.maven.repositories.map(entry => entry.id)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('SettingsView storage variant', () => {
  it('keeps the id of the open entry when its storage variant is switched', async () => {
    const { store } = await mountEntries()

    await variantSelect().setValue('0')
    await confirmVariantSwitch()

    expect(repositoryIds(store), 'the entry keeps its address through a variant switch').toEqual(['releases', 'snapshots'])
  })

  it('keeps the other fields of the entry when its storage variant is switched', async () => {
    const { store } = await mountEntries()

    await variantSelect().setValue('0')
    await confirmVariantSwitch()

    expect(store.configurations.value.maven.repositories[0].visibility).toBe('PUBLIC')
  })
})

describe('SettingsView rename', () => {
  it('shows the new id in the id field and moves the address to it', async () => {
    const { router, store } = await mountEntries()

    await typeInto(idField(), 'mirror')

    expect(repositoryIds(store)).toEqual(['mirror', 'snapshots'])
    expect(router.currentRoute.value.params.entry).toBe('mirror')
  })

  it('keeps the same item selected and on screen across a rename', async () => {
    const { router } = await mountEntries()
    const item = itemButtons()[0].element

    await typeInto(idField(), 'mirror')
    await followAddress(router)

    expect(itemButtons()[0].element, 'the item is the same element').toBe(item)
    expect(itemButtons()[0].attributes('aria-current')).toBe('true')
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('mirror')
  })

  it('does not move the address while the new id is taken, and says why on the field', async () => {
    const { router, store } = await mountEntries()

    await typeInto(idField(), 'snapshots')

    expect(router.currentRoute.value.params.entry).toBe('releases')
    expect(wrapper.get('[data-test="entry-title"]').text(), 'the open entry stays open').toBe('snapshots')
    expect(wrapper.text()).toContain('A repository with this id already exists.')
    expect(store.invalidDomains.value).toEqual(['maven'])
  })

  it('refuses to save an entry whose id is cleared, and the action bar says so', async () => {
    await mountEntries()

    await typeInto(idField(), '')

    expect(wrapper.text()).toContain('Fix the errors before saving.')
    expect(wrapper.findAll('button').find(button => button.text() === 'Save').attributes('disabled')).toBeDefined()
  })
})

describe('SettingsView entries without a usable id', () => {
  it('lists an entry without an id as unnamed, with the reason, and not as an empty item', async () => {
    const store = await loadedStore({ maven: { repositories: [{ visibility: 'PUBLIC' }, SERVER.maven.repositories[1]] } })
    await mountEntries({ store, props: { entry: 'snapshots' } })

    expect(itemTitles()).toEqual(['Unnamed repository', 'snapshots'])
    expect(itemButtons()[0].text()).toContain('Enter an id.')
  })

  it('opens an unnamed entry by its key and names it, which moves the address to the new id', async () => {
    const store = await loadedStore({ maven: { repositories: [{ visibility: 'PUBLIC' }, SERVER.maven.repositories[1]] } })
    const { router } = await mountEntries({ store, props: { entry: 'snapshots' } })

    await itemButtons()[0].trigger('click')
    await flushPromises()
    await followAddress(router)

    expect(router.currentRoute.value.params.entry.startsWith('~'), 'the unnamed entry is addressed by its key').toBe(true)
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('Unnamed repository')

    await typeInto(idField(), 'named')
    await followAddress(router)

    expect(repositoryIds(store)).toEqual(['named', 'snapshots'])
    expect(router.currentRoute.value.params.entry).toBe('named')
    expect(wrapper.get('[data-test="entry-title"]').text()).toBe('named')
  })
})

describe('SettingsView action bar', () => {
  it('shows the action bar right after an entry is created', async () => {
    // Outside the maven domain a collection has no guided flow, so "New entry" opens the id dialog.
    const server = { ingot: { repositories: [{ id: 'releases', visibility: 'PUBLIC', storageProvider: { type: 'fs' } }] } }
    await mountEntries({ props: { domain: 'ingot', entry: undefined }, store: await loadedStore(server) })
    await wrapper.find('[data-test="new-entry"]').trigger('click')
    await flushPromises()

    document.querySelector('#new-entry-id').value = 'mirror'
    document.querySelector('#new-entry-id').dispatchEvent(new Event('input'))
    await flushPromises()
    document.getElementById('new-entry-form').dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(wrapper.text()).toContain('1 unsaved change')
    expect(wrapper.findAll('[role="status"]').some(status => status.text().includes('Save'))).toBe(true)
  })
})
