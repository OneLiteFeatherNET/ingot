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

// A fresh copy of the session module per test, with the client mocked, so the nodes of one test
// cannot reach the next.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

vi.mock('../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createInfoToast: vi.fn(),
  createWarningToast: vi.fn()
}))

const loaded = []

const signIn = async (details) => {
  localStorage.clear()
  vi.resetModules()
  const axios = (await import('axios')).default
  const session = await import('./session')
  loaded.push(session)
  axios.get.mockResolvedValue({ data: details })
  const store = session.useSession()
  await store.login('alice', 'secret')
  return store
}

afterEach(() => {
  loaded.splice(0).forEach(session => session.stopSharingSession())
})

describe('session nodes', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('stores the nodes the server sends', async () => {
    const store = await signIn({ permissions: [], routes: [], nodes: ['ingot.admin.audit'] })

    expect(store.nodes.value).toEqual(['ingot.admin.audit'])
  })

  it('allows a node that the list covers', async () => {
    const store = await signIn({ permissions: [], routes: [], nodes: ['ingot.admin.*'] })

    expect(store.can('ingot.admin.maintenance')).toBe(true)
  })

  it('denies a node that the list does not cover', async () => {
    const store = await signIn({ permissions: [], routes: [], nodes: ['ingot.admin.audit'] })

    expect(store.can('ingot.admin.maintenance')).toBe(false)
  })

  it('lets the node list decide even when the manager flag says otherwise', async () => {
    const store = await signIn({ permissions: [{ identifier: 'access-token:manager' }], routes: [], instanceAdmin: true, nodes: [] })

    expect(store.can('ingot.admin.maintenance'), 'an empty list is a list: nothing is allowed').toBe(false)
  })

  it('falls back to the manager permission when the server sends no node list', async () => {
    const store = await signIn({ permissions: [{ identifier: 'access-token:manager' }], routes: [] })

    expect(store.can('ingot.admin.maintenance')).toBe(true)
  })

  it('falls back to the instance admin flag when the server sends no node list', async () => {
    const store = await signIn({ permissions: [], routes: [], instanceAdmin: true })

    expect(store.can('ingot.admin.audit')).toBe(true)
  })

  it('denies every node to a legacy session that is no manager', async () => {
    const store = await signIn({ permissions: [], routes: [] })

    expect(store.can('ingot.admin.audit')).toBe(false)
  })

  it('exposes an empty list for a legacy session', async () => {
    const store = await signIn({ permissions: [], routes: [] })

    expect(store.nodes.value).toEqual([])
  })
})
