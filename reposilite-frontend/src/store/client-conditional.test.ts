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
import type { AxiosError, AxiosResponse } from 'axios'

// The HTTP layer is replaced, so each test reads the exact headers a request would have carried.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

import axios from 'axios'
import { createClient, etagOf, isPreconditionFailed } from './client'

// The sent write as far as these tests read it: the url, the body and the request config.
type SentConfig = { headers: Record<string, string> }
type Sent = Mock<(url: string, body?: unknown, config?: SentConfig) => Promise<unknown>>

// The write methods of the mocked axios default export that these tests inspect.
type MockedAxios = { put: Sent; patch: Sent }

const http = axios as unknown as MockedAxios
const client = createClient(undefined, undefined)

const headersOf = (mock: Sent): Record<string, string> => {
  const last = mock.mock.calls.at(-1)
  if (!last) throw new Error('no request was sent')
  if (!last[2]) throw new Error('the request carried no config')
  return last[2].headers
}

describe('conditional writes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends the settings tag as If-Match with the write', async () => {
    await client.settings.update('maven', {}, '"maven-1"')

    expect(headersOf(http.put)['If-Match']).toBe('"maven-1"')
  })

  it('sends no If-Match with a settings write when no tag is known, as before', async () => {
    await client.settings.update('maven', {})

    expect(headersOf(http.put)).not.toHaveProperty('If-Match')
  })

  it('sends the token tag as If-Match with a metadata update', async () => {
    await client.tokens.update('ci', {}, '"ci-1"')

    expect(headersOf(http.patch)['If-Match']).toBe('"ci-1"')
  })

  it('sends no If-Match with a token update when no tag is known, as before', async () => {
    await client.tokens.update('ci', {})

    expect(headersOf(http.patch)).not.toHaveProperty('If-Match')
  })

  it('sends the token tag as If-Match with a create-or-replace', async () => {
    await client.tokens.createOrUpdate('ci', {}, '"ci-1"')

    expect(headersOf(http.put)['If-Match']).toBe('"ci-1"')
  })
})

describe('response tags', () => {
  it('reads the entity tag of a response', () => {
    expect(etagOf({ headers: { etag: '"abc"' } } as unknown as AxiosResponse)).toBe('"abc"')
  })

  it('reports no tag when the response carries none', () => {
    expect(etagOf({ headers: {} } as unknown as AxiosResponse)).toBeNull()
    expect(etagOf(undefined)).toBeNull()
  })

  it('recognises a 412 and nothing else as a stale write', () => {
    expect(isPreconditionFailed({ response: { status: 412 } } as unknown as AxiosError)).toBe(true)
    expect(isPreconditionFailed({ response: { status: 409 } } as unknown as AxiosError)).toBe(false)
    expect(isPreconditionFailed(new Error('network down') as unknown as AxiosError)).toBe(false)
  })
})
