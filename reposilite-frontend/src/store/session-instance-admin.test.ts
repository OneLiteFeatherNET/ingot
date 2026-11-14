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

// A fresh copy of the session module per test, with the client mocked, so the flags of one test
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

describe('session instance admin flag', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('reports an instance admin when the server sends the flag', async () => {
    const store = await signIn({ permissions: [], routes: [], nodes: ['ingot.admin.*'], instanceAdmin: true })

    expect(store.instanceAdmin.value, 'the flag is read from /api/auth/me').toBe(true)
  })

  it('reports an instance admin who holds the admin right only through a team', async () => {
    // A token sign in carries its stored permissions, which lack the manager entry for a team admin.
    const store = await signIn({ permissions: [], routes: [], nodes: ['ingot.admin.*'], instanceAdmin: true })

    expect(store.instanceAdmin.value).toBe(true)
    expect(store.isManager.value, 'the manager answer follows the server flag').toBe(true)
  })

  it('reports no instance admin when the server says so, even with a stored manager permission', async () => {
    const store = await signIn({ permissions: [{ identifier: 'access-token:manager' }], routes: [], nodes: [], instanceAdmin: false })

    expect(store.instanceAdmin.value, 'the server flag decides when it is sent').toBe(false)
    expect(store.isManager.value).toBe(false)
  })

  it('falls back to the manager permission when the server sends no flag', async () => {
    const store = await signIn({ permissions: [{ identifier: 'access-token:manager' }], routes: [] })

    expect(store.instanceAdmin.value, 'an older server is read through its manager permission').toBe(true)
  })

  it('reports no instance admin for an older server that sends neither the flag nor the permission', async () => {
    const store = await signIn({ permissions: [], routes: [] })

    expect(store.instanceAdmin.value).toBe(false)
  })
})
