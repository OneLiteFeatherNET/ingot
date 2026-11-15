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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import RepositoriesView from './RepositoriesView.vue'
import RepositoryDetail from './RepositoryDetail.vue'
import { loadedStore, repositorySchema, repositoriesServer } from './fixtures/repositories'

// The session is an administrator without a live client, so the wizard and the access card stay off the network.
vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { value: null }, isManager: { value: true } })
}))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))
vi.mock('../../store/placeholders', () => ({
  default: () => ({ basePath: '/', productionUrl: 'https://repo.example.net/' }),
  frontendSecretsHidden: () => false
}))

// jsdom has no <dialog>; the wizard and the one-of renderer use it.
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const routesOf = () => [
  { name: 'settings-repositories', path: '/settings/repositories', component: RepositoriesView },
  { name: 'settings-repository', path: '/settings/repositories/:id', component: RepositoryDetail, props: true }
]

const mountAt = async (address) => {
  const router = createRouter({ history: createMemoryHistory(), routes: routesOf() })
  await router.push(address)
  const store = await loadedStore({ server: repositoriesServer(), schema: repositorySchema({ versionPolicy: true, readOnly: true }) })
  wrapper = mount({ render: () => h(RouterView) }, {
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store }
}

// The controls of the page as a screen reader or a keyboard user meets them: the page itself and any dialog open on it.
const controls = () => Array.from(document.querySelectorAll('button, a[href], input, select, textarea'))
  .filter(control => !control.hasAttribute('disabled') && control.getAttribute('aria-hidden') !== 'true')

const accessibleName = (control) => (control.getAttribute('aria-label') ?? control.textContent ?? '').trim()

// The target size of a control, read from its shared classes: the 48px touch target, or a size of at least 24px
// (Tailwind's 6 is 1.5rem) given as min-h and min-w, or as size.
const hasTargetClass = (control) => {
  const classes = (control.getAttribute('class') ?? '').split(/\s+/)
  if (classes.includes('md-touch-target')) return true
  const sizeOf = (prefix) => {
    const found = classes.find(name => new RegExp(`^${prefix}-\\d+$`).test(name))
    return found ? Number(found.slice(prefix.length + 1)) : 0
  }
  if (sizeOf('size') >= 6) return true
  return sizeOf('min-h') >= 6 && sizeOf('min-w') >= 6
}

describe('the repositories page keyboard and screen reader contract', () => {
  it('keeps every tabindex at 0 or below on the list and on the dialogs it opens', async () => {
    await mountAt('/settings/repositories')
    const positive = () => Array.from(document.querySelectorAll('[tabindex]'))
      .filter(element => Number(element.getAttribute('tabindex')) > 0)
      .map(element => element.outerHTML.slice(0, 80))

    expect(positive(), 'the list has no positive tabindex').toEqual([])

    wrapper.find('button[aria-label="Actions for releases"]').trigger('click')
    await flushPromises()
    expect(positive(), 'the open Actions menu has no positive tabindex').toEqual([])

    document.querySelector('[role="menuitem"][aria-label="Delete releases"]').click()
    await flushPromises()
    expect(positive(), 'the delete dialog has no positive tabindex').toEqual([])
  })

  it('keeps every tabindex at 0 or below on the detail page', async () => {
    await mountAt('/settings/repositories/releases')

    const positive = Array.from(document.querySelectorAll('[tabindex]'))
      .filter(element => Number(element.getAttribute('tabindex')) > 0)
    expect(positive, 'the detail has no positive tabindex').toEqual([])
  })

  it('names the scroll region of the table and makes it focusable', async () => {
    await mountAt('/settings/repositories')

    const region = wrapper.find('[role="region"]')
    expect(region.exists(), 'the table has a scroll region').toBe(true)
    expect(region.attributes('aria-label'), 'the region has an accessible name').toBeTruthy()
    expect(region.attributes('tabindex'), 'the region can take the keyboard focus').toBe('0')
  })

  it('gives every button and link on the list an accessible name', async () => {
    await mountAt('/settings/repositories')

    const unnamed = controls()
      .filter(control => control.tagName === 'BUTTON' || control.tagName === 'A')
      .filter(control => accessibleName(control) === '')
      .map(control => control.outerHTML.slice(0, 80))
    expect(unnamed, 'no button or link is without a name').toEqual([])
  })

  it('gives every button and link on the detail page an accessible name', async () => {
    await mountAt('/settings/repositories/releases')

    const unnamed = controls()
      .filter(control => control.tagName === 'BUTTON' || control.tagName === 'A')
      .filter(control => accessibleName(control) === '')
      .map(control => control.outerHTML.slice(0, 80))
    expect(unnamed, 'no button or link is without a name').toEqual([])
  })

  it('gives every interactive control on the list a target of at least 24 by 24 pixels, through its shared classes', async () => {
    await mountAt('/settings/repositories')

    const small = controls()
      .filter(control => !hasTargetClass(control))
      .map(control => `${control.tagName} ${accessibleName(control) || control.id || control.getAttribute('type')}`)
    expect(small, 'every control carries a target size class').toEqual([])
  })

  it('gives every interactive control on the detail page a target of at least 24 by 24 pixels, through its shared classes', async () => {
    await mountAt('/settings/repositories/releases')

    const small = controls()
      .filter(control => !hasTargetClass(control))
      .map(control => `${control.tagName} ${accessibleName(control) || control.id || control.getAttribute('type')}`)
    expect(small, 'every control carries a target size class').toEqual([])
  })

  it('reports the filter count in a status region once for each change', async () => {
    await mountAt('/settings/repositories')

    const input = wrapper.find('input[type="search"], input[type="text"]')
    input.element.value = 'snap'
    input.trigger('input')
    await flushPromises()

    const counts = () => wrapper.findAll('[role="status"]').filter(region => region.text().includes('repositories shown'))
    expect(counts().map(region => region.text()), 'one status region carries the count').toEqual(['1 of 5 repositories shown'])
  })

  it('reports the sort in one status region, and no other region repeats it', async () => {
    await mountAt('/settings/repositories')

    const sort = wrapper.findAll('thead th').find(header => header.text().startsWith('Type')).find('button')
    await sort.trigger('click')
    await flushPromises()

    const reports = wrapper.findAll('[role="status"]').filter(region => region.text().includes('Sorted by'))
    expect(reports.map(region => region.text()), 'the sort is announced once').toEqual(['Sorted by Type, ascending'])
  })

  it('returns the focus to New repository when Escape closes the wizard', async () => {
    await mountAt('/settings/repositories')
    const opener = wrapper.findAll('button').find(control => control.text() === 'New repository')
    opener.element.focus()
    await opener.trigger('click')
    await flushPromises()
    expect(document.querySelectorAll('#repository-wizard').length, 'the wizard is open').toBe(1)

    document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.querySelectorAll('#repository-wizard').length, 'Escape closes the wizard').toBe(0)
    expect(document.activeElement, 'the focus returns to the opener').toBe(opener.element)
  })
})
