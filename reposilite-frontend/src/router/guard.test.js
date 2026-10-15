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
import { createManagerGuard, managerAccess } from './guard'

// The guard is handed a session it can read and a notice it can raise. Each test builds its
// own, so nothing is shared between them and no real session or toast is touched.
const managerRoute = { name: 'tokens', meta: { manager: true } }
const publicRoute = { name: 'overview', meta: {} }

const fixture = ({ isManager = false, isLogged = false, known = Promise.resolve() } = {}) => {
  const notify = vi.fn()
  const guard = createManagerGuard({
    whenSessionKnown: () => known,
    isManager: () => isManager,
    isLogged: () => isLogged,
    notify
  })
  return { guard, notify }
}

describe('managerAccess', () => {
  it('grants a manager access', () => {
    expect(managerAccess({ isManager: true, isLogged: true })).toBe('granted')
  })

  it('sends a signed in user without the manager permission back to overview', () => {
    expect(managerAccess({ isManager: false, isLogged: true })).toBe('denied')
  })

  it('asks an anonymous visitor to sign in', () => {
    expect(managerAccess({ isManager: false, isLogged: false })).toBe('sign-in')
  })
})

describe('createManagerGuard', () => {
  it('lets every visitor reach a route that is not for managers', async () => {
    const { guard, notify } = fixture()

    expect(await guard(publicRoute), 'the overview needs no session at all').toBe(true)
    expect(notify).not.toHaveBeenCalled()
  })

  it('lets a manager into a manager route without a notice', async () => {
    const { guard, notify } = fixture({ isManager: true, isLogged: true })

    expect(await guard(managerRoute)).toBe(true)
    expect(notify).not.toHaveBeenCalled()
  })

  it('keeps an anonymous visitor on the requested URL, for the page to ask them to sign in', async () => {
    const { guard, notify } = fixture()

    expect(await guard(managerRoute)).toBe(true)
    expect(notify, 'the prompt is shown by the page, not announced here').not.toHaveBeenCalled()
  })

  it('sends a signed in user who is not a manager to overview and tells them why', async () => {
    const { guard, notify } = fixture({ isLogged: true })

    expect(await guard(managerRoute)).toEqual({ name: 'overview', replace: true })
    expect(notify).toHaveBeenCalledTimes(1)
  })

  it('waits for the session to be known before it decides', async () => {
    let finish
    const known = new Promise(resolve => { finish = resolve })
    const { guard, notify } = fixture({ isManager: true, isLogged: true, known })

    const decision = guard(managerRoute)
    let settled = false
    decision.then(() => { settled = true })
    await Promise.resolve()

    expect(settled, 'no redirect may be decided while the session is still unknown').toBe(false)
    finish()
    expect(await decision).toBe(true)
    expect(notify).not.toHaveBeenCalled()
  })
})
