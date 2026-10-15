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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../router/routes'
import { setClock, resetClock } from '../../helpers/clock'
import PrincipalsPanel from './PrincipalsPanel.vue'

// The session is replaced by a ref of the client, so each test decides what the server answers.
const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const shell = { render: () => null }

const makeRouter = async (url) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
  })
  await router.push(url)
  await router.isReady()
  return router
}

const ALICE = { id: 2, kind: 'USER', name: 'alice', displayName: 'Alice Example', disabled: false, createdAt: '2026-10-02T09:00:00Z', lastSeenAt: '2026-10-12T12:00:00Z' }
const BOB = { id: 3, kind: 'USER', name: 'bob', displayName: 'Robert Builder', disabled: true, createdAt: '2026-10-03T09:00:00Z', lastSeenAt: null }
const ADMIN = { id: 1, kind: 'USER', name: 'admin', displayName: 'Administrator', disabled: false, createdAt: '2026-10-01T09:00:00Z', lastSeenAt: '2026-10-15T08:00:00Z' }

const CI = { id: 4, kind: 'MACHINE', name: 'ci-bot', displayName: 'CI bot', disabled: false, createdAt: '2026-10-04T09:00:00Z', lastSeenAt: null }
const IDLE = { id: 5, kind: 'MACHINE', name: 'idle-bot', displayName: '', disabled: false, createdAt: '2026-10-04T09:00:00Z', lastSeenAt: null }

const DETAILS = {
  alice: { ...ALICE, teams: [], instanceRole: false, tokens: [] },
  bob: { ...BOB, teams: [], instanceRole: false, tokens: [] },
  admin: { ...ADMIN, teams: [], instanceRole: true, tokens: [] },
  'ci-bot': {
    ...CI,
    teams: ['developers'],
    instanceRole: false,
    tokens: [
      { name: 'old', lastUsedAt: '2026-10-01T12:00:00Z' },
      { name: 'new', lastUsedAt: '2026-10-14T12:00:00Z' },
      { name: 'unused', lastUsedAt: null }
    ]
  },
  'idle-bot': { ...IDLE, teams: [], instanceRole: false, tokens: [{ name: 'unused', lastUsedAt: null }] }
}

const dialog = () => document.querySelector('[role="dialog"]')

const type = (id, value) => {
  const input = document.getElementById(id)
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const submitDialog = async () => {
  dialog().querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('PrincipalsPanel', () => {
  let wrapper
  let router
  let principals

  const mountPanel = async (props, url = '/access/users') => {
    router = await makeRouter(url)
    wrapper = mount(PrincipalsPanel, { props, attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()
  }

  const usernames = () => wrapper.findAll('[data-test="row"]').map(row => row.find('[data-test="username"]').text())
  const rowOf = (name) =>
    wrapper.findAll('[data-test="row"]').find(row => row.find('[data-test="username"]').text() === name)

  beforeEach(() => {
    setClock(() => new Date(Date.UTC(2026, 9, 15, 12)))
    principals = {
      list: vi.fn(async (kind) => ({ data: kind === 'USER' ? [ADMIN, ALICE, BOB] : [CI, IDLE] })),
      get: vi.fn(async (name) => ({ data: DETAILS[name], headers: { etag: '"1"' } })),
      create: vi.fn().mockResolvedValue({ data: {} }),
      setPassword: vi.fn().mockResolvedValue({ status: 204 }),
      update: vi.fn(),
      remove: vi.fn(),
      grantInstanceAdmin: vi.fn(),
      revokeInstanceAdmin: vi.fn()
    }
    holder.session = {
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

    it('filters the list by username', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper.find('[data-test="search"]').setValue('ali')

      expect(usernames()).toEqual(['alice'])
    })

    it('filters the list by display name', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper.find('[data-test="search"]').setValue('builder')

      expect(usernames()).toEqual(['bob'])
    })

    it('says so when nothing matches the search', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper.find('[data-test="search"]').setValue('zed')

      expect(usernames()).toEqual([])
      expect(wrapper.text()).toContain('Nothing matches "zed"')
    })

    it('shows the display name, the username and the Disabled chip', async () => {
      await mountPanel({ kind: 'USER' })

      const bob = rowOf('bob')

      expect(bob.find('[data-test="display-name"]').text()).toBe('Robert Builder')
      expect(bob.find('[data-test="disabled-badge"]').exists()).toBe(true)
      expect(rowOf('alice').find('[data-test="disabled-badge"]').exists(), 'alice is active').toBe(false)
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

      expect(wrapper.find('#principal-title').text()).toBe('Alice Example')
      expect(principals.get).toHaveBeenCalledWith('alice')
    })

    it('creates a user through the dialog: the principal first, then the password', async () => {
      await mountPanel({ kind: 'USER' })

      await wrapper.find('[data-test="new-principal"]').trigger('click')
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

      await wrapper.find('[data-test="new-principal"]').trigger('click')
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

    it('shows the newest last use among the tokens of a machine account', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      const activity = rowOf('ci-bot').find('[data-test="activity"]')

      expect(activity.text()).toBe('Last used 1 day ago')
      expect(activity.attributes('title')).toBe('2026-10-14 12:00 UTC')
    })

    it('says that a machine account without a used token was never used', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      expect(rowOf('idle-bot').find('[data-test="activity"]').text()).toBe('Never used')
    })

    it('falls back to the name where there is no display name', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      expect(rowOf('idle-bot').find('[data-test="display-name"]').text()).toBe('idle-bot')
    })

    it('creates a machine account with kind MACHINE', async () => {
      await mountPanel({ kind: 'MACHINE' }, '/access/machines')

      await wrapper.find('[data-test="new-principal"]').trigger('click')
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
