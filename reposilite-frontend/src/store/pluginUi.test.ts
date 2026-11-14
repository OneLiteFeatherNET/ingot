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
import axios from 'axios'
import { loadPluginUi } from './pluginUi'

// The manifest request is checked at the call it makes; axios itself is never reached.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

const TOKEN_AUTHORIZATION = 'xBasic ZTJlLWFkbWluOnNlY3JldA=='

describe('plugin manifest request', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
  })

  it('sends the access token of a token session with the manifest request', async () => {
    await loadPluginUi({ headers: { Authorization: TOKEN_AUTHORIZATION } })

    const [, config] = vi.mocked(axios.get).mock.calls[0]
    expect(config.headers.Authorization, 'the Authorization header of the manifest request').toBe(TOKEN_AUTHORIZATION)
  })
})
