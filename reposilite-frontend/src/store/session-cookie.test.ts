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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'

// Cookie sessions, the sign in actions and the cross tab channel. The server and the browser
// channel are doubles; every test loads a fresh copy of the session module.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

vi.mock('../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createInfoToast: vi.fn(),
  createSuccessToast: vi.fn(),
  createWarningToast: vi.fn(),
  createErrorToast: vi.fn()
}))

const PASSWORD = 'correct-horse-battery-staple-4711'

const PRINCIPAL = { id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice' }
const LOGIN = { data: { principal: PRINCIPAL, expiresAt: '2026-10-15T12:00:00Z' } }
const ME = {
  data: { permissions: [], routes: [], principal: PRINCIPAL, teams: ['core'], grants: [], instanceAdmin: true }
}

const responseError = (status, config) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} },
    config
  })

// BroadcastChannel double. Every instance a module creates is kept, so a test can read what
// was posted and deliver a message from "another tab".
const channels = []
class FakeBroadcastChannel {
  constructor(name) {
    this.name = name
    this.posted = []
    this.listener = null
    channels.push(this)
  }
  postMessage(message) { this.posted.push(message) }
  addEventListener(type, listener) { if (type === 'message') this.listener = listener }
  removeEventListener() { this.listener = null }
  close() {}
  receive(data) { this.listener?.({ data }) }
}

const clearCookies = () => {
  document.cookie.split(';').forEach(part => {
    const name = part.split('=')[0].trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  })
}

const loaded = []

// Routes the mocked axios by URL, so each test states only the answers it cares about.
const serve = (axios, { login = LOGIN, me = ME, providers, setup } = {}) => {
  axios.get.mockImplementation((url) => {
    if (url.endsWith('/api/auth/providers')) return Promise.resolve({ data: providers })
    if (url.endsWith('/api/auth/me')) return me instanceof Error ? Promise.reject(me) : Promise.resolve(me)
    return Promise.reject(new Error(`unexpected GET ${url}`))
  })
  axios.post.mockImplementation((url) => {
    if (url.endsWith('/api/auth/login')) return login instanceof Error ? Promise.reject(login) : Promise.resolve(login)
    if (url.endsWith('/api/auth/setup')) return setup instanceof Error ? Promise.reject(setup) : Promise.resolve(setup)
    if (url.endsWith('/api/auth/refresh')) return Promise.resolve({ data: {} })
    if (url.endsWith('/api/auth/logout')) return Promise.resolve({ data: {} })
    return Promise.reject(new Error(`unexpected POST ${url}`))
  })
}

const loadSession = async () => {
  localStorage.clear()
  sessionStorage.clear()
  clearCookies()
  channels.length = 0
  globalThis.BroadcastChannel = FakeBroadcastChannel

  vi.resetModules()
  const axios = (await import('axios')).default
  axios.get.mockReset()
  axios.post.mockReset()
  axios.request.mockReset()
  const session = await import('./session')
  const client = await import('./client')
  const toast = await import('../helpers/toast')

  loaded.push(session)
  return { axios, session, client, toast, store: session.useSession(), channel: () => channels[0] }
}

afterEach(() => {
  loaded.splice(0).forEach(session => session.stopSharingSession())
  delete globalThis.BroadcastChannel
  clearCookies()
})

