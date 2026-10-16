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
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from './create'
import { legacyLocation } from './legacy'

// Every route renders the same shell in the application. Here the shell is an empty stub,
// because what it shows is tested with the page itself.
const shell = { render: () => null }

// A fresh router per test, on memory history, with no session behind it unless a test
// provides one. The defaults describe an anonymous visitor.
const makeRouter = (options = {}) =>
  createAppRouter({
    history: createMemoryHistory(),
    component: shell,
    legacy: legacyLocation,
    siteTitle: 'Ingot Repository',
    ...options
  })

const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

describe('route to section mapping', () => {
  const cases = [
    ['/', 'overview'],
    ['/browse/releases/gav/0.1.0', 'overview'],
    ['/search', 'search'],
    ['/search?q=commons&origin=mirror', 'search'],
    ['/account', 'account'],
    ['/dashboard', 'dashboard'],
    ['/access/users', 'access'],
    ['/access/users/alice', 'access'],
    ['/access/machines', 'access'],
    ['/access/machines/ci-bot', 'access'],
    ['/access/teams', 'access'],
    ['/access/teams/developers', 'access'],
    ['/console', 'console'],
    ['/maintenance', 'maintenance'],
    ['/settings', 'settings'],
    ['/settings/maven', 'settings'],
    ['/settings/maven/repositories/releases', 'settings'],
  ]

  it.each(cases)('places %s in the %s section', async (url, section) => {
    const router = makeRouter()

    await router.push(url)

    expect(router.currentRoute.value.meta.section, `${url} belongs to ${section}`).toBe(section)
  })

  it('gives a path no route claims the not-found route, which belongs to no section', async () => {
    const router = makeRouter()

    await router.push('/releases/does/not/exist')

    expect(router.currentRoute.value.name).toBe('not-found')
    expect(router.currentRoute.value.meta.section).toBeNull()
  })
})

describe('route names and parameters', () => {
  it('names the user list, the user detail, the machine list and the machine detail', async () => {
    const router = makeRouter()

    await router.push('/access/users')
    expect(router.currentRoute.value.name).toBe('access-users')

    await router.push('/access/users/alice')
    expect(router.currentRoute.value.name).toBe('access-user')
    expect(router.currentRoute.value.params.name).toBe('alice')

    await router.push('/access/machines')
    expect(router.currentRoute.value.name).toBe('access-machines')

    await router.push('/access/machines/ci-bot')
    expect(router.currentRoute.value.name).toBe('access-machine')
    expect(router.currentRoute.value.params.name).toBe('ci-bot')
  })

  it('names the team list and the team detail for the next phase', async () => {
    const router = makeRouter()

    await router.push('/access/teams/developers')

    expect(router.currentRoute.value.name).toBe('access-team')
    expect(router.currentRoute.value.params.name).toBe('developers')
  })

  it('reads the repository path of a browse URL as the qualifier', async () => {
    const router = makeRouter()

    await router.push('/browse/releases/gav/0.1.0')

    expect(router.currentRoute.value.params.qualifier).toEqual(['releases', 'gav', '0.1.0'])
  })

  it('reads the domain, collection and entry of a settings entry', async () => {
    const router = makeRouter()

    await router.push('/settings/maven/repositories/releases')

    expect(router.currentRoute.value.name).toBe('settings-entry')
    expect(router.currentRoute.value.params).toEqual({
      domain: 'maven',
      collection: 'repositories',
      entry: 'releases'
    })
  })

  it('reads the domain alone for the settings of one domain', async () => {
    const router = makeRouter()

    await router.push('/settings/maven')

    expect(router.currentRoute.value.name).toBe('settings')
    expect(router.currentRoute.value.params.domain).toBe('maven')
  })
})

