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
import type { Mock } from 'vitest'
import type { AxiosError } from 'axios'
import type * as Client from './client'

// The request config as far as these tests read it: the headers and the status check.
type RequestConfig = { headers?: Record<string, string | undefined>; validateStatus?: (status: number) => boolean }

// The parts of the mocked axios default export that these tests drive.
type MockedAxios = {
  get: Mock<(url: string, config?: RequestConfig) => Promise<unknown>>
  post: Mock<(url: string, body?: unknown, config?: RequestConfig) => Promise<unknown>>
}

// The real axios is never reached. The module only needs the interceptor registry to exist,
// and the handler under test is called directly with a hand-made error.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

const responseError = (status: number): AxiosError =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} }
  }) as unknown as AxiosError

describe('client response interceptor', () => {
  let client: typeof Client

  beforeEach(async () => {
    vi.resetModules()
    client = await import('./client')
  })

  it('hands a 401 response to the registered unauthorized handler', async () => {
    const handler = vi.fn()
    client.setUnauthorizedHandler(handler)
    const error = responseError(401)

    await client.handleResponseError(error).catch(() => {})

    expect(handler, 'a 401 must reach the unauthorized handler exactly once').toHaveBeenCalledTimes(1)
    expect(handler).toHaveBeenCalledWith(error)
  })

  it('does not hand a 500 response to the unauthorized handler', async () => {
    const handler = vi.fn()
    client.setUnauthorizedHandler(handler)

    await client.handleResponseError(responseError(500)).catch(() => {})

    expect(handler, 'only 401 means the credentials were rejected').not.toHaveBeenCalled()
  })

  it('rethrows the failure so the caller still sees it', async () => {
    client.setUnauthorizedHandler(() => {})
    const error = responseError(401)

    await expect(client.handleResponseError(error), 'the interceptor must not swallow the error')
      .rejects.toBe(error)
  })
})

describe('token mode session check on 401', () => {
  let axios: MockedAxios
  let client: typeof Client
  let handler: Mock

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default as unknown as MockedAxios
    axios.get.mockReset()
    client = await import('./client')
    handler = vi.fn()
    client.setUnauthorizedHandler(handler)
  })

  // A listing request sent with the access token of a token sign in.
  const tokenRequestError = () => Object.assign(responseError(401), {
    config: { url: '/api/maven/details/private', method: 'get', headers: { Authorization: 'xBasic dG9rZW46c2VjcmV0' } }
  })

  it('keeps the token session when the session check still accepts it', async () => {
    axios.get.mockResolvedValue({ data: {} })
    const error = tokenRequestError()

    await expect(client.handleResponseError(error)).rejects.toBe(error)

    expect(axios.get.mock.calls[0][0], 'the session is asked about with the same credentials').toMatch(/\/api\/auth\/me$/)
    expect(axios.get.mock.calls[0][1]?.headers?.Authorization).toBe('xBasic dG9rZW46c2VjcmV0')
    expect(handler, 'a 401 on a listing is not a rejected token').not.toHaveBeenCalled()
  })

  it('ends the token session when the session check is refused too', async () => {
    axios.get.mockRejectedValue(responseError(401))
    const error = tokenRequestError()

    await expect(client.handleResponseError(error)).rejects.toBe(error)

    expect(handler, 'the server rejected the token, so the session ends').toHaveBeenCalledWith(error)
  })

  it('keeps the token session when the session check cannot reach the server', async () => {
    axios.get.mockRejectedValue(new Error('Network Error'))

    await client.handleResponseError(tokenRequestError()).catch(() => {})

    expect(handler, 'an unreachable server says nothing about the token').not.toHaveBeenCalled()
  })
})

