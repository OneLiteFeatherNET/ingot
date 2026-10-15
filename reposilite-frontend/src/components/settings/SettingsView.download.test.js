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
import download from 'downloadjs'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import { settingsClockKey } from './settingsExport'
import SettingsView from './SettingsView.vue'

vi.mock('downloadjs', () => ({ default: vi.fn() }))
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' }
        },
        additionalProperties: false
      }
    }
  }
}

const SERVER = {
  maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] }
}

const fakeClient = () => ({
  settings: {
    domains: async () => ({ data: Object.keys(SERVER) }),
    schema: async () => ({ data: SCHEMA }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(SERVER[domain])) }),
    update: async () => ({ data: {} })
  }
})

const FIXED_NOW = new Date(2026, 9, 15, 12, 0)

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
  ]
})

let wrapper

const mountWith = async ({ secretsHidden = false } = {}) => {
  const store = createConfigurationStore(ref(fakeClient()), { secretsHidden: () => secretsHidden })
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings' },
    global: {
      plugins: [namedRouter()],
      provide: { [configurationStoreKey]: store, [settingsClockKey]: () => FIXED_NOW }
    },
    attachTo: document.body
  })
  await flushPromises()
  return store
}

const openMenu = async () => {
  await wrapper.get('button[aria-haspopup="menu"]').trigger('click')
}

const choose = async (name) => {
  const item = wrapper.findAll('[role="menuitem"]').find(entry => entry.text().includes(name))
  expect(item, `the menu offers "${name}"`).toBeDefined()
  await item.trigger('click')
}

const downloadedJson = () => JSON.parse(download.mock.calls.at(-1)[0])

const editFirstRepository = (store) => {
  store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE' })
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('SettingsView download', () => {
  it('downloads the saved state, not the draft, when unsaved edits exist', async () => {
    const store = await mountWith()
    editFirstRepository(store)
    await flushPromises()

    await openMenu()
    await choose('Download saved settings')

    expect(downloadedJson()).toEqual(SERVER)
  })

  it('names the saved download after the day of the injected clock', async () => {
    await mountWith()

    await openMenu()
    await choose('Download saved settings')

    expect(download.mock.calls.at(-1)[1]).toBe('ingot-settings-2026-10-15.json')
  })

  it('downloads the draft only when the unsaved option is chosen', async () => {
    const store = await mountWith()
    editFirstRepository(store)
    await flushPromises()

    await openMenu()
    await choose('Download including unsaved changes')

    expect(downloadedJson().maven.repositories[0].visibility).toBe('PRIVATE')
    expect(download.mock.calls.at(-1)[1]).toBe('ingot-settings-2026-10-15.json')
  })

  it('keeps the saved download available while the draft has invalid entries', async () => {
    const store = await mountWith()
    store.setEntry('maven', 'repositories', 0, { id: '', visibility: 'PRIVATE' })
    await flushPromises()

    await openMenu()
    const savedItem = wrapper.findAll('[role="menuitem"]').find(entry => entry.text().includes('Download saved settings'))

    expect(savedItem.attributes('disabled')).toBeUndefined()
  })

  it('says secrets are not included when the server hides them', async () => {
    await mountWith({ secretsHidden: true })

    await openMenu()

    expect(wrapper.find('[role="menu"]').text()).toContain('Secrets are not included')
  })
})