describe('access and the old token routes', () => {
  it('redirects /access to the user list', async () => {
    const router = makeRouter()

    await router.push('/access')

    expect(router.currentRoute.value.fullPath).toBe('/access/users')
  })

  it('redirects /tokens to the machine accounts', async () => {
    const router = makeRouter()

    await router.push('/tokens')

    expect(router.currentRoute.value.fullPath).toBe('/access/machines')
  })

  it('redirects /tokens/new to the machine accounts', async () => {
    const router = makeRouter()

    await router.push('/tokens/new')

    expect(router.currentRoute.value.fullPath, 'new is not a machine name').toBe('/access/machines')
  })

  it('redirects /tokens/:name to the machine of that name', async () => {
    const router = makeRouter()

    await router.push('/tokens/ci-bot')

    expect(router.currentRoute.value.fullPath).toBe('/access/machines/ci-bot')
    expect(router.currentRoute.value.params.name).toBe('ci-bot')
  })

  it('keeps the name encoded when it is redirected', async () => {
    const router = makeRouter()

    await router.push('/tokens/ci%20bot')

    expect(router.currentRoute.value.params.name).toBe('ci bot')
  })

  it('applies the manager guard to the target of a redirect', async () => {
    const notify = vi.fn()
    const router = makeRouter({ isManager: () => false, isLogged: () => true, notify })

    await router.push('/tokens')

    expect(router.currentRoute.value.name, 'a signed in user without the permission is sent away').toBe('overview')
    expect(notify).toHaveBeenCalledTimes(1)
  })
})

describe('manager routes', () => {
  beforeEach(() => {
    document.title = ''
  })

  it('keeps an anonymous visitor on the requested URL', async () => {
    const router = makeRouter({ whenSessionKnown: () => Promise.resolve() })

    await router.push('/access/machines/ci-bot')

    expect(router.currentRoute.value.fullPath, 'the URL the visitor asked for stays in the address bar').toBe('/access/machines/ci-bot')
  })

  it('lets a manager reach the section they asked for', async () => {
    const notify = vi.fn()
    const router = makeRouter({
      isManager: () => true,
      isLogged: () => true,
      notify,
      whenSessionKnown: () => Promise.resolve()
    })

    await router.push('/console')

    expect(router.currentRoute.value.fullPath).toBe('/console')
    expect(notify).not.toHaveBeenCalled()
  })

  it('moves a signed in user without the manager permission to overview and tells them', async () => {
    const notify = vi.fn()
    const router = makeRouter({
      isManager: () => false,
      isLogged: () => true,
      notify,
      whenSessionKnown: () => Promise.resolve()
    })

    await router.push('/dashboard')

    expect(router.currentRoute.value.name).toBe('overview')
    expect(notify).toHaveBeenCalledTimes(1)
  })

  it('does not decide anything before the session is known', async () => {
    const known = deferred()
    const router = makeRouter({
      isManager: () => true,
      isLogged: () => true,
      whenSessionKnown: () => known.promise
    })

    const navigation = router.push('/access/users')
    await Promise.resolve()

    expect(router.currentRoute.value.name, 'the page must not change before the session answers').not.toBe('access-users')

    known.resolve()
    await navigation
    expect(router.currentRoute.value.name).toBe('access-users')
  })

  it('leaves routes that are not for managers alone, even for an anonymous visitor', async () => {
    const router = makeRouter({ whenSessionKnown: () => new Promise(() => {}) })

    await router.push('/browse/releases')

    expect(router.currentRoute.value.name, 'the browser needs no session to open').toBe('browse')
  })
})

describe('legacy hash links', () => {
  it('turns a legacy link into the route that replaces it on the first navigation', async () => {
    const router = makeRouter()

    await router.push('/#/releases/gav/0.1.0?tab=overview')

    expect(router.currentRoute.value.fullPath).toBe('/browse/releases/gav/0.1.0')
  })

  it('turns a legacy section link into its route', async () => {
    const router = makeRouter({ whenSessionKnown: () => Promise.resolve(), isManager: () => true, isLogged: () => true })

    await router.push('/#/?tab=tokens')

    expect(router.currentRoute.value.fullPath, 'the old tokens link ends at the machine accounts').toBe('/access/machines')
  })

  it('does not rewrite a hash on a later navigation', async () => {
    const router = makeRouter()
    await router.push('/')

    await router.push('/#/releases?tab=overview')

    expect(router.currentRoute.value.name).toBe('overview')
  })
})