describe('client repositories call', () => {
  // The module and the axios mock are loaded together, so the client sees the mock the test inspects.
  let http: MockedAxios

  beforeEach(async () => {
    vi.resetModules()
    http = (await import('axios')).default as unknown as MockedAxios
    http.get.mockReset()
    http.get.mockResolvedValue({ data: { repositories: [] } })
  })

  it('requests the overview without a query when no filter is given', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.repositories()

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/repositories$/)
  })

  it('writes the text as q and every tag as its own tag parameter, which the endpoint reads as AND', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.repositories({ q: 'nightly builds', tags: ['team-core', 'ci'] })

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/repositories\?q=nightly\+builds&tag=team-core&tag=ci$/)
  })
})

describe('maintenance endpoints', () => {
  let axios: MockedAxios
  let client: typeof Client

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default as unknown as MockedAxios
    axios.get.mockReset()
    axios.post.mockReset()
    client = await import('./client')
  })

  it('reads the storage figures from the storage endpoint', async () => {
    await client.createClient('ci', 'secret').maven.storage()

    expect(axios.get.mock.calls[0][0]).toMatch(/\/api\/maven\/storage$/)
  })

  it('asks for a storage refresh with a POST to the refresh endpoint', async () => {
    await client.createClient('ci', 'secret').maven.refreshStorage()

    expect(axios.post.mock.calls[0][0]).toMatch(/\/api\/maven\/storage\/refresh$/)
  })

  it('posts a dry run to the policy it names, with the id escaped', async () => {
    await client.createClient('ci', 'secret').cleanup.dryRun('weekly snapshots')

    expect(axios.post.mock.calls[0][0]).toMatch(/\/api\/cleanup\/policies\/weekly%20snapshots\/dry-run$/)
  })

  it('posts a run to the policy it names', async () => {
    await client.createClient('ci', 'secret').cleanup.run('snapshots-weekly')

    expect(axios.post.mock.calls[0][0]).toMatch(/\/api\/cleanup\/policies\/snapshots-weekly\/run$/)
  })

  it('lists every run when no policy is named', async () => {
    await client.createClient('ci', 'secret').cleanup.runs()

    expect(axios.get.mock.calls[0][0]).toMatch(/\/api\/cleanup\/runs$/)
  })

  it('lists the runs of one policy when it is named', async () => {
    await client.createClient('ci', 'secret').cleanup.runs('snapshots-weekly')

    expect(axios.get.mock.calls[0][0]).toMatch(/\/api\/cleanup\/runs\?policy=snapshots-weekly$/)
  })
})

describe('status probe', () => {
  let axios: MockedAxios
  let client: typeof Client

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default as unknown as MockedAxios
    axios.get.mockReset()
    axios.get.mockResolvedValue({ status: 401, headers: {}, data: '' })
    client = await import('./client')
  })

  it('sends the session credentials, so a protected integration can answer 200', async () => {
    await client.createClient('ci', 'secret').status.probe('/metrics')

    expect(axios.get.mock.calls[0][1]?.headers?.Authorization, 'the probe must authenticate like every other call')
      .toBe(`xBasic ${btoa('ci:secret')}`)
  })

  it('sends no credentials when nobody is signed in', async () => {
    await client.createClient('', '').status.probe('/metrics')

    expect(axios.get.mock.calls[0][1]?.headers, 'an anonymous probe carries no authorization header').toBeUndefined()
  })

  it('treats any status as an answer, so a 401 does not reach the error path', async () => {
    await client.createClient('ci', 'secret').status.probe('/metrics')

    expect(axios.get.mock.calls[0][1]?.validateStatus?.(401)).toBe(true)
  })
})

describe('client members call', () => {
  let http: MockedAxios

  beforeEach(async () => {
    vi.resetModules()
    http = (await import('axios')).default as unknown as MockedAxios
    http.get.mockReset()
    http.get.mockResolvedValue({ data: { members: [] } })
  })

  it('asks for the references of a group with the repository escaped in the path', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.members('public')

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/repositories\/public\/members$/)
  })
})

