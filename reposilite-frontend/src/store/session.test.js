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
import { flushPromises } from '@vue/test-utils'

// Every test gets a fresh copy of the session module, so the token ref and the stored
// credentials of one test cannot leak into the next. The toast helper is replaced, which
// keeps the tests off the real notification component.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    interceptors: { response: { use: vi.fn() } }
  }
}))

vi.mock('../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createInfoToast: vi.fn(),
  createSuccessToast: vi.fn(),
  createWarningToast: vi.fn(),
  createErrorToast: vi.fn()
}))

const responseError = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} }
  })

const SIGNED_IN_DETAILS = { permissions: [], routes: [] }

const loadSession = async ({ storedName = '', storedSecret = '' } = {}) => {
  localStorage.clear()
  if (storedName) localStorage.setItem('token-name', storedName)
  if (storedSecret) localStorage.setItem('token-secret', storedSecret)

  vi.resetModules()
  const axios = (await import('axios')).default
  const session = await import('./session')
  const client = await import('./client')
  const toast = await import('../helpers/toast')

  loaded.push(session)
  return { axios, session, client, toast, store: session.useSession() }
}

// A session loaded by a test keeps listening to storage events until it is told to stop, so
// a test cannot react to the session of an earlier test.
const loaded = []
afterEach(() => {
  loaded.splice(0).forEach(session => session.stopSharingSession())
})

describe('loginFailureMessage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('maps a rejected sign in to a message about the name or the secret', async () => {
    const { session } = await loadSession()

    expect(session.loginFailureMessage(responseError(401)))
      .toBe('Name or secret is incorrect')
  })

  it('shows the transport message when the server cannot be reached', async () => {
    const { session } = await loadSession()

    expect(session.loginFailureMessage(new Error('Network Error')))
      .toBe('Network Error')
  })

  it('shows the status for other server failures', async () => {
    const { session } = await loadSession()

    expect(session.loginFailureMessage(responseError(500)))
      .toBe('Request failed with status 500')
  })
})

describe('logout', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('confirms with a signed out notice when the user logs out', async () => {
    const { axios, store, toast } = await loadSession()
    axios.get.mockResolvedValue({ data: SIGNED_IN_DETAILS })
    await store.login('alice', 'secret')

    store.logout()

    expect(toast.createInfoToast, 'the user must see that the sign out happened')
      .toHaveBeenCalledWith('Signed out')
    expect(store.isLogged.value).toBe(false)
  })
})

describe('session expiry', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('signs out and warns once when several requests are rejected with 401', async () => {
    const { axios, client, store, toast } = await loadSession()
    axios.get.mockResolvedValue({ data: SIGNED_IN_DETAILS })
    await store.login('alice', 'secret')

    await Promise.all([1, 2, 3].map(() => client.handleResponseError(responseError(401)).catch(() => {})))

    expect(toast.createWarningToast, 'one expiry notice for a burst of rejected requests')
      .toHaveBeenCalledTimes(1)
    expect(toast.createWarningToast).toHaveBeenCalledWith('Session expired, please sign in again')
    expect(store.isLogged.value).toBe(false)
  })

  it('forgets the stored secret when the session expires', async () => {
    const { axios, client, store } = await loadSession()
    axios.get.mockResolvedValue({ data: SIGNED_IN_DETAILS })
    await store.login('alice', 'secret')

    await client.handleResponseError(responseError(401)).catch(() => {})

    expect(store.token.value).toEqual({ name: '', secret: '' })
  })

  it('ignores a rejected request while nobody is signed in', async () => {
    const { client, toast } = await loadSession()

    await client.handleResponseError(responseError(401)).catch(() => {})

    expect(toast.createWarningToast, 'anonymous browsing must not produce an expiry notice')
      .not.toHaveBeenCalled()
  })
})

describe('startup with stored credentials', () => {
  it('forgets stored credentials that the server rejects', async () => {
    const { axios, store } = await loadSession({ storedName: 'alice', storedSecret: 'stale' })
    axios.get.mockRejectedValue(responseError(401))

    await store.initializeSession()

    expect(store.token.value, 'a rejected secret must not be kept for the next visit')
      .toEqual({ name: '', secret: '' })
    expect(localStorage.getItem('token-secret')).toBe('')
  })

  it('keeps stored credentials when the server cannot be reached', async () => {
    const { axios, store } = await loadSession({ storedName: 'alice', storedSecret: 'secret' })
    axios.get.mockRejectedValue(new Error('Network Error'))

    await expect(store.initializeSession()).rejects.toThrow('Network Error')

    expect(store.token.value, 'an unreachable server says nothing about the credentials')
      .toEqual({ name: 'alice', secret: 'secret' })
  })
})

describe('startup without stored credentials', () => {
  it('does not ask the server who is signed in when nothing is stored', async () => {
    const { axios, store } = await loadSession()

    await store.initializeSession()

    expect(axios.get, 'an anonymous visit must not send a request that is bound to fail')
      .not.toHaveBeenCalled()
  })

  it('is ready at once when nothing is stored', async () => {
    const { store } = await loadSession()

    await store.initializeSession()

    expect(store.sessionReady.value).toBe(true)
  })

  it('is not ready until stored credentials have been checked', async () => {
    const { axios, store } = await loadSession({ storedName: 'alice', storedSecret: 'secret' })
    axios.get.mockRejectedValue(new Error('Network Error'))

    const starting = store.initializeSession().catch(() => {})

    expect(store.sessionReady.value, 'a listing must wait for the check').toBe(false)
    await starting
    expect(store.sessionReady.value, 'an unreachable server still ends the start-up').toBe(true)
  })
})

describe('session shared across tabs', () => {
  it('signs out when another tab clears the shared session', async () => {
    const { axios, store } = await loadSession({ storedName: 'alice', storedSecret: 'secret' })
    axios.get.mockResolvedValue({ data: SIGNED_IN_DETAILS })
    await store.initializeSession()
    expect(store.isLogged.value).toBe(true)

    localStorage.setItem('token-name', '')
    localStorage.setItem('token-secret', '')
    window.dispatchEvent(new StorageEvent('storage', { key: 'token-name', newValue: '', storageArea: localStorage }))

    expect(store.isLogged.value, 'a logout in another tab must end the session here too').toBe(false)
    expect(store.token.value).toEqual({ name: '', secret: '' })
  })

  it('signs in when another tab stores a session', async () => {
    const { axios, store } = await loadSession()
    axios.get.mockResolvedValue({ data: SIGNED_IN_DETAILS })
    await store.initializeSession()

    localStorage.setItem('token-name', 'alice')
    localStorage.setItem('token-secret', 'secret')
    window.dispatchEvent(new StorageEvent('storage', { key: 'token-secret', newValue: 'secret', storageArea: localStorage }))
    await flushPromises()

    expect(store.isLogged.value, 'a login in another tab must be followed here').toBe(true)
    expect(store.token.value).toEqual({ name: 'alice', secret: 'secret' })
  })

  it('ignores storage changes that are not about the session', async () => {
    const { axios, store } = await loadSession({ storedName: 'alice', storedSecret: 'secret' })
    axios.get.mockResolvedValue({ data: SIGNED_IN_DETAILS })
    await store.initializeSession()
    const callsBefore = axios.get.mock.calls.length

    window.dispatchEvent(new StorageEvent('storage', { key: 'console-theme', newValue: 'dark', storageArea: localStorage }))
    await flushPromises()

    expect(store.isLogged.value).toBe(true)
    expect(axios.get.mock.calls.length, 'an unrelated key must not trigger a sign in').toBe(callsBefore)
  })
})
