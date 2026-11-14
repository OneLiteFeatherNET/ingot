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
import type { Mock } from 'vitest'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type * as ClientModule from './client'

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

// The request config as far as these tests read it.
type RequestConfig = { url?: string; withCredentials?: boolean }

type MockedAxios = {
  get: Mock<(url: string, config?: RequestConfig) => Promise<unknown>>
  post: Mock<(url: string, body?: unknown, config?: RequestConfig) => Promise<unknown>>
  request: Mock<(config: RequestConfig) => Promise<unknown>>
  interceptors: { request: { use: Mock }, response: { use: Mock } }
}

// The fields the tests set on a request config. The headers are a plain object or a mock with set().
type RequestInput = { method: string, headers: object, withCredentials?: boolean }

const requestConfig = (config: RequestInput): InternalAxiosRequestConfig =>
  config as unknown as InternalAxiosRequestConfig

const responseError = (status: number): AxiosError =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} }
  }) as unknown as AxiosError

const clearCookies = () => {
  document.cookie.split(';').forEach(part => {
    const name = part.split('=')[0].trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  })
}

describe('cookie mode request interceptor', () => {
  let client: typeof ClientModule

  beforeEach(async () => {
    clearCookies()
    vi.resetModules()
    client = await import('./client')
  })

  afterEach(() => {
    clearCookies()
  })

  it('reads the csrf token from the ingot_csrf cookie', () => {
    expect(client.readCsrfToken('a=1; ingot_csrf=tok%3D1; b=2')).toBe('tok=1')
  })

  it('finds no csrf token when the cookie is absent', () => {
    expect(client.readCsrfToken('a=1; b=2'), 'no cookie means no header').toBeNull()
  })

  it('registers the request interceptor on axios', async () => {
    const axios = (await import('axios')).default as unknown as MockedAxios

    expect(axios.interceptors.request.use, 'the interceptor must be installed at import')
      .toHaveBeenCalledWith(client.handleRequest)
  })

  it('leaves a request alone outside cookie mode', () => {
    document.cookie = 'ingot_csrf=abc'

    const config = client.handleRequest(requestConfig({ method: 'post', headers: {} }))

    expect(config.withCredentials, 'token mode must not send cookies').toBeUndefined()
    expect(config.headers).toEqual({})
  })

  it('sends credentials and the csrf header on a POST in cookie mode', () => {
    document.cookie = 'ingot_csrf=abc'
    client.setCookieMode(true)

    const config = client.handleRequest(requestConfig({ method: 'post', headers: {} }))

    expect(config.withCredentials).toBe(true)
    expect(config.headers['X-CSRF-Token'], 'an unsafe cookie request must echo the csrf cookie').toBe('abc')
  })

  it.each(['put', 'patch', 'delete'])('sends the csrf header on a %s in cookie mode', (method) => {
    document.cookie = 'ingot_csrf=abc'
    client.setCookieMode(true)

    expect(client.handleRequest(requestConfig({ method, headers: {} })).headers['X-CSRF-Token']).toBe('abc')
  })

  it('sends credentials but no csrf header on a GET in cookie mode', () => {
    document.cookie = 'ingot_csrf=abc'
    client.setCookieMode(true)

    const config = client.handleRequest(requestConfig({ method: 'get', headers: {} }))

    expect(config.withCredentials).toBe(true)
    expect(config.headers, 'a safe method needs no csrf header').not.toHaveProperty('X-CSRF-Token')
  })

  it('sends no csrf header when the cookie is not there', () => {
    client.setCookieMode(true)

    expect(client.handleRequest(requestConfig({ method: 'post', headers: {} })).headers).not.toHaveProperty('X-CSRF-Token')
  })

  it('treats a request that asks for credentials like a cookie mode request', () => {
    document.cookie = 'ingot_csrf=abc'

    const config = client.handleRequest(requestConfig({ method: 'post', headers: {}, withCredentials: true }))

    expect(config.headers['X-CSRF-Token']).toBe('abc')
  })

  it('sets the header through AxiosHeaders when the config carries one', () => {
    document.cookie = 'ingot_csrf=abc'
    client.setCookieMode(true)
    const headers = { set: vi.fn() }

    client.handleRequest(requestConfig({ method: 'post', headers }))

    expect(headers.set).toHaveBeenCalledWith('X-CSRF-Token', 'abc')
  })
})

