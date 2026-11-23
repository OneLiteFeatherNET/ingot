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

import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router, type RouteRecordRaw } from 'vue-router'
import { ROUTES } from '../../router/routes'
import { setClock, resetClock } from '../../helpers/clock'
import type { PrincipalKind } from './accessPrincipals'
import PrincipalActionsMenu from './PrincipalActionsMenu.vue'
import PrincipalsPanel from './PrincipalsPanel.vue'
import { selectedKey } from './rowSelection'

// The API sends an Instant as epoch seconds: the fixtures carry the same number.
vi.mock('./rowSelection', async importOriginal => {
  const actual = await importOriginal<typeof import('./rowSelection')>()
  return { ...actual, selectedKey: vi.fn(actual.selectedKey) }
})

const wireInstant = (iso: string) => Date.parse(iso) / 1000

// The session is replaced by a ref of the client, so each test decides what the server answers.
const holder = vi.hoisted((): { session: unknown } => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: { response?: { data?: { message?: string } }, message?: string } | undefined) => error?.response?.data?.message ?? error?.message ?? String(error?.message)
}))

const shell = { render: () => null }

const makeRouter = async (url: string): Promise<Router> => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell })) as RouteRecordRaw[]
  })
  await router.push(url)
  await router.isReady()
  return router
}

// The list items as the server answers them: the flat principal and the summaries a row shows.
const ALICE = { id: 2, kind: 'USER', name: 'alice', displayName: 'Alice Example', disabled: false, createdAt: wireInstant('2026-10-02T09:00:00Z'), lastSeenAt: wireInstant('2026-10-12T12:00:00Z'), instanceAdmin: false, instanceRole: false, tokenCount: 0, providers: ['local'] }
const BOB = { id: 3, kind: 'USER', name: 'bob', displayName: 'Robert Builder', disabled: true, createdAt: wireInstant('2026-10-03T09:00:00Z'), lastSeenAt: null, instanceAdmin: false, instanceRole: false, tokenCount: 0, providers: ['local'] }
const ADMIN = { id: 1, kind: 'USER', name: 'admin', displayName: 'Administrator', disabled: false, createdAt: wireInstant('2026-10-01T09:00:00Z'), lastSeenAt: wireInstant('2026-10-15T08:00:00Z'), instanceAdmin: true, instanceRole: true, tokenCount: 0, providers: ['local'] }

const CI = { id: 4, kind: 'MACHINE', name: 'ci-bot', displayName: 'CI bot', disabled: false, createdAt: wireInstant('2026-10-04T09:00:00Z'), lastSeenAt: wireInstant('2026-10-14T12:00:00Z'), instanceAdmin: false, instanceRole: false, tokenCount: 3, providers: [] }
const IDLE = { id: 5, kind: 'MACHINE', name: 'idle-bot', displayName: '', disabled: false, createdAt: wireInstant('2026-10-04T09:00:00Z'), lastSeenAt: null, instanceAdmin: false, instanceRole: false, tokenCount: 1, providers: [] }

// What the detail view reads when a row is opened. The list never asks for it.
const DETAILS: Record<string, unknown> = {
  alice: { ...ALICE, teams: [], instanceRole: false, tokens: [] },
  bob: { ...BOB, teams: [], instanceRole: false, tokens: [] },
  admin: { ...ADMIN, teams: [], instanceRole: true, tokens: [] },
  'ci-bot': {
    ...CI,
    teams: ['developers'],
    instanceRole: false,
    tokens: [
      { name: 'old', lastUsedAt: wireInstant('2026-10-01T12:00:00Z') },
      { name: 'new', lastUsedAt: wireInstant('2026-10-14T12:00:00Z') },
      { name: 'unused', lastUsedAt: null }
    ]
  },
  'idle-bot': { ...IDLE, teams: [], instanceRole: false, tokens: [{ name: 'unused', lastUsedAt: null }] }
}

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')!

