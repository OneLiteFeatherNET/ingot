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

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

// The authentication domain is edited under Access, so the settings tabs must leave it out.
const SERVER = {
  authentication: { bruteForceProtection: { enabled: false, maxAttempts: 10, banDurationSeconds: 300 } },
  maven: { repositories: [] },
  ingot: {}
}

const SCHEMAS = {
  authentication: { type: 'object', title: 'Authentication', properties: { bruteForceProtection: { type: 'object', title: 'Brute force protection' } } },
  maven: { type: 'object', title: 'Maven', properties: { repositories: { title: 'Repositories', type: 'array', items: { type: 'object', properties: { id: { type: 'string', title: 'Id' } } } } } },
  ingot: { type: 'object', title: 'Ingot', properties: {} }
}

const fakeClient = () => ({
  settings: {
    domains: async () => ({ data: Object.keys(SERVER) }),
    schema: async (domain) => ({ data: SCHEMAS[domain] }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(SERVER[domain])) }),
    update: async () => ({ data: {} })
  }
})

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: stubRoutes()
})

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const mountSettings = async (domain = 'maven') => {
  const store = createConfigurationStore(ref(fakeClient()))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  store.selectedDomain.value = domain
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', domain },
    global: {
      plugins: [namedRouter()],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

describe('settings tabs without authentication', () => {
  it('does not offer an Authentication tab, because the domain is edited under Access', async () => {
    await mountSettings('maven')

    const links = wrapper.findAll('[aria-label="Administration"] a').map(link => link.text())
    expect(links, 'the domains are in the Administration tree').toEqual(expect.arrayContaining(['Maven', 'Ingot']))
    expect(links, 'no Authentication link, the domain is edited under Access').not.toContain('Authentication')
  })

  it('still renders the danger zone below the page', async () => {
    await mountSettings('maven')

    expect(wrapper.find('#settings-danger-title').text()).toBe('Danger zone')
  })
})