describe('client search calls', () => {
  let http: MockedAxios

  beforeEach(async () => {
    vi.resetModules()
    http = (await import('axios')).default as unknown as MockedAxios
    http.get.mockReset()
    http.get.mockResolvedValue({ data: {} })
  })

  it('requests the search without a query string when the query is empty', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.search('')

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/search$/)
  })

  it('passes the query string of the page through to the search endpoint', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.search('q=commons&extension=jar&extension=pom')

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/search\?q=commons&extension=jar&extension=pom$/)
  })

  it('asks for the files of a version with its coordinates, and repeats the repository filter', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.searchFiles({
      groupId: 'org.apache', artifactId: 'lang', version: '1.0', origin: 'mirror', repository: ['a', 'b'], page: 1, size: 100
    })

    expect(http.get.mock.calls[0][0]).toMatch(
      /\/api\/maven\/search\/files\?groupId=org\.apache&artifactId=lang&version=1\.0&origin=mirror&repository=a&repository=b&page=1&size=100$/
    )
  })

  it('leaves out the origin of the files request while it is the default', async () => {
    const { createClient } = await import('./client')

    await createClient(undefined, undefined).maven.searchFiles({ groupId: 'g', artifactId: 'a', version: '1' })

    expect(http.get.mock.calls[0][0]).not.toContain('origin=')
  })
})

describe('client search index calls', () => {
  let http: MockedAxios

  beforeEach(async () => {
    vi.resetModules()
    http = (await import('axios')).default as unknown as MockedAxios
    http.get.mockReset()
    http.post.mockReset()
    http.get.mockResolvedValue({ data: {} })
    http.post.mockResolvedValue({ status: 202, data: { runId: 7 } })
  })

  it('reads the index status from the admin status endpoint', async () => {
    const { createClient } = await import('./client')

    await createClient('ci', 'secret').maven.searchIndex()

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/search\/index$/)
  })

  it('starts a rebuild of every repository with an empty body', async () => {
    const { createClient } = await import('./client')

    await createClient('ci', 'secret').maven.startIndexRebuild({})

    expect(http.post.mock.calls[0][0]).toMatch(/\/api\/maven\/search\/index\/rebuild$/)
    expect(http.post.mock.calls[0][1]).toEqual({})
  })

  it('starts a rebuild of one repository by name in the body', async () => {
    const { createClient } = await import('./client')

    await createClient('ci', 'secret').maven.startIndexRebuild({ repositories: ['releases'] })

    expect(http.post.mock.calls[0][1]).toEqual({ repositories: ['releases'] })
  })

  it('starts a verify run with a POST to the verify endpoint', async () => {
    const { createClient } = await import('./client')

    await createClient('ci', 'secret').maven.startIndexVerify()

    expect(http.post.mock.calls[0][0]).toMatch(/\/api\/maven\/search\/index\/verify$/)
  })

  it('cancels a run by its id', async () => {
    const { createClient } = await import('./client')

    await createClient('ci', 'secret').maven.cancelIndexRun(42 as unknown as string)

    expect(http.post.mock.calls[0][0]).toMatch(/\/api\/maven\/search\/index\/runs\/42\/cancel$/)
  })
})

// A refused call as axios hands it over: the status, the server's body (ErrorResponse) and the request url.
const refusal = (status: number, data: unknown, url = 'http://ingot.test/api/identity/me/tokens'): AxiosError =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    config: { url, method: 'post' },
    response: { status, data, headers: {} }
  }) as unknown as AxiosError

const TOTP_START_URL = 'http://ingot.test/api/identity/me/factors/totp'

