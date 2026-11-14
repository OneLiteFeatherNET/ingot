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
import { createClient } from './client'

// The sign in call is checked at the request it makes; axios itself is never reached.
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

describe('auth login request', () => {
  beforeEach(() => {
    axios.post.mockReset().mockResolvedValue({ data: {} })
  })

  it('posts the id of the chosen provider with the credentials', async () => {
    await createClient('', '').auth.login({ provider: 'ldap', username: 'ldapuser', password: 'ldap-password-12' })

    expect(axios.post).toHaveBeenCalledWith(
      expect.stringContaining('/api/auth/login'),
      { provider: 'ldap', username: 'ldapuser', password: 'ldap-password-12' },
      expect.objectContaining({ withCredentials: true })
    )
  })

  it('posts the Ingot account provider when no provider is named', async () => {
    await createClient('', '').auth.login({ username: 'alice', password: 'correct horse battery' })

    expect(axios.post.mock.calls[0][1]).toEqual({ provider: 'local', username: 'alice', password: 'correct horse battery' })
  })
})
