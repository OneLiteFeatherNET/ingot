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
    ['/dashboard', 'dashboard'],
    ['/tokens', 'tokens'],
    ['/tokens/ci-bot', 'tokens'],
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
  it('reads the reserved token name new as the create form, not as a token', async () => {
    const router = makeRouter()

    await router.push('/tokens/new')

    expect(router.currentRoute.value.name).toBe('token')
    expect(router.currentRoute.value.params.name).toBe('new')
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

describe('manager routes', () => {
  beforeEach(() => {
    document.title = ''
  })

  it('keeps an anonymous visitor on the requested URL', async () => {
    const router = makeRouter({ whenSessionKnown: () => Promise.resolve() })

    await router.push('/tokens/ci-bot')

    expect(router.currentRoute.value.fullPath, 'the URL the visitor asked for stays in the address bar').toBe('/tokens/ci-bot')
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

    const navigation = router.push('/tokens')
    await Promise.resolve()

    expect(router.currentRoute.value.name, 'the page must not change before the session answers').not.toBe('tokens')

    known.resolve()
    await navigation
    expect(router.currentRoute.value.name).toBe('tokens')
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

    expect(router.currentRoute.value.fullPath).toBe('/tokens')
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
