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
import { createTokenStore } from './tokens'

// No timers and no network: the fake server below answers from memory and enforces If-Match.
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const preconditionFailed = () =>
  Object.assign(new Error('Precondition Failed'), { response: { status: 412, data: {} } })

/*
 * One token per name. The list carries no tag, the details do, and an update is refused with 412 when its tag no
 * longer describes the stored token.
 */
const fakeServer = (names) => {
  const tokens = Object.fromEntries(names.map(name => [name, { name, description: '', permissions: [], routes: [], expiresAt: null }]))
  const versions = Object.fromEntries(names.map(name => [name, 1]))
  const tagOf = (name) => `"${name}-${versions[name]}"`

  const update = vi.fn(async (name, request, etag) => {
    if (etag !== undefined && etag !== tagOf(name)) throw preconditionFailed()
    tokens[name] = { ...tokens[name], description: request.description }
    versions[name] += 1
    return { data: {} }
  })

  return {
    tokens,
    update,
    /* Another admin edits the token. */
    changeElsewhere: (name, description) => {
      tokens[name] = { ...tokens[name], description }
      versions[name] += 1
    },
    client: {
      tokens: {
        list: async () => ({ data: Object.values(tokens).map(token => ({ ...token })) }),
        get: async (name) => ({ data: { ...tokens[name] }, headers: { etag: tagOf(name) } }),
        createOrUpdate: async () => ({ data: {} }),
        update,
        delete: async () => ({}),
        regenerateSecret: async () => ({ data: '' })
      }
    }
  }
}

const recordingNotify = () => {
  const calls = { success: [], error: [] }
  return {
    calls,
    success: (message) => calls.success.push(message),
    error: (message) => calls.error.push(message)
  }
}

const loadedStore = async (server, notify) => {
  const store = createTokenStore({ getClient: () => server.client, notify })
  await store.fetchTokens()
  return store
}

describe('token writes with a tag', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends the tag the token was read with', async () => {
    const server = fakeServer(['ci'])
    const store = await loadedStore(server, recordingNotify())

    await store.saveTokenMeta(store.tokens.value[0], { description: 'nightly', manager: false, expiresAt: '' })

    expect(server.update).toHaveBeenCalledWith('ci', expect.objectContaining({ description: 'nightly' }), '"ci-1"')
  })

  it('opens the token conflict instead of an error when the token changed elsewhere', async () => {
    const server = fakeServer(['ci'])
    const notify = recordingNotify()
    const store = await loadedStore(server, notify)
    server.changeElsewhere('ci', 'other-admin')

    const saved = await store.saveTokenMeta(store.tokens.value[0], { description: 'mine', manager: false, expiresAt: '' })

    expect(saved).toBe(false)
    expect(store.conflict.value).toMatchObject({ kind: 'token', name: 'ci', title: 'ci' })
    expect(notify.calls.error).toEqual([])
    expect(notify.calls.success).toEqual([])
    expect(server.tokens.ci.description).toBe('other-admin')
  })

  it('reloads the token list and clears the conflict', async () => {
    const server = fakeServer(['ci'])
    const store = await loadedStore(server, recordingNotify())
    server.changeElsewhere('ci', 'other-admin')
    await store.saveTokenMeta(store.tokens.value[0], { description: 'mine', manager: false, expiresAt: '' })

    await store.reloadTokenConflict()

    expect(store.conflict.value).toBeNull()
    expect(store.tokens.value[0].description).toBe('other-admin')
  })

  it('overwrites the other change without a precondition after the explicit confirmation', async () => {
    const server = fakeServer(['ci'])
    const notify = recordingNotify()
    const store = await loadedStore(server, notify)
    server.changeElsewhere('ci', 'other-admin')
    await store.saveTokenMeta(store.tokens.value[0], { description: 'mine', manager: false, expiresAt: '' })

    const overwritten = await store.overwriteTokenConflict()

    expect(overwritten).toBe(true)
    expect(server.update).toHaveBeenLastCalledWith('ci', expect.objectContaining({ description: 'mine' }))
    expect(server.tokens.ci.description).toBe('mine')
    expect(store.conflict.value).toBeNull()
    expect(notify.calls.success).toEqual([`Token 'ci' updated`])
  })

  it('keeps the edit pending when the dialog is dismissed', async () => {
    const server = fakeServer(['ci'])
    const store = await loadedStore(server, recordingNotify())
    server.changeElsewhere('ci', 'other-admin')
    await store.saveTokenMeta(store.tokens.value[0], { description: 'mine', manager: false, expiresAt: '' })

    store.dismissTokenConflict()

    expect(store.conflict.value).toBeNull()
    expect(server.tokens.ci.description).toBe('other-admin')
  })
})
