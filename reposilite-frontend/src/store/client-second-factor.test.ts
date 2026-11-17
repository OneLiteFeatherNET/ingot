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
import type { createClient } from './client'

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

type MockedAxios = Record<'get' | 'post' | 'put' | 'patch' | 'delete', Mock>

// A passkey assertion as the browser produced it, in base64url. The values are fixed, not random.
const ASSERTION = {
  challengeId: 'ceremony-1',
  credentialId: 'Y3JlZA',
  clientDataJSON: 'Y2xpZW50',
  authenticatorData: 'YXV0aA',
  signature: 'c2ln'
}

describe('client second factor calls', () => {
  let axios: MockedAxios
  let client: ReturnType<typeof createClient>

  beforeEach(async () => {
    vi.resetModules()
    axios = (await import('axios')).default as unknown as MockedAxios
    axios.get.mockClear()
    axios.post.mockClear()
    axios.delete.mockClear()
    axios.patch.mockClear()
    const { createClient } = await import('./client')
    client = createClient('', '')
  })

  describe('sign in second step', () => {
    it('posts the authenticator or recovery code to the verify route with the challenge cookie', () => {
      void client.auth.verifySecondFactor('123456')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/auth\/mfa\/verify$/),
        { code: '123456' },
        expect.objectContaining({ withCredentials: true })
      )
    })

    it('posts a passkey assertion inside the passkey field of the verify route', () => {
      void client.auth.verifyPasskey(ASSERTION)

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/auth\/mfa\/verify$/),
        { passkey: ASSERTION },
        expect.objectContaining({ withCredentials: true })
      )
    })

    it('starts the passkey step of a pending sign in without a body', () => {
      void client.auth.passkeyChallenge()

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/auth\/mfa\/passkey\/options$/),
        {},
        expect.objectContaining({ withCredentials: true })
      )
    })

    it('asks for the passkey options of a user for a first factor sign in', () => {
      void client.auth.firstFactorPasskeyOptions('alice')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/auth\/passkey\/options$/),
        { username: 'alice' },
        expect.objectContaining({ withCredentials: true })
      )
    })

    it('completes a first factor passkey sign in with the flat assertion body', () => {
      void client.auth.firstFactorPasskeyLogin(ASSERTION)

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/auth\/passkey\/login$/),
        ASSERTION,
        expect.objectContaining({ withCredentials: true })
      )
    })

    it('passes a wrong code answer of the verify route on without ending the session', async () => {
      const client = await import('./client')
      const unauthorized = vi.fn()
      client.setUnauthorizedHandler(unauthorized)
      const wrongCode = Object.assign(new Error('Request failed with status code 401'), {
        response: { status: 401, data: { status: 401, message: 'Invalid code' }, headers: {} },
        config: { url: 'http://ingot.test/api/auth/mfa/verify', method: 'post' },
      }) as unknown as AxiosError

      await expect(client.handleResponseError(wrongCode)).rejects.toBe(wrongCode)
      expect(unauthorized, 'a sign in that is still pending must not end a session that does not exist').not.toHaveBeenCalled()
    })
  })

  describe('own second factors', () => {
    it('reads the security status of the signed in user', () => {
      void client.identity.me.security()

      expect(axios.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/security$/), expect.anything())
    })

    it('lists the own factors', () => {
      void client.identity.me.factors.list()

      expect(axios.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/factors$/), expect.anything())
    })

    it('starts an authenticator enrolment with an empty body when no code is given', () => {
      void client.identity.me.factors.startAuthenticator()

      expect(axios.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/factors\/totp$/), {}, expect.anything())
    })

    it('starts an authenticator enrolment with the current code of an existing factor', () => {
      void client.identity.me.factors.startAuthenticator('654321')

      expect(axios.post).toHaveBeenCalledWith(expect.stringMatching(/\/factors\/totp$/), { code: '654321' }, expect.anything())
    })

    it('confirms an authenticator enrolment with the code the app shows', () => {
      void client.identity.me.factors.confirmAuthenticator('123456')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/factors\/totp\/confirm$/),
        { code: '123456' },
        expect.anything()
      )
    })

    it('renames a factor with its id in the path', () => {
      void client.identity.me.factors.rename(7, 'Work phone')

      expect(axios.patch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/factors\/7$/),
        { name: 'Work phone' },
        expect.anything()
      )
    })

    it('removes a factor with the code in the request body', () => {
      void client.identity.me.factors.remove(7, '123456')

      expect(axios.delete).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/factors\/7$/),
        expect.objectContaining({ data: { code: '123456' } })
      )
    })

    it('starts a passkey registration', () => {
      void client.identity.me.factors.startPasskey()

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/factors\/passkey\/options$/),
        {},
        expect.anything()
      )
    })

    it('completes a passkey registration with the attestation of the browser', () => {
      const attestation = { challengeId: 'ceremony-2', clientDataJSON: 'Y2xpZW50', attestationObject: 'YXR0' }

      void client.identity.me.factors.registerPasskey(attestation)

      expect(axios.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/factors\/passkey$/), attestation, expect.anything())
    })

    it('regenerates the recovery codes with a current code', () => {
      void client.identity.me.recoveryCodes.regenerate('123456')

      expect(axios.post).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/identity\/me\/recovery-codes$/),
        { code: '123456' },
        expect.anything()
      )
    })

    it('confirms a step-up with a code of a second factor', () => {
      void client.identity.me.stepUp('123456')

      expect(axios.post).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/me\/step-up$/), { code: '123456' }, expect.anything())
    })
  })

  describe('factors of another principal, for an instance administrator', () => {
    it('lists the factors of a principal with the name encoded in the path', () => {
      void client.identity.principals.factors('ada lovelace').list()

      expect(axios.get).toHaveBeenCalledWith(
        expect.stringContaining('/api/identity/principals/ada%20lovelace/factors'),
        expect.anything()
      )
    })

    it('resets all factors of a principal', () => {
      void client.identity.principals.factors('alice').resetAll()

      expect(axios.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/principals\/alice\/factors$/), expect.anything())
    })

    it('removes one factor of a principal by id', () => {
      void client.identity.principals.factors('alice').remove(7)

      expect(axios.delete).toHaveBeenCalledWith(expect.stringMatching(/\/api\/identity\/principals\/alice\/factors\/7$/), expect.anything())
    })
  })
})
