/*
 * Copyright (c) 2023 dzikoysk
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

import { computed, ref, watch, watchEffect } from "vue"
import { createClient, readCsrfToken, setCookieMode, setUnauthorizedHandler } from './client'
import { openSessionChannel } from './session-channel'
import { createInfoToast, createWarningToast, errorMessage } from '../helpers/toast'
import { rateLimitMessage, retryAfterSeconds } from '../helpers/rate-limit'

// The access token of a token sign in. These two keys are the one legacy place where a secret
// sits in localStorage; they are kept so existing token sessions survive, and they stay empty
// in cookie mode. No password and no cookie session state is ever stored.
const token = ref({
  name: localStorage.getItem('token-name') || '',
  secret: localStorage.getItem('token-secret') || '',
})
const setToken = (name, secret) => {
  token.value = { name, secret }
}

watchEffect(() => {
  localStorage.setItem('token-name', token.value.name)
  localStorage.setItem('token-secret', token.value.secret)
})

// The one notion of "the session is known". False until the start-up check has answered, which
// is a stored token being verified or the server being asked about a cookie session: a listing
// requested before the answer is known would be requested again once it arrives, and a route
// that needs a manager must not decide before then. Views read the flag as a ref; the router
// waits for it through whenSessionKnown().
const sessionReady = ref(false)

// How this tab is signed in: "cookie" (HttpOnly cookies, the server holds the session),
// "token" (an access token kept in the legacy storage keys) or undefined (anonymous).
const authMode = ref(undefined)
const principal = ref(undefined)
const mustChangePassword = ref(false)
// What GET /api/auth/providers last said, for the sign in dialog.
const providers = ref([])
const setupRequired = ref(false)

/**
 * @type {(undefined|{
 *    accessToken: {
 *      identifier: {
 *        type: string,
 *        value: number
 *      },
 *      name: string,
 *      createdAt: string,
 *      description: string
 *    },
 *    permissions: {
 *      identifier: string,
 *      shortcut: string
 *    }[],
 *    routes: {
 *      path: string,
 *      permission: {
 *        identifier: string,
 *        shortcut: string
*       }
 *    }[],
 *    principal?: { id: string, kind: string, name: string, displayName: string },
 *    instanceAdmin?: boolean
 *  })}
 */
const defaultDetails = undefined
const details = ref(defaultDetails)

const clearSession = () => {
  setToken('', '')
  details.value = undefined
  authMode.value = undefined
  principal.value = undefined
  mustChangePassword.value = false
  setCookieMode(false)
}

// The answer of GET /api/auth/me, in either mode.
const applyMe = (data) => {
  details.value = data
  if (data?.principal) {
    principal.value = data.principal
  }
  if (typeof data?.mustChangePassword === 'boolean') {
    mustChangePassword.value = data.mustChangePassword
  }
}

const login = (name, secret) =>
  createClient(name, secret)
    .auth
    .me()
    .then(response => {
      setCookieMode(false)
      setToken(name, secret)
      authMode.value = 'token'
      applyMe(response.data)
    })

const signInWithToken = login

const anonymousApi = () => createClient('', '').auth
const channel = openSessionChannel()

// Asks the server for the session behind the cookies. Rejects when it cannot be answered.
const loadCookieDetails = () =>
  anonymousApi().meWithCookie().then(response => applyMe(response.data))

// The session started in this tab: the sign in response names the principal, then the server is
// asked for the permissions that go with it. Other tabs are told who signed in, and nothing else.
const startCookieSession = async (data) => {
  setCookieMode(true)
  setToken('', '')
  authMode.value = 'cookie'
  principal.value = data?.principal
  mustChangePassword.value = data?.mustChangePassword === true
  try {
    await loadCookieDetails()
  } catch {
    // Signed in, but the details could not be read; the views see a session without extra rights.
    details.value = details.value ?? { permissions: [], routes: [] }
  }
  channel.signedIn(principal.value)
}

const signInWithPassword = ({ username, password }) =>
  anonymousApi()
    .login({ username, password })
    .then(response => startCookieSession(response.data))

// First run: the setup response signs the new administrator in. A server that answers without a
// principal gets the same credentials once more as an ordinary sign in.
const completeSetup = async ({ setupCode, username, password, displayName }) => {
  const response = await anonymousApi().setup({ setupCode, username, password, displayName })
  if (response.data?.principal) {
    await startCookieSession(response.data)
  } else {
    await signInWithPassword({ username, password })
  }
  setupRequired.value = false
}

const loadProviders = () =>
  anonymousApi().providers().then(response => {
    providers.value = response.data?.providers ?? []
    setupRequired.value = response.data?.setupRequired === true
    return response.data
  })

// The sign in dialog shows this under the fields. A rejected pair is the one case worth
// naming, because "Request failed with status 401" tells the user nothing they can act on.
const loginFailureMessage = (error) =>
  error?.response?.status === 401
    ? 'Name or secret is incorrect'
    : errorMessage(error)

// The wording for a rejected password. A disabled account answers 403, and both are shown
// alike, so the message never tells whether a user exists. A 429 is the rate limit: its
// Retry-After header says how long to wait.
const passwordFailureMessage = (error) => {
  const status = error?.response?.status
  if (status === 429) return rateLimitMessage(retryAfterSeconds(error))
  return [401, 403].includes(status) ? 'Username or password is incorrect' : errorMessage(error)
}

const setupFailureMessage = (error) => {
  switch (error?.response?.status) {
    case 401: return 'Setup code is incorrect'
    case 409: return 'Ingot is already set up'
    default: return errorMessage(error)
  }
}

