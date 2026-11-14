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

// Session and toast are replaced, as in configuration-collections.test.js, so that the store
// touches neither the network nor the DOM.
vi.mock('./session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'] },
          redeployment: { type: 'boolean' }
        },
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
  }
}

const loadedStore = async () => {
  const client = {
    settings: {
      domains: async () => ({ data: ['maven'] }),
      schema: async () => ({ data: SCHEMA }),
      fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(SERVER[domain])) }),
      update: async () => ({ data: {} })
    }
  }
  const store = createConfigurationStore(ref(client))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  return store
}

const repositories = (store) => store.configurations.value.maven.repositories
const keysOf = (store) => store.entryKeysOf('maven', 'repositories')

describe('configuration store entry keys', () => {
  it('gives every entry a key of its own', async () => {
    const store = await loadedStore()

    expect(new Set(keysOf(store)).size, 'two entries have two different keys').toBe(2)
  })

  it('keeps the keys when an entry is edited, even when its id is cleared', async () => {
    const store = await loadedStore()
    const before = keysOf(store)

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], id: '' })

    expect(keysOf(store), 'an edit must not change which key an entry has').toEqual(before)
  })

  it('keeps the keys of the other entries when one is added, and gives the new entry a key of its own', async () => {
    const store = await loadedStore()
    const before = keysOf(store)

    store.addEntry('maven', 'repositories', { id: 'mirror' })
    const after = keysOf(store)

    expect(after.slice(0, 2)).toEqual(before)
    expect(new Set(after).size, 'three entries have three different keys').toBe(3)
  })

  it('drops the key of the removed entry and keeps the key of the entry after it', async () => {
    const store = await loadedStore()
    const [first, second] = keysOf(store)

    store.removeEntry('maven', 'repositories', 0)

    expect(keysOf(store)).toEqual([second])
    expect(keysOf(store)).not.toContain(first)
  })
})

describe('configuration store entry ids', () => {
  it('keeps the id of an entry when an edit arrives without one, so the entry does not leave the list', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 0, { visibility: 'PRIVATE', redeployment: false })

    expect(repositories(store).map(entry => entry.id)).toEqual(['releases', 'snapshots'])
    expect(repositories(store)[0].visibility).toBe('PRIVATE')
  })

  it('keeps an id that was cleared on purpose, because the id is only filled in when it is missing', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], id: '' })

    expect(repositories(store)[0].id).toBe('')
  })

  it('keeps the id when a variant switch replaces the entry with the default of its branch', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 0, { redeployment: false })

    expect(repositories(store)[0].id).toBe('releases')
  })

  it('marks the domain invalid, and so unsavable, while an entry has no id', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], id: '' })

    expect(store.invalidDomains.value).toEqual(['maven'])
    expect(await store.saveConfiguration(), 'a domain with an unnamed entry is not written').toBe(false)
  })

  it('marks the domain invalid when two entries share an id', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 1, { ...repositories(store)[1], id: 'releases' })

    expect(store.invalidDomains.value).toEqual(['maven'])
  })

  it('clears the invalid state once the id is usable again', async () => {
    const store = await loadedStore()
    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], id: '' })

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], id: 'named' })

    expect(store.invalidDomains.value).toEqual([])
  })
})
