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
import type { Mock } from 'vitest'
import type { AxiosError } from 'axios'

// The second step of a password sign in, as the session store runs it. The server and the browser's WebAuthn
// are doubles; every test loads a fresh copy of the session module.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

vi.mock('./webauthn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./webauthn')>()),
  getPasskeyAssertion: vi.fn(),
  isWebAuthnSupported: vi.fn(() => true)
}))

const PRINCIPAL = { id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice' }
const SESSION_ANSWER = { data: { principal: PRINCIPAL, mustChangePassword: false, expiresAt: '2026-10-15T12:00:00Z' } }
const CHALLENGE_ANSWER = { data: { mfaRequired: true, methods: ['AUTHENTICATOR', 'RECOVERY_CODE'] } }
const ME = { data: { permissions: [], routes: [], principal: PRINCIPAL, teams: [], grants: [], instanceAdmin: false } }

// A passkey assertion of the server's shape, in base64url. The values are fixed.
const ASSERTION = { credentialId: 'Y3JlZA', clientDataJSON: 'Y2xpZW50', authenticatorData: 'YXV0aA', signature: 'c2ln' }

const httpError = (status: number, data: Record<string, unknown> = {}, headers: Record<string, string> = {}) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data, headers }
  }) as unknown as AxiosError

type AxiosDouble = { get: Mock; post: Mock; request: Mock }

// Routes the mocked axios by URL. Each test states only the answers it needs; anything else is an error.
const serve = (axios: AxiosDouble, answers: Record<string, unknown>) => {
  const answer = (url: string) => {
    const key = Object.keys(answers).find(path => url.endsWith(path))
    if (key === undefined) return Promise.reject(new Error(`unexpected request ${url}`))
    const reply = answers[key]
    return reply instanceof Error ? Promise.reject(reply) : Promise.resolve(reply)
  }
  axios.get.mockImplementation(answer)
  axios.post.mockImplementation(answer)
}

const loadSession = async () => {
  localStorage.clear()
  sessionStorage.clear()
  vi.resetModules()
  const axios = (await import('axios')).default as unknown as AxiosDouble
  axios.get.mockReset()
  axios.post.mockReset()
  const session = await import('./session')
  const webauthn = await import('./webauthn')
  return { axios, session, store: session.useSession(), webauthn }
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('password answer that asks for a second factor', () => {
  it('resolves with the methods and starts no session', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/auth/login': CHALLENGE_ANSWER })

    const challenge = await store.signInWithPassword({ username: 'alice', password: 'pw-12345678901' })

    expect(challenge, 'the dialog needs the methods to show the second step').toEqual({
      mfaRequired: true,
      methods: ['AUTHENTICATOR', 'RECOVERY_CODE'],
    })
    expect(store.principal.value, 'no principal before the second factor is verified').toBeUndefined()
    expect(store.authMode.value, 'no session mode before the second factor is verified').toBeUndefined()
  })

  it('still resolves with nothing and starts the session when the answer carries no second factor', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/auth/login': SESSION_ANSWER, '/api/auth/me': ME })

    const challenge = await store.signInWithPassword({ username: 'alice', password: 'pw-12345678901' })

    expect(challenge).toBeUndefined()
    expect(store.principal.value?.name, 'a password sign in without a factor signs the user in as before').toBe('alice')
  })
})

describe('completing the second step with a code', () => {
  it('posts the code to the verify route and starts the session from its answer', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/auth/mfa/verify': SESSION_ANSWER, '/api/auth/me': ME })

    await store.completeSecondFactor('123456')

    expect(axios.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/auth\/mfa\/verify$/), { code: '123456' }, expect.anything())
    expect(store.principal.value?.name, 'the session starts only after the code is accepted').toBe('alice')
  })

  it('leaves the session unstarted and rejects when the code is wrong', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/auth/mfa/verify': httpError(401, { status: 401, message: 'Invalid code' }) })

    await expect(store.completeSecondFactor('000000')).rejects.toMatchObject({ response: { status: 401 } })
    expect(store.principal.value, 'a wrong code does not sign the user in').toBeUndefined()
  })
})

describe('completing the second step with a passkey', () => {
  it('takes the options, runs the ceremony and posts the assertion with its challenge id', async () => {
    const { axios, store, webauthn } = await loadSession()
    serve(axios, {
      '/api/auth/mfa/passkey/options': { data: { challengeId: 'ceremony-9', challenge: '-_-_', rpId: 'ingot.test', allowCredentials: ['AAE'] } },
      '/api/auth/mfa/verify': SESSION_ANSWER,
      '/api/auth/me': ME,
    })
    vi.mocked(webauthn.getPasskeyAssertion).mockResolvedValue(ASSERTION)

    await store.completePasskeySecondFactor()

    expect(webauthn.getPasskeyAssertion).toHaveBeenCalledWith({ challenge: '-_-_', rpId: 'ingot.test', allowCredentials: ['AAE'] })
    expect(axios.post).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/auth\/mfa\/verify$/),
      { passkey: { challengeId: 'ceremony-9', ...ASSERTION } },
      expect.anything()
    )
    expect(store.principal.value?.name).toBe('alice')
  })

  it('stays pending and rejects with the cancellation when the browser prompt is dismissed', async () => {
    const { axios, store, webauthn } = await loadSession()
    serve(axios, {
      '/api/auth/mfa/passkey/options': { data: { challengeId: 'ceremony-9', challenge: '-_-_', rpId: 'ingot.test', allowCredentials: ['AAE'] } },
    })
    vi.mocked(webauthn.getPasskeyAssertion).mockRejectedValue(new webauthn.WebAuthnCancelledError())

    await expect(store.completePasskeySecondFactor()).rejects.toBeInstanceOf(webauthn.WebAuthnCancelledError)
    expect(axios.post, 'a cancelled prompt sends no assertion').not.toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/auth\/mfa\/verify$/),
      expect.anything(),
      expect.anything()
    )
    expect(store.principal.value).toBeUndefined()
  })

  it('refuses without asking the server when the browser has no WebAuthn', async () => {
    const { axios, store, webauthn } = await loadSession()
    serve(axios, {})
    vi.mocked(webauthn.isWebAuthnSupported).mockReturnValue(false)

    await expect(store.completePasskeySecondFactor()).rejects.toBeInstanceOf(webauthn.WebAuthnUnsupportedError)
    expect(axios.post, 'an unsupported browser must not start a passkey challenge').not.toHaveBeenCalled()
  })
})

describe('second step failure messages', () => {
  it('shows the server message of a wrong code, which keeps the step open', async () => {
    const { session } = await loadSession()

    expect(session.secondFactorFailureMessage(httpError(401, { status: 401, message: 'Invalid code' }))).toBe('Invalid code')
  })

  it('names the code as the reason when the server gives no message', async () => {
    const { session } = await loadSession()

    expect(session.secondFactorFailureMessage(httpError(401))).toBe('The code is not valid')
  })

  it('says how long to wait when the server rate limits the second step', async () => {
    const { session } = await loadSession()

    expect(session.secondFactorFailureMessage(httpError(429, {}, { 'Retry-After': '60' }))).toBe(
      (await import('../helpers/rate-limit')).rateLimitMessage(60)
    )
  })

  it('shows the reason of a cancelled passkey prompt', async () => {
    const { session, webauthn } = await loadSession()

    expect(session.secondFactorFailureMessage(new webauthn.WebAuthnCancelledError())).toBe('The passkey request was cancelled')
  })
})
