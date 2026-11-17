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

/*
 * The WebAuthn ceremonies of the dashboard. The server sends and reads every binary field in base64url, and the browser
 * takes and gives ArrayBuffers, so this module converts between the two and calls navigator.credentials. Every ceremony
 * goes through here, so the encoding is written once. The browser is passed in as an environment, so the tests run the
 * same code against fakes.
 */

// The relying party of a ceremony is the host the server names; the user name is the account the passkey belongs to.
const RELYING_PARTY_NAME = 'Ingot'
const CEREMONY_TIMEOUT_MS = 120_000

// Algorithms the server verifies: ES256 first, RS256 as the fallback that some platform authenticators offer.
const PUBLIC_KEY_ALGORITHMS = [-7, -257]

export class WebAuthnUnsupportedError extends Error {
  constructor() {
    super('Passkeys are not supported in this browser')
    this.name = 'WebAuthnUnsupportedError'
  }
}

export class WebAuthnCancelledError extends Error {
  constructor() {
    super('The passkey request was cancelled')
    this.name = 'WebAuthnCancelledError'
  }
}

// What the helper reads of the browser. The class is the feature test; the container runs the ceremonies.
export interface WebAuthnEnvironment {
  publicKeyCredential?: unknown
  credentials?: {
    get: (options: CredentialRequestOptions) => Promise<Credential | null>
    create: (options: CredentialCreationOptions) => Promise<Credential | null>
  }
}

// The fields of an assertion the server reads, in base64url. The caller adds the challenge id it was given.
export interface PasskeyAssertionFields {
  credentialId: string
  clientDataJSON: string
  authenticatorData: string
  signature: string
}

// The fields of a registration the server reads, in base64url. The caller adds the challenge id it was given.
export interface PasskeyAttestationFields {
  clientDataJSON: string
  attestationObject: string
}

// The options the server returns for an assertion: the challenge to sign, the relying party and the passkeys that may answer.
export interface AssertionOptions {
  challenge: string
  rpId: string
  allowCredentials: string[]
}

// The options the server returns for a registration. The user handle is the account's opaque id, which the passkey must store
// so that a usernameless sign in can find the account again; the user name is only shown to the person.
export interface RegistrationOptions {
  challenge: string
  rpId: string
  userName: string
  userHandle: string
}

export const browserEnvironment = (): WebAuthnEnvironment => ({
  publicKeyCredential: typeof window === 'undefined' ? undefined : (window as { PublicKeyCredential?: unknown }).PublicKeyCredential,
  credentials: typeof navigator === 'undefined' ? undefined : navigator.credentials,
})

// True when the browser has the class and both calls. A browser without them hides the passkey actions.
export const isWebAuthnSupported = (environment: WebAuthnEnvironment = browserEnvironment()): boolean =>
  environment.publicKeyCredential !== undefined
  && environment.publicKeyCredential !== null
  && typeof environment.credentials?.get === 'function'
  && typeof environment.credentials?.create === 'function'

const BASE64URL = /^[A-Za-z0-9_-]*$/

// The bytes of a buffer or of a view of one, copied out so that the caller's bytes are never shared.
const bytesOf = (value: ArrayBuffer | ArrayBufferView): Uint8Array => {
  if (ArrayBuffer.isView(value)) {
    return new Uint8Array(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength))
  }
  return new Uint8Array(value.slice(0))
}

// base64url without padding, the form the server writes and reads.
export const bufferToBase64url = (value: ArrayBuffer | ArrayBufferView): string => {
  const bytes = bytesOf(value)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

// The bytes of a base64url text. Padding is optional. A character outside the url alphabet is refused as a TypeError.
export const base64urlToBuffer = (text: string): ArrayBuffer => {
  if (!BASE64URL.test(text)) {
    throw new TypeError('The value is not base64url')
  }
  if (text.length % 4 === 1) {
    throw new TypeError('The value is not base64url')
  }
  const padded = text.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(text.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes.buffer
}

const requireSupport = (environment: WebAuthnEnvironment): NonNullable<WebAuthnEnvironment['credentials']> => {
  if (!isWebAuthnSupported(environment) || !environment.credentials) {
    throw new WebAuthnUnsupportedError()
  }
  return environment.credentials
}

// Runs the sign in or step-up ceremony and answers the fields the server reads. A dismissed prompt rejects with a cancellation.
export const getPasskeyAssertion = async (
  options: AssertionOptions,
  environment: WebAuthnEnvironment = browserEnvironment(),
): Promise<PasskeyAssertionFields> => {
  const credentials = requireSupport(environment)
  const credential = await credentials.get({
    publicKey: {
      challenge: base64urlToBuffer(options.challenge),
      rpId: options.rpId,
      allowCredentials: options.allowCredentials.map(id => ({ type: 'public-key', id: base64urlToBuffer(id) })),
      userVerification: 'preferred',
      timeout: CEREMONY_TIMEOUT_MS,
    },
  })
  if (!credential) {
    throw new WebAuthnCancelledError()
  }
  const publicKey = credential as PublicKeyCredential
  const response = publicKey.response as AuthenticatorAssertionResponse
  return {
    credentialId: bufferToBase64url(publicKey.rawId),
    clientDataJSON: bufferToBase64url(response.clientDataJSON),
    authenticatorData: bufferToBase64url(response.authenticatorData),
    signature: bufferToBase64url(response.signature),
  }
}

// Runs the passkey registration ceremony and answers the fields the server reads.
export const createPasskey = async (
  options: RegistrationOptions,
  environment: WebAuthnEnvironment = browserEnvironment(),
): Promise<PasskeyAttestationFields> => {
  const credentials = requireSupport(environment)
  const credential = await credentials.create({
    publicKey: {
      challenge: base64urlToBuffer(options.challenge),
      rp: { id: options.rpId, name: RELYING_PARTY_NAME },
      user: {
        id: base64urlToBuffer(options.userHandle),
        name: options.userName,
        displayName: options.userName,
      },
      pubKeyCredParams: PUBLIC_KEY_ALGORITHMS.map(alg => ({ type: 'public-key', alg })),
      authenticatorSelection: { residentKey: 'preferred', userVerification: 'preferred' },
      attestation: 'none',
      timeout: CEREMONY_TIMEOUT_MS,
    },
  })
  if (!credential) {
    throw new WebAuthnCancelledError()
  }
  const response = (credential as PublicKeyCredential).response as AuthenticatorAttestationResponse
  return {
    clientDataJSON: bufferToBase64url(response.clientDataJSON),
    attestationObject: bufferToBase64url(response.attestationObject),
  }
}
