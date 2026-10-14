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

import { describe, it, expect } from 'vitest'
import {
  createTokenStore,
  normalizeRoutePath,
  checkRouteDraft,
  validateTokenName,
  isDraftDirty
} from './tokens'

// Every test builds its own store around its own fake client, so no state leaks between tests.
const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

const recordingNotify = () => {
  const calls = { success: [], error: [] }
  return {
    calls,
    success: (message) => calls.success.push(message),
    error: (message) => calls.error.push(message)
  }
}

const fakeClient = (tokens = {}) => ({
  tokens: {
    list: () => Promise.resolve({ data: [] }),
    createOrUpdate: () => Promise.resolve({ data: {} }),
    update: () => Promise.resolve({}),
    delete: () => Promise.resolve({}),
    regenerateSecret: () => Promise.resolve({ data: '' }),
    ...tokens
  }
})

const newStore = (client, notify = recordingNotify()) =>
  createTokenStore({ getClient: () => client, notify })

const tokenWithRoutes = (...paths) => ({
  name: 'ci',
  permissions: [],
  routes: paths.map(path => ({ path, permission: { identifier: 'route:read' } }))
})

describe('token list loading', () => {
  it('reports loading while the list request is pending', () => {
    const pending = deferred()
    const store = newStore(fakeClient({ list: () => pending.promise }))

    store.fetchTokens()

    expect(store.loading.value).toBe(true)
  })

  it('does not report the list as loaded while the request is pending', () => {
    const pending = deferred()
    const store = newStore(fakeClient({ list: () => pending.promise }))

    store.fetchTokens()

    expect(store.loaded.value).toBe(false)
  })

  it('stops reporting loading once the request has succeeded', async () => {
    const store = newStore(fakeClient({ list: () => Promise.resolve({ data: [{ name: 'ci' }] }) }))

    await store.fetchTokens()

    expect(store.loading.value).toBe(false)
  })

  it('reports loaded with an empty list when the server has no tokens', async () => {
    const store = newStore(fakeClient({ list: () => Promise.resolve({ data: [] }) }))

    await store.fetchTokens()

    expect(store.loaded.value).toBe(true)
  })

  it('keeps the list unloaded when the request fails', async () => {
    const store = newStore(fakeClient({ list: () => Promise.reject(new Error('boom')) }))

    await store.fetchTokens()

    expect(store.loaded.value).toBe(false)
  })

  it('exposes the failure message when the request fails', async () => {
    const store = newStore(fakeClient({ list: () => Promise.reject(new Error('boom')) }))

    await store.fetchTokens()

    expect(store.loadError.value).toBe('boom')
  })

  it('clears the failure once a retry succeeds', async () => {
    let attempts = 0
    const store = newStore(fakeClient({
      list: () => {
        attempts += 1
        return attempts === 1 ? Promise.reject(new Error('boom')) : Promise.resolve({ data: [] })
      }
    }))

    await store.fetchTokens()
    await store.fetchTokens()

    expect(store.loadError.value).toBe('')
  })

  it('reports loaded after a retry succeeds', async () => {
    let attempts = 0
    const store = newStore(fakeClient({
      list: () => {
        attempts += 1
        return attempts === 1 ? Promise.reject(new Error('boom')) : Promise.resolve({ data: [] })
      }
    }))

    await store.fetchTokens()
    await store.fetchTokens()

    expect(store.loaded.value).toBe(true)
  })
})

describe('one-time secret', () => {
  it('holds the secret of a newly generated token', async () => {
    const client = fakeClient({ createOrUpdate: () => Promise.resolve({ data: { secret: 's3cr3t' } }) })
    const store = newStore(client)

    await store.createToken('ci', { type: 'PERSISTENT' })

    expect(store.secret.value).toEqual({ name: 'ci', value: 's3cr3t' })
  })

  it('keeps the secret when the list is refreshed afterwards', async () => {
    const client = fakeClient({ createOrUpdate: () => Promise.resolve({ data: { secret: 's3cr3t' } }) })
    const store = newStore(client)

    await store.createToken('ci', { type: 'PERSISTENT' })
    await store.fetchTokens()

    expect(store.secret.value?.value).toBe('s3cr3t')
  })

  it('holds the secret of a regenerated token', async () => {
    const store = newStore(fakeClient({ regenerateSecret: () => Promise.resolve({ data: 'fresh' }) }))

    await store.regenerateSecret('ci')

    expect(store.secret.value).toEqual({ name: 'ci', value: 'fresh' })
  })

  it('clears the secret only when it is dismissed', async () => {
    const store = newStore(fakeClient({ regenerateSecret: () => Promise.resolve({ data: 'fresh' }) }))
    await store.regenerateSecret('ci')

    store.dismissSecret()

    expect(store.secret.value).toBeNull()
  })
})

describe('route path validation', () => {
  it('adds a leading slash to a relative path', () => {
    expect(normalizeRoutePath('releases')).toBe('/releases')
  })

  it('keeps a path that already starts with a slash', () => {
    expect(normalizeRoutePath('/releases')).toBe('/releases')
  })

  it('normalises a blank path to an empty string', () => {
    expect(normalizeRoutePath('   ')).toBe('')
  })

  it('reports an empty path as empty', () => {
    expect(checkRouteDraft(tokenWithRoutes(), { path: '  ' }).error).toBe('empty')
  })

  it('reports a path that another route already uses as a duplicate', () => {
    const result = checkRouteDraft(tokenWithRoutes('/releases'), { path: 'releases' })

    expect(result.error).toBe('duplicate')
  })

  it('returns the existing route that a duplicate path collides with', () => {
    const result = checkRouteDraft(tokenWithRoutes('/releases'), { path: '/releases' })

    expect(result.existing.path).toBe('/releases')
  })

  it('does not report the route being edited as a duplicate of itself', () => {
    const result = checkRouteDraft(tokenWithRoutes('/releases'), { path: '/releases', original: '/releases' })

    expect(result.error).toBeNull()
  })

  it('accepts a path that no route uses yet', () => {
    const result = checkRouteDraft(tokenWithRoutes('/releases'), { path: '/snapshots' })

    expect(result.error).toBeNull()
  })
})

describe('token name validation', () => {
  it('rejects a blank name', () => {
    expect(validateTokenName('  ', [])).toBe('empty')
  })

  it('rejects a name with a colon', () => {
    expect(validateTokenName('ci:bot', [])).toBe('invalid')
  })

  it('rejects a name with a slash', () => {
    expect(validateTokenName('ci/bot', [])).toBe('invalid')
  })

  it('rejects a name that another token already has', () => {
    expect(validateTokenName('ci', [{ name: 'ci' }])).toBe('duplicate')
  })

  it('accepts a fresh name', () => {
    expect(validateTokenName('ci-bot', [{ name: 'ci' }])).toBeNull()
  })
})

describe('unsaved draft detection', () => {
  it('reports a draft as clean while it matches its baseline', () => {
    expect(isDraftDirty({ path: '/a', read: true }, { path: '/a', read: true })).toBe(false)
  })

  it('reports a draft as dirty once a field differs from its baseline', () => {
    expect(isDraftDirty({ path: '/b', read: true }, { path: '/a', read: true })).toBe(true)
  })
})