describe('sign in with a password', () => {
  it('posts the local provider with the username and password', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    const loginCall = axios.post.mock.calls.find(call => call[0].endsWith('/api/auth/login'))
    expect(loginCall[1]).toEqual({ provider: 'local', username: 'alice', password: PASSWORD })
  })

  it('sets cookie mode and the principal from the response', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    expect(store.authMode.value).toBe('cookie')
    expect(store.principal.value).toEqual(PRINCIPAL)
    expect(store.isLogged.value, 'the rest of the app asks isLogged').toBe(true)
  })

  it('loads the session details so permissions and instanceAdmin are known', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    expect(store.instanceAdmin.value).toBe(true)
    expect(store.isManager.value, 'an instance admin counts as a manager').toBe(true)
  })

  it('keeps the token empty so nothing leaks into the token keys', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    expect(store.token.value).toEqual({ name: '', secret: '' })
  })

  it('stores the must change password flag from the response', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { login: { data: { ...LOGIN.data, mustChangePassword: true } }, me: { data: { ...ME.data, mustChangePassword: true } } })

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    expect(store.mustChangePassword.value).toBe(true)
  })

  it('has no must change password flag by default', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    expect(store.mustChangePassword.value).toBe(false)
  })

  it('never writes the password to localStorage or sessionStorage', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })
    await flushPromises()

    const dump = (storage) => JSON.stringify(Object.fromEntries(
      Object.keys(storage).map(key => [key, storage.getItem(key)])
    ))
    expect(dump(localStorage), 'the password must not reach localStorage').not.toContain(PASSWORD)
    expect(dump(sessionStorage), 'the password must not reach sessionStorage').not.toContain(PASSWORD)
  })

  it('writes no cookie mode state to localStorage', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })
    await flushPromises()

    const keys = Object.keys(localStorage)
    expect(keys.filter(key => !['token-name', 'token-secret'].includes(key)),
      'only the legacy token keys may exist, and they stay empty').toEqual([])
    expect(localStorage.getItem('token-secret')).toBe('')
  })

  it('tells the other tabs who signed in and nothing else', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios)

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    expect(channel().posted).toEqual([{ type: 'signed-in', principal: PRINCIPAL }])
    expect(JSON.stringify(channel().posted), 'the password must not travel').not.toContain(PASSWORD)
  })

  it('turns on credentials and the csrf header for later requests', async () => {
    const { axios, store, client } = await loadSession()
    serve(axios)
    document.cookie = 'ingot_csrf=abc'

    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    const config = client.handleRequest({ method: 'post', headers: {} })
    expect(config.withCredentials).toBe(true)
    expect(config.headers['X-CSRF-Token']).toBe('abc')
  })

  it('stays signed out and rethrows when the server rejects the password', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios, { login: responseError(401) })

    await expect(store.signInWithPassword({ username: 'alice', password: 'wrong' })).rejects.toThrow()

    expect(store.isLogged.value).toBe(false)
    expect(store.authMode.value).toBeUndefined()
    expect(channel().posted, 'a failed sign in is nobody else\'s business').toEqual([])
  })
})

describe('sign out', () => {
  it('posts the logout in cookie mode and clears the local state', async () => {
    const { axios, store } = await loadSession()
    serve(axios)
    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    await store.signOut()

    expect(axios.post.mock.calls.some(call => call[0].endsWith('/api/auth/logout'))).toBe(true)
    expect(store.isLogged.value).toBe(false)
    expect(store.authMode.value).toBeUndefined()
    expect(store.principal.value).toBeUndefined()
    expect(store.mustChangePassword.value).toBe(false)
  })

  it('still carries the csrf header while the logout request is built', async () => {
    const { axios, store, client } = await loadSession()
    serve(axios)
    document.cookie = 'ingot_csrf=abc'
    await store.signInWithPassword({ username: 'alice', password: PASSWORD })
    let headers = null
    axios.post.mockImplementation((url) => {
      if (url.endsWith('/api/auth/logout')) {
        headers = client.handleRequest({ method: 'post', headers: {} }).headers
      }
      return Promise.resolve({ data: {} })
    })

    await store.signOut()

    expect(headers?.['X-CSRF-Token'], 'the mode may only end after the logout call went out').toBe('abc')
  })

  it('stops sending credentials once signed out', async () => {
    const { axios, store, client } = await loadSession()
    serve(axios)
    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    await store.signOut()

    expect(client.handleRequest({ method: 'get', headers: {} }).withCredentials).toBeUndefined()
  })

  it('tells the other tabs that the session ended', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios)
    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    await store.signOut()

    expect(channel().posted.at(-1)).toEqual({ type: 'signed-out' })
  })

  it('signs out locally even when the logout request fails', async () => {
    const { axios, store } = await loadSession()
    serve(axios)
    await store.signInWithPassword({ username: 'alice', password: PASSWORD })
    axios.post.mockRejectedValue(new Error('Network Error'))

    await store.signOut()

    expect(store.isLogged.value, 'the user asked to leave, a failed request must not keep them in').toBe(false)
  })

  it('confirms with a signed out notice', async () => {
    const { axios, store, toast } = await loadSession()
    serve(axios)
    await store.signInWithPassword({ username: 'alice', password: PASSWORD })

    await store.signOut()

    expect(toast.createInfoToast).toHaveBeenCalledWith('Signed out')
  })
})

