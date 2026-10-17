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

import { describe, it, expect } from 'vitest'
import { setupFailureMessage } from './session'

// The answers of POST /api/auth/setup as the backend sends them (AuthLoginEndpoints.kt). The status
// alone does not tell the cases apart: 403 covers a wrong code and an instance that is already set
// up, and 409 covers a lost race and a taken name. The body message is what tells them apart.
const answer = (status, message, headers = {}) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: { status, message }, headers }
  })

describe('setupFailureMessage', () => {
  it('says the setup code is incorrect when the code does not match', () => {
    expect(setupFailureMessage(answer(403, 'Invalid setup code'))).toBe('Setup code is incorrect')
  })

  it('says Ingot is already set up when a user exists and no code was involved', () => {
    expect(setupFailureMessage(answer(403, 'Setup is not required, a user already exists'))).toBe('Ingot is already set up')
  })

  it('tells the visitor to sign in when another instance created the first administrator', () => {
    expect(
      setupFailureMessage(answer(409, 'The first administrator was created by another instance of this database, sign in instead'))
    ).toBe('Ingot is already set up. Sign in instead.')
  })

  it('says the user name is taken when a 409 names a taken user name', () => {
    expect(setupFailureMessage(answer(409, 'This user name is already taken'))).toBe('That user name is already taken')
  })

  it('never reads a 409 about a taken name as already set up', () => {
    expect(setupFailureMessage(answer(409, 'This user name is already taken'))).not.toBe('Ingot is already set up')
  })

  it('says how long to wait when too many setup codes were tried', () => {
    expect(setupFailureMessage(answer(429, 'Too many failed setup attempts, try again later', { 'retry-after': '600' })))
      .toBe('Too many attempts, try again in 10 minutes')
  })

  it('shows the rule the server names when the password is too short', () => {
    expect(setupFailureMessage(answer(400, 'Password must have at least 12 characters')))
      .toBe('Password must have at least 12 characters')
  })

  it('shows the rule the server names when the user name is invalid', () => {
    expect(setupFailureMessage(answer(400, 'User name must not contain colons, spaces or control characters')))
      .toBe('User name must not contain colons, spaces or control characters')
  })

  it('falls back to the server message for a 409 whose body it does not recognise', () => {
    expect(setupFailureMessage(answer(409, 'Something new happened'))).toBe('Something new happened')
  })
})
