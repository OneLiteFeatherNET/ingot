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
import { createAjv } from '@jsonforms/core'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'
// The schemas the backend serves for every settings domain, copied from its generated output (see the fixtures folder).
import audit from './fixtures/audit.schema.json'
import authentication from './fixtures/authentication.schema.json'
import cleanup from './fixtures/cleanup.schema.json'
import frontend from './fixtures/frontend.schema.json'
import javadoc from './fixtures/javadoc.schema.json'
import maven from './fixtures/maven.schema.json'
import statistics from './fixtures/statistics.schema.json'
import web from './fixtures/web.schema.json'

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMAS = { audit, authentication, cleanup, frontend, javadoc, maven, statistics, web }

// The titles a user must find on the page for a domain: every field, every group heading and every list heading.
// Only the defaults are stored, so the form fills in the rest, as it does for a domain that was never edited.
const titlesOf = (schema) => Object.entries(schema.properties ?? {}).flatMap(([name, property]) => {
  const title = property.title ?? name
  const nested = property.type === 'object' && property.properties ? titlesOf(property) : []
  return [title, ...nested]
})

const fakeClient = () => ({
  settings: {
    domains: async () => ({ data: Object.keys(SCHEMAS) }),
    schema: async (domain) => ({ data: SCHEMAS[domain] }),
    fetch: async () => ({ data: {} }),
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

const mountDomain = async (domain) => {
  const store = createConfigurationStore(ref(fakeClient()))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
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

describe.each(Object.keys(SCHEMAS))('the %s domain form', (domain) => {
  it('renders every field, group and list title of its schema', async () => {
    await mountDomain(domain)
    const panel = wrapper.get('[role="tabpanel"]').text()

    for (const title of titlesOf(SCHEMAS[domain])) {
      expect(panel, `the title "${title}" of the ${domain} schema`).toContain(title)
    }
  })
})

describe.each(Object.keys(SCHEMAS))('the %s schema', (domain) => {
  // A fresh validator per test: a compiled schema is cached by its validator, so the shared one can hide a failure.
  it('compiles with a fresh validator, as the forms compile it', async () => {
    const store = createConfigurationStore(ref(fakeClient()))
    await store.fetchConfiguration()

    expect(() => createAjv({ useDefaults: true }).compile(store.schemas.value[domain])).not.toThrow()
  })
})
