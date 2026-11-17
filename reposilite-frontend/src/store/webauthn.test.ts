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

import { describe, it, expect, vi } from 'vitest'
import {
  base64urlToBuffer,
  bufferToBase64url,
  createPasskey,
  getPasskeyAssertion,
  isWebAuthnSupported,
  WebAuthnUnsupportedError,
  type WebAuthnEnvironment,
} from './webauthn'

// The bytes of a buffer, so that a test can compare fixed values without a typed array in each assertion.
const bytesOf = (buffer: ArrayBuffer): number[] => Array.from(new Uint8Array(buffer))
const buffer = (...values: number[]): ArrayBuffer => new Uint8Array(values).buffer

// A browser with WebAuthn, as far as the helper reads it. The credentials are fakes that answer what the test sets.
const supportedEnvironment = (answer: { get?: unknown; create?: unknown } = {}) => {
  const get = vi.fn<(options: unknown) => Promise<unknown>>(() => Promise.resolve(answer.get ?? null))
  const create = vi.fn<(options: unknown) => Promise<unknown>>(() => Promise.resolve(answer.create ?? null))
  const environment: WebAuthnEnvironment = {
    publicKeyCredential: function PublicKeyCredential() {},
    credentials: { get, create } as unknown as WebAuthnEnvironment['credentials'],
  }
  return { environment, get, create }
}

const UNSUPPORTED: WebAuthnEnvironment = {}

describe('base64url conversion', () => {
  it('encodes the bytes 0xfb 0xff 0xbf as the url alphabet without padding', () => {
    expect(bufferToBase64url(buffer(0xfb, 0xff, 0xbf))).toBe('-_-_')
  })

  it('encodes a single byte without padding characters', () => {
    expect(bufferToBase64url(buffer(0xff))).toBe('_w')
  })

  it('encodes two bytes without padding characters', () => {
    expect(bufferToBase64url(buffer(0x00, 0x01))).toBe('AAE')
  })

  it('encodes a view of a typed array from its own offset and length', () => {
    const backing = new Uint8Array([0x00, 0xfb, 0xff, 0xbf, 0x00])

    expect(bufferToBase64url(backing.subarray(1, 4))).toBe('-_-_')
  })

  it('decodes the url alphabet back to the same bytes', () => {
    expect(bytesOf(base64urlToBuffer('-_-_'))).toEqual([0xfb, 0xff, 0xbf])
  })

  it('decodes text without padding', () => {
    expect(bytesOf(base64urlToBuffer('AAE'))).toEqual([0x00, 0x01])
  })

  it('round trips every byte value through the encoding', () => {
    const every = Array.from({ length: 256 }, (_, value) => value)

    expect(bytesOf(base64urlToBuffer(bufferToBase64url(buffer(...every))))).toEqual(every)
  })

  it('refuses the standard base64 characters, which the url alphabet does not use', () => {
    expect(() => base64urlToBuffer('+/+/')).toThrow(TypeError)
  })

  it('refuses a text whose length cannot be a base64 text', () => {
    expect(() => base64urlToBuffer('A')).toThrow(TypeError)
  })
})

describe('WebAuthn support', () => {
  it('reports no support when the browser has no public key credential class', () => {
    expect(isWebAuthnSupported(UNSUPPORTED)).toBe(false)
  })

  it('reports no support when the credentials container has no create', () => {
    const environment: WebAuthnEnvironment = {
      publicKeyCredential: function PublicKeyCredential() {},
      credentials: { get: vi.fn() } as unknown as WebAuthnEnvironment['credentials'],
    }

    expect(isWebAuthnSupported(environment)).toBe(false)
  })

  it('reports support when the class and both credential calls exist', () => {
    expect(isWebAuthnSupported(supportedEnvironment().environment)).toBe(true)
  })

  it('refuses an assertion in a browser without WebAuthn, saying the feature is not supported', async () => {
    const request = getPasskeyAssertion({ challenge: 'Y2hhbGxlbmdl', rpId: 'ingot.test', allowCredentials: [] }, UNSUPPORTED)

    await expect(request).rejects.toBeInstanceOf(WebAuthnUnsupportedError)
    await expect(request).rejects.toThrow(/not supported/)
  })

  it('refuses a registration in a browser without WebAuthn, saying the feature is not supported', async () => {
    const request = createPasskey({ challenge: 'Y2hhbGxlbmdl', rpId: 'ingot.test', userName: 'alice' }, UNSUPPORTED)

    await expect(request).rejects.toThrow(/not supported/)
  })
})

