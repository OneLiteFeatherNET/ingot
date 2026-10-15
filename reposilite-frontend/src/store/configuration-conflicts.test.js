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
import { createConfigurationStore, diffFields } from './configuration'
import { createSuccessToast, createErrorToast } from '../helpers/toast'

// The store is built per test against a fake server. Session and toast are replaced so that
// importing the store touches neither the network nor the DOM. No timers are used.
vi.mock('./session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  properties: { name: { type: 'string' }, size: { type: 'number' } }
}

const response = (data, etag) => ({ data, headers: { etag } })

const preconditionFailed = () =>
  Object.assign(new Error('Precondition Failed'), { response: { status: 412, data: {} } })

/*
 * A server with one state per domain that enforces If-Match the way the backend does: a write that carries a tag
 * which no longer describes the stored state is refused with 412 and changes nothing.
 */
const fakeServer = (initial) => {
  const state = structuredClone(initial)
  const versions = Object.fromEntries(Object.keys(state).map(domain => [domain, 1]))
  const tagOf = (domain) => `"${domain}-${versions[domain]}"`

  const update = vi.fn(async (domain, content, etag) => {
    if (etag !== undefined && etag !== tagOf(domain)) throw preconditionFailed()
    state[domain] = structuredClone(content)
    versions[domain] += 1
    return response(structuredClone(content), tagOf(domain))
  })

  return {
    state,
    update,
    /* Another admin saves the domain. */
    changeElsewhere: (domain, content) => {
      state[domain] = structuredClone(content)
      versions[domain] += 1
    },
    client: {
      settings: {
        domains: async () => ({ data: Object.keys(state) }),
        schema: async (domain) => ({ data: { ...SCHEMA, title: domain } }),
        fetch: async (domain) => response(structuredClone(state[domain]), tagOf(domain)),
        update
      }
    }
  }
}

const loadedStore = async (server) => {
  const store = createConfigurationStore(ref(server.client))
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  return store
}

describe('settings writes with a tag', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends the tag the domain was loaded with', async () => {
    const server = fakeServer({ maven: { name: 'loaded' } })
    const store = await loadedStore(server)

    store.setDomainData('maven', { name: 'edited' })
    await store.saveConfiguration()

    expect(server.update).toHaveBeenCalledWith('maven', { name: 'edited' }, '"maven-1"')
    expect(server.state.maven).toEqual({ name: 'edited' })
  })

  it('takes the tag of the write back from the read that follows it', async () => {
    const server = fakeServer({ maven: { name: 'loaded' } })
    const store = await loadedStore(server)

    store.setDomainData('maven', { name: 'first' })
    await store.saveConfiguration()
    store.setDomainData('maven', { name: 'second' })
    await store.saveConfiguration()

    expect(server.update).toHaveBeenLastCalledWith('maven', { name: 'second' }, '"maven-2"')
  })

  it('opens the conflict dialog instead of an error when the server answers 412', async () => {
    const server = fakeServer({ maven: { name: 'loaded' } })
    const store = await loadedStore(server)
    server.changeElsewhere('maven', { name: 'other-admin' })

    store.setDomainData('maven', { name: 'mine' })
    const saved = await store.saveConfiguration()

    expect(saved).toBe(false)
    expect(store.conflict.value).toMatchObject({ kind: 'settings', name: 'maven', title: 'maven', reviewed: false })
    expect(createErrorToast).not.toHaveBeenCalled()
    expect(createSuccessToast).not.toHaveBeenCalled()
    expect(store.saveError.value).toBeNull()
  })

  it('leaves the other admin change in place and keeps the local draft dirty after a 412', async () => {
    const server = fakeServer({ maven: { name: 'loaded' } })
    const store = await loadedStore(server)
    server.changeElsewhere('maven', { name: 'other-admin' })

    store.setDomainData('maven', { name: 'mine' })
    await store.saveConfiguration()

    expect(server.state.maven).toEqual({ name: 'other-admin' })
    expect(store.isDirty('maven')).toBe(true)
  })

  it('keeps the other failures as an error and opens no dialog', async () => {
    const server = fakeServer({ maven: { name: 'loaded' } })
    server.update.mockRejectedValueOnce(Object.assign(new Error('disk full'), { response: { status: 500 } }))
    const store = await loadedStore(server)

    store.setDomainData('maven', { name: 'mine' })
    const saved = await store.saveConfiguration()

    expect(saved).toBe(false)
    expect(store.conflict.value).toBeNull()
    expect(store.saveError.value).toBe('Not saved: maven. disk full')
    expect(createErrorToast).toHaveBeenCalledTimes(1)
  })
})