describe('refresh and expiry in cookie mode', () => {
  const signedIn = async () => {
    const loadedSession = await loadSession()
    serve(loadedSession.axios)
    await loadedSession.store.signInWithPassword({ username: 'alice', password: PASSWORD })
    loadedSession.axios.post.mockClear()
    return loadedSession
  }

  it('refreshes once and retries when a request is rejected', async () => {
    const { axios, store, client } = await signedIn()
    axios.request.mockResolvedValue({ data: 'ok' })

    await client.handleResponseError(responseError(401, { url: '/api/x', method: 'get' }))

    expect(axios.post.mock.calls.filter(call => call[0].endsWith('/api/auth/refresh'))).toHaveLength(1)
    expect(axios.request).toHaveBeenCalledTimes(1)
    expect(store.isLogged.value, 'a refreshed session stays signed in').toBe(true)
  })

  it('keeps the session when a content request is refused after the refresh', async () => {
    // The details card asks for /<group>/maven-metadata.xml; a group that mirrors private members answers 401 for it.
    const { axios, store, client, toast } = await signedIn()
    axios.request.mockResolvedValue({ data: 'ok' })
    await client.handleResponseError(responseError(401, { url: '/onelitefeather/maven-metadata.xml', method: 'get' }))
    const retried = axios.request.mock.calls[0][0]

    await client.handleResponseError(responseError(401, retried)).catch(() => {})

    expect(store.isLogged.value, 'the session was refreshed, so the signed in user stays signed in').toBe(true)
    expect(toast.createWarningToast, 'no expiry notice for a refused listing').not.toHaveBeenCalled()
  })

  it('signs out when the refresh itself fails', async () => {
    const { axios, store, client, toast } = await signedIn()
    axios.post.mockRejectedValue(responseError(401, { url: '/api/auth/refresh', method: 'post' }))

    await client.handleResponseError(responseError(401, { url: '/api/x', method: 'get' })).catch(() => {})

    expect(store.isLogged.value).toBe(false)
    expect(store.authMode.value).toBeUndefined()
    expect(toast.createWarningToast, 'one expiry notice').toHaveBeenCalledTimes(1)
  })
})

