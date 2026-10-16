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
// The schema the backend serves for the authentication domain, copied from its generated output.
import authenticationSchema from './fixtures/authentication.schema.json'

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

// The staging shape: LDAP switched off, the placeholder host, and the password hidden by the server.
const SERVER = {
  authentication: {
    bruteForceProtection: { enabled: false, maxAttempts: 10, banDurationSeconds: 300 },
    ldap: {
      enabled: false,
      ssl: false,
      hostname: 'ldap.domain.com',
      port: 389,
      baseDn: 'dc=company,dc=com',
      searchUserDn: 'cn=reposilite,ou=admins,dc=domain,dc=com',
      searchUserPassword: '********',
      typeAttribute: 'person',
      userAttribute: 'cn',
      userFilter: '(&(objectClass=person)(ou=Maven Users))',
      userType: 'PERSISTENT'
    }
  },
  maven: { repositories: [] }
}

const SCHEMAS = {
  authentication: authenticationSchema,
  maven: { type: 'object', title: 'Maven', properties: { repositories: { title: 'Repositories', type: 'array', items: { type: 'object', properties: { id: { type: 'string', title: 'Id' } } } } } }
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
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } },
    { name: 'access-sso', path: '/access/sso', component: { render: () => null } },
    { name: 'access-ldap', path: '/access/ldap', component: { render: () => null } }
  ]
})

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const mountOn = async (domain) => {
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

describe('authentication settings tab', () => {
  it('offers the authentication domain as a tab', async () => {
    await mountOn('authentication')

    expect(wrapper.findAll('[role="tab"]').map(tab => tab.text())).toContain('Authentication')
  })

  it('renders the legacy LDAP section with its fields', async () => {
    await mountOn('authentication')
    const text = wrapper.get('[role="tabpanel"]').text()

    expect(text, 'the LDAP section heading').toContain('LDAP')
    for (const label of ['Enabled', 'SSL', 'Hostname', 'Port', 'Base DN', 'Search-User DN', 'Search-User Password', 'Type Attribute', 'User Attribute', 'User Filter', 'User Type']) {
      expect(text, `the LDAP field "${label}"`).toContain(label)
    }
  })

  it('shows the staging hostname in the LDAP hostname field', async () => {
    await mountOn('authentication')

    const hostname = wrapper.findAll('input').find(input => input.element.value === 'ldap.domain.com')
    expect(hostname, 'an input holding the LDAP hostname').toBeDefined()
  })

  it('still renders the danger zone below the form', async () => {
    await mountOn('authentication')

    expect(wrapper.find('#settings-danger-title').text()).toBe('Danger zone')
  })
})
