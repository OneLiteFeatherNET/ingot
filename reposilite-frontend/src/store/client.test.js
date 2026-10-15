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

// The real axios is never reached. The module only needs the interceptor registry to exist,
// and the handler under test is called directly with a hand-made error.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    interceptors: { response: { use: vi.fn() } }
  }
}))

const responseError = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} }
  })

describe('client response interceptor', () => {
  let client

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

describe('client repositories call', () => {
  // The module and the axios mock are loaded together, so the client sees the mock the test inspects.
  let http

  beforeEach(async () => {
    vi.resetModules()
    http = (await import('axios')).default
    http.get.mockReset()
    http.get.mockResolvedValue({ data: { repositories: [] } })
  })

  it('requests the overview without a query when no filter is given', async () => {
    const { createClient } = await import('./client')

    await createClient().maven.repositories()

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/repositories$/)
  })

  it('writes the text as q and every tag as its own tag parameter, which the endpoint reads as AND', async () => {
    const { createClient } = await import('./client')

    await createClient().maven.repositories({ q: 'nightly builds', tags: ['team-core', 'ci'] })

    expect(http.get.mock.calls[0][0]).toMatch(/\/api\/maven\/repositories\?q=nightly\+builds&tag=team-core&tag=ci$/)
  })
})
