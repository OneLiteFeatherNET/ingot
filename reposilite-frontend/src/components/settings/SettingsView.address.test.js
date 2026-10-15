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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// The configuration store is a module singleton, so each test loads a fresh copy. The server
// is replaced by a client that knows two domains with empty settings: the tests are about
// which domain the view shows and reports, not about the forms.
const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

const serverWithDomains = (...domains) => ({
  settings: {
    domains: vi.fn(() => Promise.resolve({ data: domains })),
    schema: vi.fn(() => Promise.resolve({ data: { type: 'object', properties: {} } })),
    fetch: vi.fn(() => Promise.resolve({ data: {} })),
    update: vi.fn()
  }
})

// The router is imported after the module reset, like the view, so both share one copy of vue-router.
const mountSettings = async (props) => {
  const { default: SettingsView } = await import('./SettingsView.vue')
  const { createMemoryHistory, createRouter } = await import('vue-router')
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
      { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
    ]
  })
  return mount(SettingsView, {
    props: { selectedTab: 'Settings', ...props },
    global: { plugins: [router] }
  })
}

describe('SettingsView address', () => {
  let wrapper

  beforeEach(() => {
    vi.resetModules()
    session.client = serverWithDomains('maven', 'proxy')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('shows the domain the address names', async () => {
    const { useConfiguration } = await import('../../store/configuration')
    wrapper = await mountSettings({ domain: 'proxy' })
    await flushPromises()

    expect(useConfiguration().store.selectedDomain.value).toBe('proxy')
  })

  it('reports a domain the user switches to, so the address can follow it', async () => {
    const { useConfiguration } = await import('../../store/configuration')
    wrapper = await mountSettings({ domain: 'maven' })
    await flushPromises()

    useConfiguration().store.selectedDomain.value = 'proxy'
    await flushPromises()

    expect(wrapper.emitted('update:domain')?.at(-1)).toEqual(['proxy'])
  })

  it('does not report back the domain the address already names', async () => {
    wrapper = await mountSettings({ domain: 'maven' })
    await flushPromises()

    expect(wrapper.emitted('update:domain')).toBeUndefined()
  })
})
