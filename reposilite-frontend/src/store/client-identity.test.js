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

vi.mock('axios', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    patch: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ status: 204 })),
    request: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

describe('client identity namespace', () => {
  let axios
  let identity

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default
    axios.get.mockClear()
    axios.post.mockClear()
    axios.delete.mockClear()
    axios.put.mockClear()
    axios.patch.mockClear()
    const { createClient } = await import('./client')
    identity = createClient('', '').identity
  })

  describe('me', () => {
    it('posts the exact password payload to the self service endpoint', () => {
      identity.me.changePassword({ currentPassword: 'old-password-1', newPassword: 'new-password-12' })

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/password$/),
        { currentPassword: 'old-password-1', newPassword: 'new-password-12' },
        expect.anything()
      )
    })

    it('lists the tokens of the signed in principal', () => {
      identity.me.tokens.list()

      expect(axios.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/tokens$/), expect.anything())
    })

    it('creates a token with the body it is given', () => {
      identity.me.tokens.create({ name: 'laptop', expiresAt: null, scopes: null })

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/tokens$/),
        { name: 'laptop', expiresAt: null, scopes: null },
        expect.anything()
      )
    })

    it('rotates a token by its encoded name', () => {
      identity.me.tokens.rotate('my token')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/tokens\/my%20token\/rotate$/),
        {},
        expect.anything()
      )
    })

    it('revokes a token by its encoded name', () => {
      identity.me.tokens.revoke('my token')

      expect(axios.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/tokens\/my%20token$/),
        expect.anything()
      )
    })
  })

  describe('principalTokens', () => {
    it('lists the tokens of the named principal', () => {
      identity.principalTokens('ci-bot').list()

      expect(axios.get).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/ci-bot\/tokens$/),
        expect.anything()
      )
    })

    it('creates and revokes below the principal', () => {
      const api = identity.principalTokens('ci bot')

      api.create({ name: 'deploy' })
      api.revoke('deploy')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/ci%20bot\/tokens$/),
        { name: 'deploy' },
        expect.anything()
      )
      expect(axios.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/ci%20bot\/tokens\/deploy$/),
        expect.anything()
      )
    })

    it('offers no rotate, because the server has no such endpoint for a principal', () => {
      expect(identity.principalTokens('ci-bot').rotate).toBeUndefined()
    })
  })
  describe('principals', () => {
    it('lists the principals of one kind', () => {
      identity.principals.list('USER')

      expect(axios.get).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\?kind=USER$/),
        expect.anything()
      )
    })

    it('reads one principal by its encoded name', () => {
      identity.principals.get('ann marie')

      expect(axios.get).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/ann%20marie$/),
        expect.anything()
      )
    })

    it('creates a principal with the body it is given', () => {
      identity.principals.create({ kind: 'MACHINE', name: 'ci' })

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals$/),
        { kind: 'MACHINE', name: 'ci' },
        expect.anything()
      )
    })

    it('patches a principal and sends the entity tag as If-Match', () => {
      identity.principals.update('alice', { disabled: true }, '"7"')

      expect(axios.patch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/alice$/),
        { disabled: true },
        expect.objectContaining({ headers: expect.objectContaining({ 'If-Match': '"7"' }) })
      )
    })

    it('sends no If-Match header without an entity tag', () => {
      identity.principals.update('alice', { disabled: true }, null)

      expect(axios.patch.mock.calls[0][2].headers).not.toHaveProperty('If-Match')
    })

    it('deletes a principal by its encoded name', () => {
      identity.principals.remove('alice')

      expect(axios.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/alice$/),
        expect.anything()
      )
    })

    it('posts the new password as the exact payload', () => {
      identity.principals.setPassword('alice', 'a-long-password-1')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/alice\/password$/),
        { password: 'a-long-password-1' },
        expect.anything()
      )
    })

    it('grants the instance admin role with a PUT', () => {
      identity.principals.grantInstanceAdmin('alice')

      expect(axios.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/alice\/instance-admin$/),
        {},
        expect.anything()
      )
    })

    it('revokes the instance admin role with a DELETE', () => {
      identity.principals.revokeInstanceAdmin('alice')

      expect(axios.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/principals\/alice\/instance-admin$/),
        expect.anything()
      )
    })
  })
})
