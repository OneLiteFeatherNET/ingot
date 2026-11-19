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

// The step-up of a sensitive action, as the session store runs it. The server is a double: every test loads a fresh copy of
// the session module, so no state is shared between tests.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

const SECOND_FACTOR_NEEDED = { status: 403, message: 'step_up_required', problems: [{ node: 'AUTHENTICATOR' }, { node: 'RECOVERY_CODE' }] }
const SECURITY = {
  factorCounts: { authenticator: 0, passkey: 0 },
  recoveryCodesLeft: 0,
  policy: { covered: true, requiredForAdmins: true, requiredForTeams: [] },
  graceUntil: '2026-12-02T00:00:00Z',
  scope: 'enrol',
  stepUpUntil: null
}

const httpError = (status: number, data: Record<string, unknown> = {}) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data, headers: {} }
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
  return { axios, session, store: session.useSession() }
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('withStepUp runs a sensitive action', () => {
  it('runs the action once and returns its result when no second factor is needed', async () => {
    const { store } = await loadSession()
    const action = vi.fn().mockResolvedValue('token-created')

    await expect(store.withStepUp(action)).resolves.toBe('token-created')
    expect(action, 'the action runs exactly once').toHaveBeenCalledTimes(1)
    expect(store.stepUp.value, 'the step-up dialog stays closed').toBeUndefined()
  })

  it('rethrows an error that is not a step-up without opening the dialog', async () => {
    const { store } = await loadSession()
    const action = vi.fn().mockRejectedValue(httpError(500, { message: 'boom' }))

    await expect(store.withStepUp(action)).rejects.toMatchObject({ response: { status: 500 } })
    expect(store.stepUp.value, 'only step_up_required opens the dialog').toBeUndefined()
  })

  it('opens the dialog with the methods the server listed when step_up_required is answered', async () => {
    const { store } = await loadSession()
    const action = vi.fn().mockRejectedValueOnce(httpError(403, SECOND_FACTOR_NEEDED)).mockResolvedValueOnce('token-created')

    const pending = store.withStepUp(action)
    await vi.waitFor(() => expect(store.stepUp.value, 'the dialog opens on the answer').toBeDefined())

    expect(store.stepUp.value?.methods).toEqual(['AUTHENTICATOR', 'RECOVERY_CODE'])
    expect(action, 'the action waits for the second factor').toHaveBeenCalledTimes(1)
    store.cancelStepUp()
    await expect(pending).rejects.toMatchObject({ response: { status: 403 } })
  })

  it('retries the action once after the second factor is confirmed', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/identity/me/step-up': { data: { authenticatedAt: '2026-11-18T10:00:00Z', stepUpUntil: '2026-11-18T10:05:00Z' } } })
    const action = vi.fn().mockRejectedValueOnce(httpError(403, SECOND_FACTOR_NEEDED)).mockResolvedValueOnce('token-created')

    const pending = store.withStepUp(action)
    await vi.waitFor(() => expect(store.stepUp.value).toBeDefined())
    await store.confirmStepUp('123456')

    await expect(pending, 'the retried result is returned').resolves.toBe('token-created')
    expect(action, 'the action ran twice: once refused, once retried').toHaveBeenCalledTimes(2)
    expect(axios.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/step-up$/), { code: '123456' }, expect.anything())
    expect(store.stepUp.value, 'the dialog closes after the second factor').toBeUndefined()
  })

  it('does not retry again when the retried action answers step_up_required once more', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/identity/me/step-up': { data: { authenticatedAt: '2026-11-18T10:00:00Z', stepUpUntil: '2026-11-18T10:05:00Z' } } })
    const action = vi.fn().mockRejectedValue(httpError(403, SECOND_FACTOR_NEEDED))

    const pending = store.withStepUp(action)
    await vi.waitFor(() => expect(store.stepUp.value).toBeDefined())
    await store.confirmStepUp('123456')

    await expect(pending, 'the second refusal is the error of the call').rejects.toMatchObject({ response: { status: 403 } })
    expect(action, 'the action is not retried a second time').toHaveBeenCalledTimes(2)
    expect(store.stepUp.value, 'the dialog is closed with the error').toBeUndefined()
  })

  it('keeps the dialog open with the server message when the code is wrong', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/identity/me/step-up': httpError(401, { status: 401, message: 'Invalid code' }) })
    const action = vi.fn().mockRejectedValue(httpError(403, SECOND_FACTOR_NEEDED))

    const pending = store.withStepUp(action)
    await vi.waitFor(() => expect(store.stepUp.value).toBeDefined())
    await store.confirmStepUp('000000')

    expect(store.stepUp.value?.message, 'the wrong code is named in the dialog').toBe('Invalid code')
    expect(store.stepUp.value?.busy, 'the dialog is no longer busy').toBe(false)
    store.cancelStepUp()
    await expect(pending).rejects.toMatchObject({ response: { status: 403 } })
  })

  it('rejects with the original refusal and never retries when the dialog is dismissed', async () => {
    const { store } = await loadSession()
    const action = vi.fn().mockRejectedValue(httpError(403, SECOND_FACTOR_NEEDED))

    const pending = store.withStepUp(action)
    await vi.waitFor(() => expect(store.stepUp.value).toBeDefined())
    store.cancelStepUp()

    await expect(pending).rejects.toMatchObject({ response: { status: 403 } })
    expect(action, 'a dismissed dialog must not retry the action').toHaveBeenCalledTimes(1)
    expect(store.stepUp.value).toBeUndefined()
  })

  it('does not open the dialog for a step-up that lists no second factor the user can use', async () => {
    const { store } = await loadSession()
    const action = vi.fn().mockRejectedValue(httpError(403, { status: 403, message: 'step_up_required', problems: [] }))

    await expect(store.withStepUp(action)).rejects.toMatchObject({ response: { status: 403 } })
    expect(store.stepUp.value, 'there is nothing to confirm with').toBeUndefined()
  })
})

describe('security status of the session', () => {
  it('reads the grace end and the enrolment scope from the security status', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/identity/me/security': { data: SECURITY } })

    await store.loadSecurityStatus()

    expect(store.graceUntil.value, 'the banner shows the end the server gives').toBe('2026-12-02T00:00:00Z')
    expect(store.restricted.value, 'the enrolment scope restricts the session').toBe(true)
  })

  it('reads a full scope without a grace end as neither banner nor restriction', async () => {
    const { axios, store } = await loadSession()
    serve(axios, { '/api/identity/me/security': { data: { ...SECURITY, graceUntil: null, scope: 'full' } } })

    await store.loadSecurityStatus()

    expect(store.graceUntil.value).toBeUndefined()
    expect(store.restricted.value).toBe(false)
  })
})