const type = (id: string, value: string) => {
  const input = document.getElementById(id) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const submitDialog = async () => {
  dialog().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('PrincipalsPanel', () => {
  let wrapper: VueWrapper | undefined
  let router: Router
  let principals: Record<'list' | 'get' | 'create' | 'setPassword' | 'update' | 'remove' | 'grantInstanceAdmin' | 'revokeInstanceAdmin', Mock>

  const mountPanel = async (props: { kind: PrincipalKind, name?: string }, url = '/access/users') => {
    router = await makeRouter(url)
    wrapper = mount(PrincipalsPanel, { props, attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()
  }

  const usernames = () => wrapper!.findAll('[data-test="row"]').map(row => row.find('[data-test="username"]').text())
  const rowOf = (name: string) =>
    wrapper!.findAll('[data-test="row"]').find(row => row.find('[data-test="username"]').text() === name)!

  beforeEach(() => {
    setClock(() => new Date(Date.UTC(2026, 9, 15, 12)))
    principals = {
      list: vi.fn((kind: string) => Promise.resolve({ data: kind === 'USER' ? [ADMIN, ALICE, BOB] : [CI, IDLE] })),
      get: vi.fn((name: string) => Promise.resolve({ data: DETAILS[name], headers: { etag: '"1"' } })),
      create: vi.fn().mockResolvedValue({ data: {} }),
      setPassword: vi.fn().mockResolvedValue({ status: 204 }),
      update: vi.fn(),
      remove: vi.fn(),
      grantInstanceAdmin: vi.fn(),
      revokeInstanceAdmin: vi.fn()
    }
    holder.session = {
      passwordMinLength: ref(12),
      client: ref({
        identity: {
          principals,
          principalTokens: () => ({ list: vi.fn().mockResolvedValue({ data: [] }), create: vi.fn(), revoke: vi.fn() })
        },
        tokens: { get: vi.fn().mockRejectedValue(new Error('not found')) }
      })
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
    resetClock()
  })

  describe('users', () => {
    it('lists the principals of kind USER', async () => {
      await mountPanel({ kind: 'USER' })

      expect(principals.list).toHaveBeenCalledWith('USER')
      expect(usernames()).toEqual(['admin', 'alice', 'bob'])
    })

    it('reads the list in one request and no detail of any row', async () => {
      await mountPanel({ kind: 'USER' })

      expect(principals.list, 'one list request').toHaveBeenCalledTimes(1)
      expect(principals.get, 'no per-row detail request').not.toHaveBeenCalled()
    })

    it('shows the instance admin chip of a user who holds the role through a team only', async () => {
      principals.list.mockResolvedValue({ data: [{ ...ALICE, instanceAdmin: true, instanceRole: false }] })

      await mountPanel({ kind: 'USER' })

      expect(rowOf('alice').find('[data-test="admin-badge"]').exists(), 'the list item says instanceAdmin').toBe(true)
    })

    it('hands the actions menu the direct role and not the role held through a team', async () => {
      principals.list.mockResolvedValue({ data: [{ ...ALICE, instanceAdmin: true, instanceRole: false }, ADMIN] })

      await mountPanel({ kind: 'USER' })

      const menus = wrapper!.findAllComponents(PrincipalActionsMenu)
      expect(menus.map(menu => menu.props('instanceAdmin') as boolean),'alice holds it through a team, admin directly').toEqual([false, true])
    })

    it('shows no instance admin chip when the list item lacks the flags', async () => {
      principals.list.mockResolvedValue({ data: [{ id: 2, kind: 'USER', name: 'alice', displayName: 'Alice Example', disabled: false, createdAt: wireInstant('2026-10-02T09:00:00Z'), lastSeenAt: null }] })

      await mountPanel({ kind: 'USER' })

      expect(rowOf('alice').find('[data-test="admin-badge"]').exists()).toBe(false)
    })

    it('lifts the row of an open actions menu above the rows that follow it', async () => {
      await mountPanel({ kind: 'USER' })

      await rowOf('alice').find('button[aria-haspopup="menu"]').trigger('click')

      expect(rowOf('alice').classes(), 'each row is its own stacking context, so the open one needs a z-index').toContain('z-md-sticky')
      expect(rowOf('bob').classes()).not.toContain('z-md-sticky')
    })

    it('puts the row back when the actions menu closes', async () => {
      await mountPanel({ kind: 'USER' })
      await rowOf('alice').find('button[aria-haspopup="menu"]').trigger('click')

      await rowOf('alice').find('button[aria-haspopup="menu"]').trigger('click')

      expect(rowOf('alice').classes()).not.toContain('z-md-sticky')
    })

    it('builds the principal rows from the shared list rows', async () => {
      await mountPanel({ kind: 'USER' })

      expect(wrapper!.findComponent({ name: 'MdList' }).exists(), 'the rows sit in an MdList').toBe(true)
      const items = wrapper!.findAllComponents({ name: 'MdListItem' })
      expect(items.map(item => item.attributes('data-test')), 'every principal row is an MdListItem').toEqual(['row', 'row', 'row'])
    })

    it('puts the actions menu beside the link of a row, not inside it', async () => {
      await mountPanel({ kind: 'USER' })

      const link = rowOf('alice').find('a')
      expect(link.find('button[aria-haspopup="menu"]').exists(), 'a menu button inside a link is nested interactive content').toBe(false)
      expect(rowOf('alice').find('button[aria-haspopup="menu"]').exists(), 'the row still offers its actions').toBe(true)
    })

    it('marks the link of the open user as current, and nothing else', async () => {
      await mountPanel({ kind: 'USER', name: 'alice' }, '/access/users/alice')

      const current = wrapper!.findAll('[data-test="principals-list"] [aria-current]')
      expect(current, 'exactly one element of the list is current').toHaveLength(1)
      expect(current[0].element.tagName, 'the link carries the state, so it is announced with the link').toBe('A')
      expect(current[0].attributes('aria-current'), 'the open user is the current item').toBe('true')
      expect(current[0].find('[data-test="username"]').text(), 'the current link is the open user').toBe('alice')
    })

    it('asks for the selected row once per render of the list', async () => {
      await mountPanel({ kind: 'USER', name: 'alice' }, '/access/users/alice')
      vi.mocked(selectedKey).mockClear()

      await wrapper!.find('[data-test="search"]').setValue('a')

      expect(selectedKey, 'one answer per render, whatever the number of rows').toHaveBeenCalledTimes(1)
    })

    it('draws the selected container on the row of the open user only', async () => {
      await mountPanel({ kind: 'USER', name: 'alice' }, '/access/users/alice')

      expect(rowOf('alice').classes(), 'the open row takes the secondary container').toContain('bg-secondary-container')
      expect(rowOf('bob').classes(), 'another row keeps the list surface').not.toContain('bg-secondary-container')
    })

    it('leaves a ctrl click on a row to the browser, so the user opens in a new tab', async () => {
      await mountPanel({ kind: 'USER' })

      await rowOf('alice').find('a').trigger('click', { ctrlKey: true })
      await flushPromises()

      expect(router.currentRoute.value.fullPath, 'the list stays where it is').toBe('/access/users')
    })

    it('filters the list by username', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper!.find('[data-test="search"]').setValue('ali')

      expect(usernames()).toEqual(['alice'])
    })

    it('filters the list by display name', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper!.find('[data-test="search"]').setValue('builder')

      expect(usernames()).toEqual(['bob'])
    })

    it('draws a failed load as an error state panel and reads the list again on Retry', async () => {
      principals.list.mockRejectedValueOnce({ message: 'Store unavailable' })
      await mountPanel({ kind: 'USER' })

      const panel = wrapper!.find('[data-test="list-error"]')
      expect(panel.exists(), 'the error is an error state panel').toBe(true)
      expect(panel.attributes('data-ingot-state')).toBe('error')
      expect(panel.text()).toContain('Store unavailable')

      await panel.find('button').trigger('click')
      await flushPromises()

      expect(wrapper!.find('[data-test="list-error"]').exists(), 'the retry cleared the error').toBe(false)
      expect(usernames()).toEqual(['admin', 'alice', 'bob'])
    })

    it('draws the loading line as a busy state panel', async () => {
      principals.list.mockReturnValue(new Promise(() => undefined))
      await mountPanel({ kind: 'USER' })

      const panel = wrapper!.find('[data-test="list-loading"]')
      expect(panel.exists(), 'the loading state panel is drawn').toBe(true)
      expect(panel.attributes('aria-busy')).toBe('true')
      expect(wrapper!.text()).not.toContain('Loading...')
    })

    it('draws the empty list as an empty state panel', async () => {
      principals.list.mockResolvedValue({ data: [] })
      await mountPanel({ kind: 'USER' })

      const panel = wrapper!.find('[data-test="list-empty"]')
      expect(panel.exists(), 'the empty state panel is drawn').toBe(true)
      expect(panel.text()).toContain('There are no users yet.')
    })

    it('says so when nothing matches the search', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper!.find('[data-test="search"]').setValue('zed')

      expect(usernames()).toEqual([])
      expect(wrapper!.text()).toContain('Nothing matches "zed"')
    })

    it('shows the display name, the username and the Disabled chip', async () => {
      await mountPanel({ kind: 'USER' })

      const bob = rowOf('bob')

      expect(bob.find('[data-test="display-name"]').text()).toBe('Robert Builder')
      expect(bob.find('[data-test="disabled-badge"]').exists()).toBe(true)
      expect(rowOf('alice').find('[data-test="disabled-badge"]').exists(), 'alice is active').toBe(false)
    })

    it('keeps the same inset on the detail pane at every width', async () => {
      await mountPanel({ kind: 'USER', name: 'alice' }, '/access/users/alice')

      const pane = wrapper!.find('.expanded\\:p-6')
      expect(pane.exists(), 'the detail pane takes the wide inset from 840 pixels').toBe(true)
      expect(pane.classes(), 'the detail pane has an inset below 840 pixels too').toContain('p-4')
      expect(pane.classes(), 'the detail pane is never hidden below 840 pixels').not.toContain('max-expanded:hidden')
    })

    it('shows the Instance admin chip for the user who holds the role', async () => {
      await mountPanel({ kind: 'USER' })

      expect(rowOf('admin').find('[data-test="admin-badge"]').exists()).toBe(true)
      expect(rowOf('alice').find('[data-test="admin-badge"]').exists()).toBe(false)
    })

    it('shows the last sign-in relative to the clock with the absolute time as tooltip', async () => {
      await mountPanel({ kind: 'USER' })

      const activity = rowOf('alice').find('[data-test="activity"]')

      expect(activity.text()).toBe('Last sign-in 3 days ago')
      expect(activity.attributes('title')).toBe('2026-10-12 12:00 UTC')
    })

    it('says that a user who never signed in never did', async () => {
      await mountPanel({ kind: 'USER' })

      const activity = rowOf('bob').find('[data-test="activity"]')

      expect(activity.text()).toBe('Never signed in')
      expect(activity.attributes('title')).toBeUndefined()
    })

    it('opens the detail of a user through the router when its row is chosen', async () => {
      await mountPanel({ kind: 'USER' })

      await rowOf('alice').find('a').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/access/users/alice')
    })

    it('shows the detail of the user named by the address next to the list', async () => {
      await mountPanel({ kind: 'USER', name: 'alice' }, '/access/users/alice')

      expect(wrapper!.find('[data-test="principal-display-name"]').text()).toBe('Alice Example')
      expect(principals.get).toHaveBeenCalledWith('alice')
    })

    it('offers the permission check next to New user on the list when no user is selected', async () => {
      await mountPanel({ kind: 'USER' })

      const link = wrapper!.findAll('a').find(item => item.text() === 'Check a permission')
      expect(link?.attributes('href'), 'the list toolbar opens the check').toBe('/access/check')
    })

    it('draws no permission check link beside the detail of a user, the detail pane offers its own', async () => {
      await mountPanel({ kind: 'USER', name: 'alice' }, '/access/users/alice')

      expect(wrapper!.findAll('a').map(item => item.text())).not.toContain('Check a permission')
    })

    it('creates a user through the dialog: the principal first, then the password', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper!.find('[data-test="new-principal"]').trigger('click')
      await flushPromises()
      type('new-user-name', 'carol')
      type('new-user-password', 'a-long-password')
      await submitDialog()

      expect(principals.create).toHaveBeenCalledWith({ kind: 'USER', name: 'carol' })
      expect(principals.setPassword).toHaveBeenCalledWith('carol', 'a-long-password')
      expect(principals.create.mock.invocationCallOrder[0]).toBeLessThan(principals.setPassword.mock.invocationCallOrder[0])
    })

    it('reads the list again after a user was created', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper!.find('[data-test="new-principal"]').trigger('click')
      await flushPromises()
      type('new-user-name', 'carol')
      type('new-user-password', 'a-long-password')
      await submitDialog()

      expect(principals.list).toHaveBeenCalledTimes(2)
    })
  })

  describe('machine accounts', () => {
    it('lists the principals of kind MACHINE', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      expect(principals.list).toHaveBeenCalledWith('MACHINE')
      expect(usernames()).toEqual(['ci-bot', 'idle-bot'])
    })

    it('reads the list in one request and no detail of any machine account', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      expect(principals.list, 'one list request').toHaveBeenCalledTimes(1)
      expect(principals.get, 'no per-row detail request').not.toHaveBeenCalled()
    })

    it('shows the last use of a machine account from the last seen time of its list item', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      const activity = rowOf('ci-bot').find('[data-test="activity"]')

      expect(activity.text()).toBe('Last used 1 day ago')
      expect(activity.attributes('title')).toBe('2026-10-14 12:00 UTC')
    })

    it('says that a machine account that was never seen was never used', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      expect(rowOf('idle-bot').find('[data-test="activity"]').text()).toBe('Never used')
    })

    it('falls back to the name where there is no display name', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      expect(rowOf('idle-bot').find('[data-test="display-name"]').text()).toBe('idle-bot')
    })

    it('creates a machine account with kind MACHINE', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      await wrapper!.find('[data-test="new-principal"]').trigger('click')
      await flushPromises()
      type('new-machine-name', 'deploy-bot')
      await submitDialog()

      expect(principals.create).toHaveBeenCalledWith({ kind: 'MACHINE', name: 'deploy-bot' })
      expect(principals.setPassword, 'a machine account has no password').not.toHaveBeenCalled()
    })

    it('opens the machine detail route when a row is chosen', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      await rowOf('ci-bot').find('a').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/access/machines/ci-bot')
    })
  })
})