describe('startup with a cookie session', () => {
  it('learns about a cookie session from GET /api/auth/me', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.initializeSession()

    expect(store.authMode.value).toBe('cookie')
    expect(store.principal.value).toEqual(PRINCIPAL)
    expect(store.instanceAdmin.value).toBe(true)
    expect(store.sessionReady.value).toBe(true)
  })

  describe('the must change password flag', () => {
    const meWith = (flag) => ({ data: { ...ME.data, ...(flag === undefined ? {} : { mustChangePassword: flag }) } })

    it('is read from GET /api/auth/me after a reload', async () => {
      const { axios, store } = await loadSession()
      serve(axios, { me: meWith(true) })

      await store.initializeSession()

      expect(store.mustChangePassword.value, 'a reload must not lose the flag').toBe(true)
    })

    it('is off when GET /api/auth/me does not send it', async () => {
      const { axios, store } = await loadSession()
      serve(axios, { me: meWith(undefined) })

      await store.initializeSession()

      expect(store.mustChangePassword.value).toBe(false)
    })

    it('is read from the answer that follows a refresh', async () => {
      const { axios, store } = await loadSession()
      document.cookie = 'ingot_csrf=abc'
      let answers = 0
      serve(axios)
      axios.get.mockImplementation((url) => {
        if (!url.endsWith('/api/auth/me')) return Promise.reject(new Error('unexpected'))
        answers++
        return answers === 1 ? Promise.reject(responseError(401)) : Promise.resolve(meWith(true))
      })

      await store.initializeSession()

      expect(store.mustChangePassword.value).toBe(true)
    })

    it('is read from GET /api/auth/me after a sign in, whatever the sign in response said', async () => {
      const { axios, store } = await loadSession()
      serve(axios, { me: meWith(true) })

      await store.signInWithPassword({ username: 'alice', password: PASSWORD })

      expect(store.mustChangePassword.value).toBe(true)
    })

    it('is cleared when the session is read again and the server no longer asks for a change', async () => {
      const { axios, store, channel } = await loadSession()
      serve(axios, { me: meWith(true) })
      await store.initializeSession()
      serve(axios, { me: meWith(undefined) })

      channel().receive({ type: 'signed-in', principal: PRINCIPAL })
      await flushPromises()

      expect(store.mustChangePassword.value, 'the server no longer asks for a change').toBe(false)
    })
  })

  it('is anonymous when the server answers 401', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { me: responseError(401) })

    await store.initializeSession()

    expect(store.authMode.value).toBeUndefined()
    expect(store.isLogged.value).toBe(false)
  })

  it('tries one refresh when an old csrf cookie shows that a session existed', async () => {
    const { axios, store } = await loadSession()
    document.cookie = 'ingot_csrf=abc'
    let answers = 0
    serve(axios)
    axios.get.mockImplementation((url) => {
      if (!url.endsWith('/api/auth/me')) return Promise.reject(new Error('unexpected'))
      answers++
      return answers === 1 ? Promise.reject(responseError(401)) : Promise.resolve(ME)
    })

    await store.initializeSession()

    expect(axios.post.mock.calls.filter(call => call[0].endsWith('/api/auth/refresh'))).toHaveLength(1)
    expect(store.authMode.value, 'the refreshed cookie session is picked up').toBe('cookie')
  })

  it('does not refresh when nothing suggests a session existed', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { me: responseError(401) })

    await store.initializeSession()

    expect(axios.post, 'an anonymous visit sends no refresh').not.toHaveBeenCalled()
  })

  it('stays anonymous and ready when the server cannot be reached', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { me: new Error('Network Error') })

    await store.initializeSession()

    expect(store.isLogged.value).toBe(false)
    expect(store.sessionReady.value, 'a route must not wait forever').toBe(true)
  })
})

describe('cross tab sync', () => {
  it('follows a signed-in message with the public identity', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios, { me: responseError(401) })
    await store.initializeSession()
    serve(axios)

    channel().receive({ type: 'signed-in', principal: PRINCIPAL })
    await flushPromises()

    expect(store.authMode.value).toBe('cookie')
    expect(store.principal.value).toEqual(PRINCIPAL)
    expect(store.isLogged.value, 'the details come from the server, not from the message').toBe(true)
  })

  it('follows a signed-out message', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios)
    await store.initializeSession()

    channel().receive({ type: 'signed-out' })

    expect(store.isLogged.value).toBe(false)
    expect(store.authMode.value).toBeUndefined()
  })

  it('does not echo a received message back to the other tabs', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios)
    await store.initializeSession()

    channel().receive({ type: 'signed-out' })

    expect(channel().posted, 'two tabs would otherwise answer each other forever').toEqual([])
  })

  it('keeps a token session when another tab signs out of a cookie session', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios, { me: ME })
    await store.signInWithToken('ci', 'secret')

    channel().receive({ type: 'signed-out' })

    expect(store.authMode.value, 'the token belongs to this tab').toBe('token')
  })

  it('holds no secret in state after a message with extra fields', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios, { me: responseError(401) })
    await store.initializeSession()
    serve(axios)

    channel().receive({ type: 'signed-in', principal: PRINCIPAL, secret: 'leaked', password: PASSWORD })
    await flushPromises()

    expect(JSON.stringify(store.principal.value)).not.toContain('leaked')
    expect(JSON.stringify(store.principal.value)).not.toContain(PASSWORD)
  })

  it('ignores a message it does not know', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios)
    await store.initializeSession()

    channel().receive({ type: 'reboot' })

    expect(store.isLogged.value).toBe(true)
  })
})

