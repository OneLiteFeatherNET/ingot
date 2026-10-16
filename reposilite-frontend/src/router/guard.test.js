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
import { createManagerGuard, createPasswordChangeGuard, managerAccess, NO_ACCESS_MESSAGE } from './guard'
import { ADMIN_NODES, ACCESS_NODES } from '../store/nodeAccess'

// The guard is handed a session it can read and a notice it can raise. Each test builds its
// own, so nothing is shared between them and no real session or toast is touched.
const managerRoute = { name: 'tokens', meta: { manager: true } }
const publicRoute = { name: 'overview', meta: {} }

const maintenanceRoute = { name: 'maintenance', meta: { manager: true, node: ADMIN_NODES.maintenance } }
const accessRoute = { name: 'access-users', meta: { manager: true, anyNode: ACCESS_NODES } }

// `nodes` is the node list of the session; without it the session is a legacy one and the
// manager flag decides, as the session store does.
const fixture = ({ isManager = false, isLogged = false, nodes, known = Promise.resolve(), notify = vi.fn() } = {}) => {
  const can = nodes === undefined
    ? () => isManager
    : (node) => nodes.some(entry => entry === node || entry === '*' || (entry.endsWith('.*') && node.startsWith(entry.slice(0, -1))))
  const guard = createManagerGuard({
    whenSessionKnown: () => known,
    isManager: () => isManager,
    isLogged: () => isLogged,
    can,
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

describe('createManagerGuard with permission nodes', () => {
  it('sends a user without the node of the route to overview', async () => {
    const { guard } = fixture({ isLogged: true, nodes: [ADMIN_NODES.audit] })

    expect(await guard(maintenanceRoute)).toEqual({ name: 'overview', replace: true })
  })

  it('tells the user that they have no access to that page', async () => {
    const { guard, notify } = fixture({ isLogged: true, nodes: [] })

    await guard(maintenanceRoute)

    expect(notify).toHaveBeenCalledWith(NO_ACCESS_MESSAGE)
    expect(NO_ACCESS_MESSAGE).toBe('You do not have access to that page')
  })

  it('lets a user with the node of the route in', async () => {
    const { guard, notify } = fixture({ isLogged: true, nodes: [ADMIN_NODES.maintenance] })

    expect(await guard(maintenanceRoute)).toBe(true)
    expect(notify).not.toHaveBeenCalled()
  })

  it('lets an instance admin with ingot.admin.* in', async () => {
    const { guard } = fixture({ isLogged: true, nodes: ['ingot.admin.*'] })

    expect(await guard(maintenanceRoute)).toBe(true)
  })

  it('lets a user in who has one of the alternatives of the route', async () => {
    const { guard } = fixture({ isLogged: true, nodes: [ADMIN_NODES.teams] })

    expect(await guard(accessRoute)).toBe(true)
  })

  it('sends a user with no access node away from the access section', async () => {
    const { guard } = fixture({ isLogged: true, nodes: [ADMIN_NODES.audit] })

    expect(await guard(accessRoute)).toEqual({ name: 'overview', replace: true })
  })

  it('keeps an anonymous visitor on the requested URL', async () => {
    const { guard, notify } = fixture({ nodes: [] })

    expect(await guard(maintenanceRoute)).toBe(true)
    expect(notify).not.toHaveBeenCalled()
  })

  it('lets a legacy manager session into the route, as before nodes existed', async () => {
    const { guard } = fixture({ isLogged: true, isManager: true })

    expect(await guard(maintenanceRoute)).toBe(true)
  })

  it('sends a legacy session without the manager permission to overview', async () => {
    const { guard } = fixture({ isLogged: true })

    expect(await guard(maintenanceRoute)).toEqual({ name: 'overview', replace: true })
  })

  it('shows the notice once when it is wired to the shared info toast', async () => {
    vi.resetModules()
    const toast = await import('../helpers/toast')
    const { snackbar } = await import('../store/snackbar')
    const { guard } = fixture({ isLogged: true, nodes: [], notify: toast.createInfoToastOnce })

    await guard(maintenanceRoute)
    await guard(maintenanceRoute)

    expect(snackbar.entries.value.map(entry => entry.message)).toEqual([NO_ACCESS_MESSAGE])
  })
})

describe('createPasswordChangeGuard', () => {
  const guardFor = (mustChange) => createPasswordChangeGuard({ mustChangePassword: () => mustChange })

  it('lets every route through when no new password is required', () => {
    expect(guardFor(false)({ name: 'tokens' })).toBe(true)
  })

  it('sends any other route to the account page while a new password is required', () => {
    expect(guardFor(true)({ name: 'overview' })).toEqual({ name: 'account', replace: true })
  })

  it('sends a manager route to the account page as well', () => {
    expect(guardFor(true)(managerRoute)).toEqual({ name: 'account', replace: true })
  })

  it('lets the account page itself open, or the redirect would never end', () => {
    expect(guardFor(true)({ name: 'account' })).toBe(true)
  })

  it('reads the flag at each navigation', () => {
    let required = true
    const guard = createPasswordChangeGuard({ mustChangePassword: () => required })

    expect(guard({ name: 'overview' })).toEqual({ name: 'account', replace: true })
    required = false
    expect(guard({ name: 'overview' })).toBe(true)
  })
})
