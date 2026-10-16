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

import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { createConfigurationStore } from './configuration'
import { createSuccessToast, createErrorToast } from '../helpers/toast'

// The store is built per test with a fake client, so no module state leaks between tests.
// Session and toast are replaced so that importing the store touches neither the network
// nor the DOM.
vi.mock('./session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  properties: { name: { type: 'string' } }
}

const fakeClient = ({ update = async () => ({ data: {} }) } = {}) => {
  const server = {
    maven: { name: 'server-maven' },
    frontend: { name: 'server-frontend' }
  }
  return {
    server,
    settings: {
      domains: async () => ({ data: ['maven', 'frontend'] }),
      schema: async (domain) => ({ data: { ...SCHEMA, title: domain } }),
      fetch: async (domain) => ({ data: { ...server[domain] } }),
      update
    }
  }
}

const loadedStore = async (client = fakeClient()) => {
  const store = createConfigurationStore(ref(client))
  const loaded = await store.fetchConfiguration()
  expect(loaded, 'the fixture configuration should load').toBe(true)
  return store
}

describe('configuration store', () => {
  it('selects the first settings tab by default, never the domain that is managed under Access', async () => {
    const server = { login: { ldap: {} }, maven: { name: 'server-maven' } }
    const client = {
      settings: {
        domains: async () => ({ data: ['login', 'maven'] }),
        schema: async (domain) => ({ data: { ...SCHEMA, title: domain } }),
        fetch: async (domain) => ({ data: { ...server[domain] } }),
        update: async () => ({ data: {} })
      }
    }

    const store = await loadedStore(client)

    expect(store.selectedDomain.value).toBe('maven')
  })

  it('keeps the edited values dirty when saving fails', async () => {
    const client = fakeClient({ update: async () => { throw new Error('connection refused') } })
    const store = await loadedStore(client)
    store.setDomainData('maven', { name: 'edited' })

    const saved = await store.saveConfiguration()

    expect(saved, 'a failed save must report failure to the caller').toBe(false)
    expect(store.isDirty('maven'), 'a failed save must not clear the unsaved edit').toBe(true)
    expect(store.saveError.value, 'the failure reason must be kept for the action bar').toContain('connection refused')
  })

  it('clears the dirty state after a successful save', async () => {
    const client = fakeClient()
    client.settings.fetch = async (domain) => ({ data: { name: `saved-${domain}` } })
    const store = await loadedStore(client)
    store.setDomainData('maven', { name: 'edited' })

    const saved = await store.saveConfiguration()

    expect(saved, 'a successful save must report success').toBe(true)
    expect(store.isDirty('maven'), 'a saved domain must no longer be dirty').toBe(false)
    expect(store.saveError.value, 'a successful save must clear the previous error').toBeNull()
  })

  it('reports a domain as dirty only while it differs from the server snapshot', async () => {
    const store = await loadedStore()

    store.setDomainData('maven', { name: 'edited' })
    expect(store.isDirty('maven'), 'an edited domain must be dirty').toBe(true)

    store.setDomainData('maven', { name: 'server-maven' })
    expect(store.isDirty('maven'), 'an edit back to the server value must not be dirty').toBe(false)
  })

  it('reports unsaved edits from the store, so a remounted view still sees them', async () => {
    const store = await loadedStore()
    store.setDomainData('frontend', { name: 'edited' })

    // A remounted view holds no state of its own; it asks the store, which is all this checks.
    const hasChangesAfterRemount = store.hasChanges.value

    expect(hasChangesAfterRemount, 'unsaved edits must survive a view remount').toBe(true)
  })

  it('lists only the domain that has errors as invalid', async () => {
    const store = await loadedStore()

    store.setValidity('maven', false)

    expect(store.invalidDomains.value, 'only the domain with errors is invalid').toEqual(['maven'])
  })

  it('does not clear the errors of one domain when another domain becomes valid', async () => {
    const store = await loadedStore()
    store.setValidity('maven', false)

    store.setValidity('frontend', true)

    expect(store.invalidDomains.value, 'a valid frontend must not hide the maven errors').toEqual(['maven'])
  })

  it('refuses to save while any domain is invalid', async () => {
    let updateCalls = 0
    const client = fakeClient({ update: async () => { updateCalls++; return { data: {} } } })
    const store = await loadedStore(client)
    store.setDomainData('maven', { name: 'edited' })
    store.setValidity('frontend', false)

    const saved = await store.saveConfiguration()

    expect(saved, 'save must be refused while a domain is invalid').toBe(false)
    expect(updateCalls, 'no domain may be written while a domain is invalid').toBe(0)
  })

  it('discards pending edits by restoring the server snapshot', async () => {
    const store = await loadedStore()
    store.setDomainData('maven', { name: 'edited' })
    store.setValidity('maven', false)

    store.discardChanges()

    expect(store.configurations.value.maven, 'discard must restore the server value').toEqual({ name: 'server-maven' })
    expect(store.hasChanges.value, 'no domain may be dirty after discard').toBe(false)
    expect(store.invalidDomains.value, 'discard must clear the validity of the discarded domain').toEqual([])
  })

  it('does not announce a successful load, because loading is not a user action', async () => {
    vi.clearAllMocks()

    await loadedStore()

    expect(createSuccessToast, 'a successful load must not raise a success notice').not.toHaveBeenCalled()
  })

  it('still announces a failed load', async () => {
    vi.clearAllMocks()
    const client = fakeClient()
    client.settings.domains = async () => { throw new Error('unreachable') }

    await createConfigurationStore(ref(client)).fetchConfiguration()

    expect(createErrorToast, 'a failed load must raise an error notice').toHaveBeenCalledTimes(1)
  })

  it('reports a failed load and no domains', async () => {
    const client = fakeClient()
    client.settings.domains = async () => { throw new Error('unreachable') }
    const store = createConfigurationStore(ref(client))

    const loaded = await store.fetchConfiguration()

    expect(loaded, 'a failed load must report failure').toBe(false)
    expect(store.loadError.value, 'the load failure must be kept for the view').toContain('unreachable')
  })
})
