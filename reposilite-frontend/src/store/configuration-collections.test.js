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
import { createConfigurationStore, countDifferences } from './configuration'

// Session and toast are replaced, as in configuration.test.js, so that the store touches
// neither the network nor the DOM.
vi.mock('./session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const REPOSITORY_SCHEMA = {
  type: 'object',
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

const serverWithRepositories = () => ({
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', redeployment: false },
      { id: 'snapshots', visibility: 'PRIVATE', redeployment: true }
    ]
  }
})

const fakeClient = (server = serverWithRepositories()) => ({
  server,
  settings: {
    domains: async () => ({ data: ['maven'] }),
    schema: async () => ({ data: { ...REPOSITORY_SCHEMA, title: 'Maven' } }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(server[domain])) }),
    update: async () => ({ data: {} })
  }
})

const loadedStore = async () => {
  const store = createConfigurationStore(ref(fakeClient()))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  return store
}

const repositories = (store) => store.configurations.value.maven.repositories

describe('countDifferences', () => {
  it('is zero for equal values', () => {
    expect(countDifferences({ a: [{ id: 'x' }] }, { a: [{ id: 'x' }] })).toBe(0)
  })

  it('counts one changed property once', () => {
    expect(countDifferences({ visibility: 'PRIVATE' }, { visibility: 'PUBLIC' })).toBe(1)
  })

  it('counts two changed properties twice', () => {
    expect(countDifferences({ a: 1, b: 2 }, { a: 0, b: 3 })).toBe(2)
  })

  it('counts an added entry as one change, not one per property', () => {
    expect(countDifferences([{ id: 'a', visibility: 'PUBLIC' }, { id: 'b', visibility: 'PUBLIC' }], [{ id: 'a', visibility: 'PUBLIC' }])).toBe(1)
  })

  it('counts a change inside a nested object once', () => {
    expect(countDifferences({ storage: { bucket: 'old', region: 'eu' } }, { storage: { bucket: 'new', region: 'eu' } })).toBe(1)
  })
})

describe('configuration store collections', () => {
  it('reports no unsaved changes right after loading', async () => {
    const store = await loadedStore()

    expect(store.hasUnsavedChanges.value, 'a freshly loaded configuration has nothing to save').toBe(false)
    expect(store.changeCount.value).toBe(0)
  })

  it('counts every changed value, so the action bar can say how many edits are pending', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'PRIVATE', redeployment: true })

    expect(store.hasUnsavedChanges.value).toBe(true)
    expect(store.changeCount.value, 'two changed values are two pending changes').toBe(2)
  })

  it('writes an edited entry at its position and leaves the other entries alone', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 1, { ...repositories(store)[1], visibility: 'PUBLIC' })

    expect(repositories(store).map(entry => entry.id)).toEqual(['releases', 'snapshots'])
    expect(repositories(store)[1].visibility).toBe('PUBLIC')
    expect(repositories(store)[0].visibility).toBe('PUBLIC')
  })

  it('appends a new entry and reports its position', async () => {
    const store = await loadedStore()

    const index = store.addEntry('maven', 'repositories', { id: 'mirror' })

    expect(index).toBe(2)
    expect(repositories(store).map(entry => entry.id)).toEqual(['releases', 'snapshots', 'mirror'])
    expect(store.isDirty('maven')).toBe(true)
  })

  it('removes the entry at a position and marks the domain dirty', async () => {
    const store = await loadedStore()

    store.removeEntry('maven', 'repositories', 0)

    expect(repositories(store).map(entry => entry.id)).toEqual(['snapshots'])
    expect(store.hasUnsavedChanges.value).toBe(true)
  })

  it('brings back a deleted entry when the edit is discarded', async () => {
    const store = await loadedStore()
    store.removeEntry('maven', 'repositories', 0)

    store.discardChanges()

    expect(repositories(store).map(entry => entry.id)).toEqual(['releases', 'snapshots'])
    expect(store.hasUnsavedChanges.value, 'discarding ends the unsaved state').toBe(false)
  })

  it('clears the pending count once the edits are discarded', async () => {
    const store = await loadedStore()
    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'PRIVATE' })

    store.discardChanges()

    expect(store.changeCount.value).toBe(0)
  })

  it('marks the domain invalid when an entry breaks the schema', async () => {
    const store = await loadedStore()

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'UNKNOWN' })

    expect(store.invalidDomains.value).toEqual(['maven'])
  })

  it('keeps the domain invalid while another entry is edited, because the broken entry is still there', async () => {
    const store = await loadedStore()
    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'UNKNOWN' })

    store.setEntry('maven', 'repositories', 1, { ...repositories(store)[1], redeployment: false })

    expect(store.invalidDomains.value).toEqual(['maven'])
  })

  it('clears the invalid state once the broken entry is fixed', async () => {
    const store = await loadedStore()
    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'UNKNOWN' })

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'PRIVATE' })

    expect(store.invalidDomains.value).toEqual([])
  })

  it('keeps a domain valid when the schema cannot be compiled, and leaves the report to the form', async () => {
    const store = await loadedStore()
    store.schemas.value.maven = { type: 'not-a-type' }

    store.setEntry('maven', 'repositories', 0, { ...repositories(store)[0], visibility: 'PRIVATE' })

    expect(store.invalidDomains.value).toEqual([])
  })
})