describe('settings conflict resolution', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /* Loads, edits, and lets another admin save first, so the next save is refused. */
  const conflicted = async () => {
    const server = fakeServer({ maven: { name: 'loaded', size: 1 } })
    const store = await loadedStore(server)
    server.changeElsewhere('maven', { name: 'other-admin', size: 1 })
    store.setDomainData('maven', { name: 'mine', size: 1 })
    await store.saveConfiguration()
    return { server, store }
  }

  it('reloads the server state and discards the local draft', async () => {
    const { server, store } = await conflicted()

    expect(await store.reloadConflict()).toBe(true)

    expect(store.conflict.value).toBeNull()
    expect(store.configurations.value.maven).toEqual({ name: 'other-admin', size: 1 })
    expect(store.isDirty('maven')).toBe(false)
    expect(server.update).toHaveBeenCalledTimes(1)
  })

  it('sends the reloaded tag with the next save', async () => {
    const { server, store } = await conflicted()
    await store.reloadConflict()

    store.setDomainData('maven', { name: 'after-reload', size: 1 })
    await store.saveConfiguration()

    expect(server.update).toHaveBeenLastCalledWith('maven', { name: 'after-reload', size: 1 }, '"maven-2"')
  })

  it('overwrites the other change with the local draft, sent without a precondition', async () => {
    const { server, store } = await conflicted()

    expect(await store.overwriteConflict()).toBe(true)

    expect(server.update).toHaveBeenLastCalledWith('maven', { name: 'mine', size: 1 })
    expect(server.state.maven).toEqual({ name: 'mine', size: 1 })
    expect(store.conflict.value).toBeNull()
    expect(store.isDirty('maven')).toBe(false)
    expect(createSuccessToast).toHaveBeenCalledWith('Configuration updated')
  })

  it('lists the fields where the draft and the server differ when reviewed', async () => {
    const { store } = await conflicted()

    await store.reviewConflict()

    expect(store.conflict.value.reviewed).toBe(true)
    expect(store.conflict.value.differences).toEqual([
      { path: 'name', local: 'mine', server: 'other-admin' }
    ])
    expect(store.isDirty('maven'), 'reviewing changes nothing').toBe(true)
  })

  it('keeps the draft pending when the dialog is dismissed, so the next save meets the same conflict', async () => {
    const { server, store } = await conflicted()

    store.dismissConflict()
    expect(store.conflict.value).toBeNull()
    expect(store.isDirty('maven')).toBe(true)

    await store.saveConfiguration()

    expect(server.update).toHaveBeenLastCalledWith('maven', { name: 'mine', size: 1 }, '"maven-1"')
    expect(store.conflict.value).not.toBeNull()
  })
})

describe('diffFields', () => {
  it('finds nothing when both sides are equal', () => {
    expect(diffFields({ a: { b: 1 } }, { a: { b: 1 } })).toEqual([])
  })

  it('names a nested field by its dotted path', () => {
    expect(diffFields({ a: { b: 1, c: 2 } }, { a: { b: 1, c: 3 } })).toEqual([
      { path: 'a.c', local: 2, server: 3 }
    ])
  })

  it('reports a key present on one side only, with the other side undefined', () => {
    expect(diffFields({ a: 1 }, {})).toEqual([{ path: 'a', local: 1, server: undefined }])
  })

  it('compares arrays of equal length index by index', () => {
    expect(diffFields({ list: [1, 2] }, { list: [1, 3] })).toEqual([
      { path: 'list.1', local: 2, server: 3 }
    ])
  })

  it('reports a whole array when the lengths differ', () => {
    expect(diffFields({ list: [1] }, { list: [1, 2] })).toEqual([
      { path: 'list', local: [1], server: [1, 2] }
    ])
  })
})