describe('token mode', () => {
  it('signs in with the name and secret and keeps the token keys', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.signInWithToken('ci', 's3cret')
    await flushPromises()

    expect(store.authMode.value).toBe('token')
    expect(store.token.value).toEqual({ name: 'ci', secret: 's3cret' })
    expect(localStorage.getItem('token-name')).toBe('ci')
    expect(localStorage.getItem('token-secret')).toBe('s3cret')
  })

  it('authenticates with the xBasic header and without cookies', async () => {
    const { axios, store, client } = await loadSession()
    serve(axios)

    await store.signInWithToken('ci', 's3cret')

    const meCall = axios.get.mock.calls.find(call => call[0].endsWith('/api/auth/me'))
    expect(meCall[1].headers.Authorization).toBe(`xBasic ${btoa('ci:s3cret')}`)
    expect(client.handleRequest({ method: 'post', headers: {} }).withCredentials,
      'token mode never sends cookies').toBeUndefined()
  })

  it('says nothing to the other tabs, because they follow the storage keys', async () => {
    const { axios, store, channel } = await loadSession()
    serve(axios)

    await store.signInWithToken('ci', 's3cret')

    expect(channel().posted).toEqual([])
  })

  it('signs out without a server call', async () => {
    const { axios, store } = await loadSession()
    serve(axios)
    await store.signInWithToken('ci', 's3cret')
    axios.post.mockClear()

    await store.signOut()

    expect(axios.post).not.toHaveBeenCalled()
    expect(store.isLogged.value).toBe(false)
    expect(store.token.value).toEqual({ name: '', secret: '' })
  })

  it('keeps the old login action working', async () => {
    const { axios, store } = await loadSession()
    serve(axios)

    await store.login('ci', 's3cret')

    expect(store.isLogged.value).toBe(true)
  })
})

describe('providers and setup', () => {
  it('loads the providers and the setup flag', async () => {
    const { axios, store } = await loadSession()
    serve(axios, {
      providers: {
        providers: [{ id: 'local', type: 'LOCAL', label: 'Password' }],
        tokenLogin: true,
        setupRequired: true
      }
    })

    await store.loadProviders()

    expect(store.providers.value).toEqual([{ id: 'local', type: 'LOCAL', label: 'Password' }])
    expect(store.setupRequired.value).toBe(true)
  })

  it('posts the setup request and signs in from its response', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { setup: { data: { principal: PRINCIPAL } } })

    await store.completeSetup({ setupCode: 'abc', username: 'alice', password: PASSWORD, displayName: 'Alice' })

    const setupCall = axios.post.mock.calls.find(call => call[0].endsWith('/api/auth/setup'))
    expect(setupCall[1]).toEqual({ setupCode: 'abc', username: 'alice', password: PASSWORD, displayName: 'Alice' })
    expect(axios.post.mock.calls.some(call => call[0].endsWith('/api/auth/login')),
      'the setup response already signed the user in').toBe(false)
    expect(store.authMode.value).toBe('cookie')
    expect(store.setupRequired.value).toBe(false)
  })

  it('signs in with the same credentials when the setup response has no principal', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { setup: { data: {} } })

    await store.completeSetup({ setupCode: 'abc', username: 'alice', password: PASSWORD })

    const loginCall = axios.post.mock.calls.find(call => call[0].endsWith('/api/auth/login'))
    expect(loginCall[1]).toEqual({ provider: 'local', username: 'alice', password: PASSWORD })
    expect(store.authMode.value).toBe('cookie')
  })
})
