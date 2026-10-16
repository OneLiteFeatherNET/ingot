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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { createConfigurationStore } from './configuration'
import { setAppearance, applyAppearance } from './appearance'

// The appearance is replaced by spies, so the test sees the calls the store makes and nothing
// touches the document.
vi.mock('./appearance', () => ({
  setAppearance: vi.fn(),
  applyAppearance: vi.fn(async () => {})
}))
vi.mock('./session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = { type: 'object', properties: { name: { type: 'string' } } }

// A fresh server per test. A write stores what it was given, as the real server does, unless the
// test makes the write fail.
const fakeClient = ({ failWrites = false } = {}) => {
  const server = {
    maven: { name: 'server-maven' },
    frontend: { appearance: { seedColor: '#006a6a' } }
  }
  return {
    server,
    settings: {
      domains: async () => ({ data: ['maven', 'frontend'] }),
      schema: async (domain) => ({ data: { ...SCHEMA, title: domain } }),
      fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(server[domain])) }),
      update: vi.fn(async (domain, data) => {
        if (failWrites) throw new Error('connection refused')
        server[domain] = JSON.parse(JSON.stringify(data))
        return { data: {} }
      })
    }
  }
}

const loadedStore = async (client = fakeClient()) => {
  const store = createConfigurationStore(ref(client))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  return store
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('saving the frontend settings', () => {
  it('applies the saved appearance at once, without a reload', async () => {
    const store = await loadedStore()
    store.setDomainData('frontend', { appearance: { seedColor: '#8a1c1c' } })

    await store.saveConfiguration()

    expect(setAppearance).toHaveBeenCalledWith({ seedColor: '#8a1c1c' }), 'the saved value is what the server now holds'
    expect(applyAppearance).toHaveBeenCalledTimes(1)
  })

  it('applies the appearance of the server response, not of the edited form', async () => {
    const client = fakeClient()
    client.settings.fetch = async () => ({ data: { appearance: { seedColor: '#123456' } } })
    const store = await loadedStore(client)
    store.setDomainData('frontend', { appearance: { seedColor: '#8a1c1c' } })

    await store.saveConfiguration()

    expect(setAppearance).toHaveBeenLastCalledWith({ seedColor: '#123456' })
  })

  it('leaves the appearance alone when another domain is saved', async () => {
    const store = await loadedStore()
    store.setDomainData('maven', { name: 'edited' })

    await store.saveConfiguration()

    expect(applyAppearance).not.toHaveBeenCalled()
  })

  it('leaves the appearance alone when the frontend save fails', async () => {
    const store = await loadedStore(fakeClient({ failWrites: true }))
    store.setDomainData('frontend', { appearance: { seedColor: '#8a1c1c' } })

    await store.saveConfiguration()

    expect(applyAppearance).not.toHaveBeenCalled()
  })
})

describe('colour fields in the frontend settings', () => {
  it('marks the domain invalid when a colour is not #RRGGBB, so it cannot be saved', async () => {
    const client = fakeClient()
    const store = await loadedStore(client)
    store.setDomainData('frontend', { appearance: { primaryColor: 'teal' } })
    store.validateDomain('frontend')

    const saved = await store.saveConfiguration()

    expect(store.invalidDomains.value).toEqual(['frontend'])
    expect(saved, 'an invalid domain is not written').toBe(false)
    expect(client.settings.update).not.toHaveBeenCalled()
  })

  it('accepts an emptied colour field, which means not set', async () => {
    const store = await loadedStore()
    store.setDomainData('frontend', { appearance: { seedColor: '#006a6a', primaryColor: '' } })
    store.validateDomain('frontend')

    expect(store.invalidDomains.value).toEqual([])
  })
})