describe('client second factor error mapping', () => {
  let client: typeof Client

  beforeEach(async () => {
    vi.resetModules()
    client = await import('./client')
  })

  it('maps a 403 step_up_required answer to the step-up problem', () => {
    const error = refusal(403, { status: 403, message: 'step_up_required', problems: [] })

    expect(client.secondFactorProblemOf(error)?.problem, 'a refused sensitive action names the step-up problem').toBe('step_up_required')
  })

  it('lists the second factor methods of a step-up answer in the order the server gave them', () => {
    const error = refusal(403, {
      status: 403,
      message: 'step_up_required',
      problems: [
        { node: 'AUTHENTICATOR', message: 'Confirm with this second factor at /api/identity/me/step-up' },
        { node: 'RECOVERY_CODE', message: 'Confirm with this second factor at /api/identity/me/step-up' },
      ],
    })

    expect(client.secondFactorProblemOf(error)?.methods, 'the dialog offers the methods the user has').toEqual(['AUTHENTICATOR', 'RECOVERY_CODE'])
  })

  it('leaves out a step-up method name the client does not know', () => {
    const error = refusal(403, {
      status: 403,
      message: 'step_up_required',
      problems: [{ node: 'SMS', message: 'unknown' }, { node: 'PASSKEY', message: 'ok' }],
    })

    expect(client.secondFactorProblemOf(error)?.methods, 'only known methods reach the dialog').toEqual(['PASSKEY'])
  })

  it('gives a step-up answer without a method list an empty list of methods', () => {
    const error = refusal(403, { status: 403, message: 'step_up_required' })

    expect(client.secondFactorProblemOf(error)?.methods, 'a missing list is not an error').toEqual([])
  })

  it('gives the step-up message a next step rather than the raw code', () => {
    const error = refusal(403, { status: 403, message: 'step_up_required' })

    const message = client.secondFactorProblemOf(error)?.message ?? ''

    expect(message, 'the user reads a reason and a next step').toMatch(/second factor/i)
    expect(message, 'the raw problem code is not shown to the user').not.toContain('step_up_required')
  })

  it('maps a 403 mfa_enrolment_required answer to the enrolment problem', () => {
    const error = refusal(403, { status: 403, message: 'mfa_enrolment_required' })

    expect(client.secondFactorProblemOf(error)?.problem, 'a restricted session names the enrolment problem').toBe('mfa_enrolment_required')
  })

  it('tells a restricted session to add a second factor on the account page', () => {
    const error = refusal(403, { status: 403, message: 'mfa_enrolment_required' })

    expect(client.secondFactorProblemOf(error)?.message, 'the next step is named').toMatch(/account page/i)
  })

  it('maps the 409 of an authenticator enrolment without a settings key to its problem', () => {
    const reason = 'Authenticator codes need the settings key: set INGOT_SETTINGS_KEY first.'
    const error = refusal(409, { status: 409, message: reason }, TOTP_START_URL)

    expect(client.secondFactorProblemOf(error)?.problem, 'the 409 of the start names its own problem').toBe('authenticator_enrolment_unavailable')
  })

  it('shows the server reason for the 409 of an authenticator enrolment', () => {
    const reason = 'Authenticator codes need the settings key: set INGOT_SETTINGS_KEY first.'
    const error = refusal(409, { status: 409, message: reason }, TOTP_START_URL)

    expect(client.secondFactorProblemOf(error)?.message, 'the reason the server gave is what the dialog shows').toBe(reason)
  })

  it('does not map a 409 of another second factor route', () => {
    const error = refusal(409, { status: 409, message: 'No authenticator is waiting for a code' }, `${TOTP_START_URL}/confirm`)

    expect(client.secondFactorProblemOf(error), 'the confirm answer keeps its own message').toBeNull()
  })

  it('does not map a 403 wrong code answer', () => {
    const error = refusal(403, { status: 403, message: 'The code is not valid' }, `${TOTP_START_URL}/confirm`)

    expect(client.secondFactorProblemOf(error), 'a wrong code is shown with the server message').toBeNull()
  })

  it('does not map a 401 that carries the step-up code', () => {
    const error = refusal(401, { status: 401, message: 'step_up_required' })

    expect(client.secondFactorProblemOf(error), 'only a 403 is a refused sensitive action').toBeNull()
  })

  it('returns null for an error that is not an answer of the server', () => {
    expect(client.secondFactorProblemOf(new Error('Network Error')), 'a network failure has no problem code').toBeNull()
  })
})