describe('document title', () => {
  beforeEach(() => {
    document.title = ''
  })

  it('names the browsed repository path in the title', async () => {
    const router = makeRouter()

    await router.push('/browse/releases/gav')

    expect(document.title).toBe('releases/gav · Ingot Repository')
  })

  it('keeps the site title on overview', async () => {
    const router = makeRouter()

    await router.push('/')

    expect(document.title).toBe('Ingot Repository')
  })
})

describe('search route', () => {
  it('opens the search page for an anonymous visitor, who needs no sign in for it', async () => {
    const router = makeRouter()

    await router.push('/search?q=commons')

    expect(router.currentRoute.value.name).toBe('search')
    expect(router.currentRoute.value.meta.manager, 'search is not a manager section').toBeUndefined()
    expect(router.currentRoute.value.query.q, 'the query stays in the address').toBe('commons')
  })
})

describe('account route', () => {
  it('is open to a signed in user who is not a manager', async () => {
    const router = makeRouter({ isLogged: () => true })

    await router.push('/account')

    expect(router.currentRoute.value.name).toBe('account')
    expect(router.currentRoute.value.meta.manager, 'the account page is not a manager section').toBeUndefined()
  })

  it('sends every other route to the account page while a new password is required', async () => {
    const router = makeRouter({ isLogged: () => true, mustChangePassword: () => true })

    await router.push('/browse/releases')

    expect(router.currentRoute.value.name, 'the redirect replaces the requested route').toBe('account')
  })

  it('does not redirect when no new password is required', async () => {
    const router = makeRouter({ isLogged: () => true, mustChangePassword: () => false })

    await router.push('/browse/releases')

    expect(router.currentRoute.value.name).toBe('browse')
  })
})

describe('routes gated by permission nodes', () => {
  const sessionWith = (...nodes) => ({
    isManager: () => true,
    isLogged: () => true,
    can: (node) => nodes.includes(node),
    whenSessionKnown: () => Promise.resolve()
  })

  it('sends a user without ingot.admin.maintenance away from the maintenance page', async () => {
    const router = makeRouter(sessionWith('ingot.admin.audit'))

    await router.push('/maintenance')

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('opens the maintenance page for a user with ingot.admin.maintenance', async () => {
    const router = makeRouter(sessionWith('ingot.admin.maintenance'))

    await router.push('/maintenance')

    expect(router.currentRoute.value.name).toBe('maintenance')
  })

  it('opens the first access tab the user may use when /access is visited', async () => {
    const router = makeRouter(sessionWith('ingot.admin.teams'))

    await router.push('/access')

    expect(router.currentRoute.value.name).toBe('access-teams')
  })

  it('sends a user without the users node away from the user list', async () => {
    const router = makeRouter(sessionWith('ingot.admin.teams'))

    await router.push('/access/users')

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('ends the token redirect at overview for a user without ingot.admin.tokens', async () => {
    const router = makeRouter(sessionWith('ingot.admin.users'))

    await router.push('/tokens')

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('opens the machine accounts for a user with ingot.admin.tokens', async () => {
    const router = makeRouter(sessionWith('ingot.admin.tokens'))

    await router.push('/tokens')

    expect(router.currentRoute.value.name).toBe('access-machines')
  })

  it('keeps the manager rule for a session that gives only a manager flag', async () => {
    const router = makeRouter({
      isManager: () => true,
      isLogged: () => true,
      whenSessionKnown: () => Promise.resolve()
    })

    await router.push('/audit')

    expect(router.currentRoute.value.name).toBe('audit')
  })
})