const finishSignOut = () => {
  const mode = authMode.value
  clearSession()
  if (mode === 'cookie') {
    channel.signedOut()
  }
  createInfoToast('Signed out')
}

// A cookie session is ended on the server first, while the CSRF header is still sent. A request
// that fails does not keep the user signed in here. A token session is only forgotten locally.
const signOut = () => {
  if (authMode.value !== 'cookie') {
    finishSignOut()
    return Promise.resolve()
  }
  return anonymousApi().logout().catch(() => {}).then(finishSignOut)
}

const logout = signOut

// The server rejected the credentials of a request made while signed in. Once the session
// is cleared, every later rejection in the same burst finds nobody signed in and does
// nothing, so the user sees exactly one notice.
const expireSession = () => {
  if (!isLogged.value) {
    return
  }
  clearSession()
  createWarningToast('Session expired, please sign in again')
}

const client = computed(() => createClient(token.value.name, token.value.secret))

// A rejected secret stays useless on every later visit, so it is forgotten here. A server
// that cannot be reached at all says nothing about the credentials and keeps them. Without
// a stored token the server is asked about a cookie session instead.
const initializeSession = async () => {
  shareSession()
  try {
    if (token.value.name && token.value.secret) {
      try {
        await login(token.value.name, token.value.secret)
        return
      } catch (error) {
        if (error?.response?.status !== 401) {
          throw error
        }
        clearSession()
      }
    }
    await probeCookieSession()
  } finally {
    sessionReady.value = true
  }
}

// Only the server can say whether HttpOnly cookies hold a session. When the access cookie has
// run out, a readable CSRF cookie shows that a session once existed, and one refresh is tried
// before the visit counts as anonymous. A visit that never signed in sends no refresh.
const probeCookieSession = async () => {
  const api = anonymousApi()
  try {
    let response
    try {
      response = await api.meWithCookie()
    } catch (error) {
      if (error?.response?.status !== 401 || !readCsrfToken()) {
        throw error
      }
      await api.refresh()
      response = await api.meWithCookie()
    }
    setCookieMode(true)
    authMode.value = 'cookie'
    applyMe(response.data)
  } catch {
    // Anonymous, or the server could not be reached; the views report their own failures.
  }
}

// Resolves once sessionReady is true. The watcher is stopped as soon as it has fired, so a
// settled wait holds no listener.
const whenSessionKnown = () => new Promise(resolve => {
  if (sessionReady.value) {
    resolve()
    return
  }
  const stop = watch(sessionReady, ready => {
    if (!ready) return
    stop()
    resolve()
  }, { flush: 'sync' })
})

// The session is shared by all tabs of the browser: a login or a logout in one tab is followed
// by the others. A token session travels through two storage keys, so the handler only reacts
// to changes of those keys and only acts when the stored session differs from the one held here.
// A cookie session travels through the session channel, which carries no secret.
const SESSION_KEYS = ['token-name', 'token-secret']
let sharing = false
let stopListening = () => {}

const onStorage = (event) => {
  if (event.key !== null && !SESSION_KEYS.includes(event.key)) {
    return
  }
  const name = localStorage.getItem('token-name') || ''
  const secret = localStorage.getItem('token-secret') || ''

  if (!name || !secret) {
    if (token.value.name || token.value.secret) {
      clearSession()
    }
    return
  }
  if (name === token.value.name && secret === token.value.secret) {
    return
  }
  // A rejected pair leaves this tab as it was; the other tab has already been told it failed.
  login(name, secret).catch(() => {})
}

// A cookie session started or ended in another tab. The message names only the principal; the
// details of the session are read from the server, so nothing secret depends on the channel.
const onChannelMessage = (message) => {
  if (message.type === 'signed-out') {
    if (authMode.value === 'cookie') {
      clearSession()
    }
    return
  }
  if (authMode.value === 'token') {
    return
  }
  setCookieMode(true)
  authMode.value = 'cookie'
  principal.value = message.principal
  loadCookieDetails().catch(() => {
    if (authMode.value === 'cookie') {
      clearSession()
    }
  })
}

const shareSession = () => {
  if (sharing) return
  window.addEventListener('storage', onStorage)
  stopListening = channel.subscribe(onChannelMessage)
  sharing = true
}

const stopSharingSession = () => {
  window.removeEventListener('storage', onStorage)
  stopListening()
  stopListening = () => {}
  sharing = false
}

const isLogged = computed(() => details.value !== undefined)

const instanceAdmin = computed(() => details.value?.instanceAdmin === true)

// The server's instanceAdmin answer decides when it sends one; the access-token manager
// permission is the fallback for a server that does not.
const isManager = computed(() => {
  if (typeof details.value?.instanceAdmin === 'boolean') {
    return details.value.instanceAdmin
  }
  return details.value
    ?.permissions
    ?.some(entry => entry.identifier === 'access-token:manager') === true
})

const hasPermissionTo = (path, permission) => {
  if (isManager.value) {
    return true
  }
  return details.value
    ?.routes
    ?.find(route => path.startsWith(route.path) && route.permission.identifier === permission) !== undefined
}

setUnauthorizedHandler(expireSession)

export function useSession() {
  return {
    token,
    details,
    login,
    logout,
    signInWithPassword,
    signInWithToken,
    signOut,
    completeSetup,
    loadProviders,
    authMode,
    principal,
    instanceAdmin,
    mustChangePassword,
    providers,
    setupRequired,
    loginFailureMessage,
    passwordFailureMessage,
    setupFailureMessage,
    isLogged,
    client,
    isManager,
    hasPermissionTo,
    initializeSession,
    sessionReady
  }
}

export { loginFailureMessage, passwordFailureMessage, setupFailureMessage, stopSharingSession, whenSessionKnown }