describe('cookie mode refresh on 401', () => {
  let axios: MockedAxios
  let client: typeof ClientModule
  let handler: Mock<(error: unknown) => void>

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default as unknown as MockedAxios
    axios.post.mockReset()
    axios.request.mockReset()
    client = await import('./client')
    handler = vi.fn<(error: unknown) => void>()
    client.setUnauthorizedHandler(handler)
    client.setCookieMode(true)
  })

  const rejected = (url = '/api/maven/details/', config: Record<string, unknown> = {}) =>
    Object.assign(responseError(401), { config: { url, method: 'get', ...config } })

  it('refreshes once and retries the original request once', async () => {
    axios.post.mockResolvedValue({ data: {} })
    axios.request.mockResolvedValue({ data: 'ok' })

    const result = await client.handleResponseError(rejected())

    expect(axios.post.mock.calls.map(call => call[0]), 'one refresh call')
      .toEqual([expect.stringMatching(/\/api\/auth\/refresh$/)])
    expect(axios.request).toHaveBeenCalledTimes(1)
    expect(axios.request.mock.calls[0][0].url).toBe('/api/maven/details/')
    expect(result).toEqual({ data: 'ok' })
    expect(handler, 'a recovered session is not an expiry').not.toHaveBeenCalled()
  })

  it('does not refresh again when the retried request is rejected too', async () => {
    axios.post.mockResolvedValue({ data: {} })
    axios.request.mockResolvedValue({})
    await client.handleResponseError(rejected())
    const retried = axios.request.mock.calls[0][0]
    axios.post.mockClear()

    await client.handleResponseError(Object.assign(responseError(401), { config: retried })).catch(() => {})

    expect(axios.post, 'a second 401 on the same request must not loop').not.toHaveBeenCalled()
  })

  it('keeps the session when the retried request is rejected, because the refresh just succeeded', async () => {
    axios.post.mockResolvedValue({ data: {} })
    axios.request.mockResolvedValue({})
    await client.handleResponseError(rejected())
    const retried = axios.request.mock.calls[0][0]

    await client.handleResponseError(Object.assign(responseError(401), { config: retried })).catch(() => {})

    expect(handler, 'a 401 on a listing or content request is the server refusing that request, not the session').not.toHaveBeenCalled()
  })

  it('runs the unauthorized handler and rejects when the refresh fails', async () => {
    axios.post.mockRejectedValue(responseError(401))
    const error = rejected()

    await expect(client.handleResponseError(error)).rejects.toBe(error)

    expect(handler).toHaveBeenCalledWith(error)
    expect(axios.request, 'nothing is retried without a session').not.toHaveBeenCalled()
  })

  it('shares one refresh between concurrent rejected requests', async () => {
    axios.post.mockResolvedValue({ data: {} })
    axios.request.mockResolvedValue({})

    await Promise.all([rejected('/a'), rejected('/b'), rejected('/c')].map(error => client.handleResponseError(error)))

    expect(axios.post, 'a burst of 401 answers needs a single refresh').toHaveBeenCalledTimes(1)
    expect(axios.request).toHaveBeenCalledTimes(3)
  })

  it('does not refresh when the rejected request is the login itself', async () => {
    const error = rejected('/api/auth/login', { method: 'post' })

    await expect(client.handleResponseError(error)).rejects.toBe(error)

    expect(axios.post, 'a wrong password is not an expired session').not.toHaveBeenCalled()
    expect(handler).not.toHaveBeenCalled()
  })

  it('does not refresh when the refresh call itself is rejected', async () => {
    const error = rejected('/api/auth/refresh', { method: 'post' })

    await expect(client.handleResponseError(error)).rejects.toBe(error)

    expect(axios.post).not.toHaveBeenCalled()
  })

  it('does not refresh outside cookie mode', async () => {
    client.setCookieMode(false)

    await client.handleResponseError(rejected()).catch(() => {})

    expect(axios.post).not.toHaveBeenCalled()
    expect(handler, 'token mode keeps the plain unauthorized behaviour').toHaveBeenCalledTimes(1)
  })
})

describe('auth calls', () => {
  let axios: MockedAxios
  let client: typeof ClientModule

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default as unknown as MockedAxios
    axios.get.mockReset()
    axios.post.mockReset()
    client = await import('./client')
  })

  it('posts the local provider with the username and password to the login endpoint', async () => {
    await client.createClient('', '').auth.login({ username: 'alice', password: 'correct horse' })

    const [url, body, config] = axios.post.mock.calls[0]
    expect(url).toMatch(/\/api\/auth\/login$/)
    expect(body).toEqual({ provider: 'local', username: 'alice', password: 'correct horse' })
    expect(config?.withCredentials).toBe(true)
  })

  it('posts the setup request without an empty display name', async () => {
    await client.createClient('', '').auth.setup({ setupCode: 'abc', username: 'root', password: 'long enough pass', displayName: '' })

    expect(axios.post.mock.calls[0][0]).toMatch(/\/api\/auth\/setup$/)
    expect(axios.post.mock.calls[0][1]).toEqual({ setupCode: 'abc', username: 'root', password: 'long enough pass' })
  })

  it('asks for the providers with credentials', async () => {
    await client.createClient('', '').auth.providers()

    expect(axios.get.mock.calls[0][0]).toMatch(/\/api\/auth\/providers$/)
    expect(axios.get.mock.calls[0][1]?.withCredentials).toBe(true)
  })
})
