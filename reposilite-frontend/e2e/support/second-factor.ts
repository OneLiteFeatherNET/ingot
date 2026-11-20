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

import { createHmac, randomBytes } from 'node:crypto'
import { createPrincipal, siteUrl } from './api'

/*
 * Second factors for the browser suite: a password user of its own for each test, an authenticator enrolled through
 * the public REST API, and the codes an authenticator app would show. The server checks codes against its own clock,
 * so the codes here are computed from the wall clock as an app does; a code is never used twice, because the server
 * refuses a step it has already accepted.
 */

const STEP_SECONDS = 30
const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'

const tokenAuthorization = (): string =>
  `Basic ${Buffer.from(`${process.env.E2E_TOKEN_NAME}:${process.env.E2E_TOKEN_SECRET}`).toString('base64')}`

const decodeBase32 = (text: string): Buffer => {
  let buffer = 0
  let bits = 0
  const bytes: number[] = []
  for (const character of text.replace(/=+$/, '').toUpperCase()) {
    buffer = (buffer << 5) | BASE32.indexOf(character)
    bits += 5
    if (bits >= 8) {
      bytes.push((buffer >>> (bits - 8)) & 0xff)
      bits -= 8
    }
  }
  return Buffer.from(bytes)
}

// RFC 6238 with HMAC-SHA1, six digits and a thirty second step, as TotpAlgorithm.kt computes it.
const totpCode = (secret: string, step: number): string => {
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(step))
  const hash = createHmac('sha1', decodeBase32(secret)).update(counter).digest()
  const offset = hash[hash.length - 1] & 0x0f
  const binary = hash.readUInt32BE(offset) & 0x7fffffff
  return String(binary % 1000000).padStart(6, '0')
}

const currentStep = (): number => Math.floor(Date.now() / 1000 / STEP_SECONDS)

// The base32 secret in the otpauth address the server hands out once.
const secretOf = (otpauthUri: string): string => {
  const secret = new URL(otpauthUri).searchParams.get('secret')
  if (!secret) {
    throw new Error(`the address ${otpauthUri} names no secret`)
  }
  return secret
}

const password = (): string => randomBytes(18).toString('base64url')

const expectOk = async (response: Response, action: string): Promise<Response> => {
  if (!response.ok) {
    throw new Error(`${action} answered ${response.status}: ${await response.text()}`)
  }
  return response
}

/*
 * A browser session of one user, held outside the browser: the cookies of the login answer, and the csrf cookie
 * repeated in its header as the dashboard does it.
 */
type SessionPost = (path: string, body: unknown) => Promise<Response>

const signInOverApi = async (username: string, secret: string): Promise<SessionPost> => {
  const login = await expectOk(await fetch(siteUrl('/api/auth/login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider: 'local', username, password: secret }),
  }), `signing ${username} in`)
  const cookies = login.headers.getSetCookie().map(cookie => cookie.split(';')[0])
  const csrf = cookies.find(cookie => cookie.startsWith('ingot_csrf='))?.slice('ingot_csrf='.length) ?? ''
  return (path: string, body: unknown) => fetch(siteUrl(path), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: cookies.join('; '), 'X-CSRF-Token': csrf },
    body: JSON.stringify(body),
  })
}

/*
 * A user with a password of its own. An administrator's reset makes the password temporary, so the user replaces it
 * once, here, and the browser sign-in of the test is an ordinary one.
 */
const createPasswordUser = async (username: string): Promise<string> => {
  await createPrincipal({ kind: 'USER', name: username })
  const temporary = password()
  await expectOk(await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(username)}/password`), {
    method: 'POST',
    headers: { Authorization: tokenAuthorization(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ newPassword: temporary }),
  }), `setting the password of ${username}`)
  const lasting = password()
  const post = await signInOverApi(username, temporary)
  await expectOk(await post('/api/identity/me/password', { currentPassword: temporary, newPassword: lasting }), `changing the password of ${username}`)
  return lasting
}

/*
 * Enrols an authenticator for the user and returns its secret, the step of the code that confirmed it, and the
 * recovery codes the first factor brings.
 */
interface EnrolledAuthenticator {
  secret: string
  step: number
  recoveryCodes: string[]
}

const enrolAuthenticator = async (username: string, secret: string): Promise<EnrolledAuthenticator> => {
  const post = await signInOverApi(username, secret)
  const started = await expectOk(await post('/api/identity/me/factors/totp', {}), `starting the enrolment of ${username}`)
  const { otpauthUri } = await started.json() as { otpauthUri: string }
  const authenticator = secretOf(otpauthUri)
  const step = currentStep()
  const confirmed = await expectOk(
    await post('/api/identity/me/factors/totp/confirm', { code: totpCode(authenticator, step) }),
    `confirming the enrolment of ${username}`,
  )
  const { recoveryCodes } = await confirmed.json() as { recoveryCodes: string[] }
  return { secret: authenticator, step, recoveryCodes }
}

/*
 * The code of the step after [usedStep]. The server accepts one step either side of its own, and only a step later
 * than the last one it accepted, so this code is good for the next minute after the used one.
 */
const nextCode = (secret: string, usedStep: number): string => totpCode(secret, usedStep + 1)

export { createPasswordUser, enrolAuthenticator, nextCode, secretOf, totpCode, currentStep }