describe('passkey assertion', () => {
  it('passes the decoded challenge and the allowed credentials to the browser', async () => {
    const { environment, get } = supportedEnvironment({ get: null })

    await getPasskeyAssertion({ challenge: '-_-_', rpId: 'ingot.test', allowCredentials: ['AAE'] }, environment).catch(() => undefined)

    const options = get.mock.calls[0][0] as { publicKey: { challenge: ArrayBuffer; rpId: string; allowCredentials: Array<{ type: string; id: ArrayBuffer }> } }
    expect(bytesOf(options.publicKey.challenge), 'the challenge the browser signs').toEqual([0xfb, 0xff, 0xbf])
    expect(options.publicKey.rpId, 'the relying party identifier').toBe('ingot.test')
    expect(options.publicKey.allowCredentials.map(credential => credential.type), 'every allowed credential is a public key').toEqual(['public-key'])
    expect(bytesOf(options.publicKey.allowCredentials[0].id), 'the id of the allowed credential').toEqual([0x00, 0x01])
  })

  it('answers the four base64url fields of the assertion the server expects', async () => {
    const credential = {
      rawId: buffer(0xfb, 0xff, 0xbf),
      response: {
        clientDataJSON: buffer(0x01),
        authenticatorData: buffer(0x02, 0x03),
        signature: buffer(0x04),
      },
    }
    const { environment } = supportedEnvironment({ get: credential })

    const assertion = await getPasskeyAssertion({ challenge: 'AAE', rpId: 'ingot.test', allowCredentials: [] }, environment)

    expect(assertion).toEqual({
      credentialId: '-_-_',
      clientDataJSON: 'AQ',
      authenticatorData: 'AgM',
      signature: 'BA',
    })
  })

  it('reports a cancelled request when the browser answers without a credential', async () => {
    const { environment } = supportedEnvironment({ get: null })

    await expect(getPasskeyAssertion({ challenge: 'AAE', rpId: 'ingot.test', allowCredentials: [] }, environment)).rejects.toThrow(
      /cancelled/
    )
  })
})

describe('passkey registration', () => {
  it('passes the relying party, the decoded challenge and the user name to the browser', async () => {
    const { environment, create } = supportedEnvironment({ create: null })

    await createPasskey({ challenge: '-_-_', rpId: 'ingot.test', userName: 'alice' }, environment).catch(() => undefined)

    const options = create.mock.calls[0][0] as { publicKey: { challenge: ArrayBuffer; rp: { id: string }; user: { name: string } } }
    expect(bytesOf(options.publicKey.challenge), 'the challenge the browser signs').toEqual([0xfb, 0xff, 0xbf])
    expect(options.publicKey.rp.id, 'the relying party identifier').toBe('ingot.test')
    expect(options.publicKey.user.name, 'the account the passkey belongs to').toBe('alice')
  })

  it('answers the client data and the attestation object in base64url', async () => {
    const credential = {
      response: {
        clientDataJSON: buffer(0x01),
        attestationObject: buffer(0x02, 0x03),
      },
    }
    const { environment } = supportedEnvironment({ create: credential })

    const attestation = await createPasskey({ challenge: 'AAE', rpId: 'ingot.test', userName: 'alice' }, environment)

    expect(attestation).toEqual({ clientDataJSON: 'AQ', attestationObject: 'AgM' })
  })
})
