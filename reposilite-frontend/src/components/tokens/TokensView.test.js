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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// The client is swapped per test through this holder. The store reads it lazily.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

const fakeClient = (overrides = {}) => ({
  tokens: {
    list: vi.fn(() => Promise.resolve({ data: [] })),
    createOrUpdate: vi.fn(() => Promise.resolve({ data: { secret: 's3cr3t' } })),
    update: vi.fn(() => Promise.resolve({})),
    delete: vi.fn(() => Promise.resolve({})),
    regenerateSecret: vi.fn(() => Promise.resolve({ data: 'fresh-secret' })),
    ...overrides
  }
})

const token = (name, overrides = {}) => ({
  name,
  identifier: { type: 'PERSISTENT', value: 1 },
  createdAt: [2026, 1, 1],
  expiresAt: null,
  description: '',
  permissions: [],
  routes: [],
  ...overrides
})

const readRoute = (path) => ({ path, permission: { identifier: 'route:read' } })

// Fresh modules per test: the token store is a shared singleton, and each test needs its own.
const mountView = async (props = {}) => {
  const { default: TokensView } = await import('./TokensView.vue')
  return mount(TokensView, { props: { selectedTab: 'Tokens', ...props } })
}

// Fixed reference time for the expiry tests: 2026-06-15 at noon UTC. The view must not read the system clock.
const NOW = Date.UTC(2026, 5, 15, 12)

const buttonWithText = (wrapper, text) =>
  wrapper.findAll('button').find(button => button.text() === text)

const clickButton = async (wrapper, text) => {
  const button = buttonWithText(wrapper, text)
  if (!button) throw new Error(`no button labelled "${text}"`)
  await button.trigger('click')
}

describe('TokensView', () => {
  let wrapper

  beforeEach(() => {
    vi.resetModules()
    session.client = fakeClient()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  describe('one-time secret', () => {
    it('keeps the secret visible after the view is remounted', async () => {
      const store = (await import('../../store/tokens')).useTokens()
      await store.createToken('ci', { type: 'PERSISTENT' })

      wrapper = await mountView()
      wrapper.unmount()
      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('s3cr3t')
    })

    it('removes the secret once it is dismissed', async () => {
      const store = (await import('../../store/tokens')).useTokens()
      await store.createToken('ci', { type: 'PERSISTENT' })
      wrapper = await mountView()
      await flushPromises()

      await clickButton(wrapper, 'Dismiss')

      expect(wrapper.text()).not.toContain('s3cr3t')
    })

    it('announces the secret through a live region', async () => {
      const store = (await import('../../store/tokens')).useTokens()
      await store.createToken('ci', { type: 'PERSISTENT' })
      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.find('[role="status"]').text()).toContain('s3cr3t')
    })

    it('tells the user that the secret will not be shown again', async () => {
      const store = (await import('../../store/tokens')).useTokens()
      await store.createToken('ci', { type: 'PERSISTENT' })
      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.text()).toMatch(/you won.t see this secret again/i)
    })
  })

  describe('loading, error and empty states', () => {
    it('shows a progress indicator while the list is loading', async () => {
      session.client = fakeClient({ list: vi.fn(() => deferred().promise) })

      wrapper = await mountView()

      expect(wrapper.find('[role="progressbar"]').exists()).toBe(true)
    })

    it('does not claim there are no tokens while the list is loading', async () => {
      session.client = fakeClient({ list: vi.fn(() => deferred().promise) })

      wrapper = await mountView()

      expect(wrapper.text()).not.toContain('No access tokens yet')
    })

    it('shows the failure message when the list request fails', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.reject(new Error('network down'))) })

      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('network down')
    })

    it('offers a retry action when the list request fails', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.reject(new Error('network down'))) })

      wrapper = await mountView()
      await flushPromises()

      expect(buttonWithText(wrapper, 'Retry')).toBeDefined()
    })

    it('does not show the empty state after a failed request', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.reject(new Error('network down'))) })

      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.text()).not.toContain('No access tokens yet')
    })

    it('requests the list again when retry is pressed', async () => {
      const list = vi.fn()
        .mockImplementationOnce(() => Promise.reject(new Error('network down')))
        .mockImplementationOnce(() => Promise.resolve({ data: [token('ci')] }))
      session.client = fakeClient({ list })

      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, 'Retry')
      await flushPromises()

      expect(list).toHaveBeenCalledTimes(2)
    })

    it('shows the empty state once the server reports no tokens', async () => {
      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('No access tokens yet')
    })
  })

  describe('routes', () => {
    it('opens the route editor from a real button', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('ci')] })) })
      wrapper = await mountView()
      await flushPromises()

      expect(buttonWithText(wrapper, '+ Add route')).toBeDefined()
    })

    it('shows an inline error and saves nothing when the route path is empty', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('ci')] })) })
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Add route')

      await clickButton(wrapper, 'Add')

      expect(wrapper.text()).toContain('Enter a route path')
    })

    it('does not save a route whose path is empty', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('ci')] })) })
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Add route')

      await clickButton(wrapper, 'Add')

      expect(session.client.tokens.update).not.toHaveBeenCalled()
    })

    it('offers to edit the existing route when the new path is a duplicate', async () => {
      session.client = fakeClient({
        list: vi.fn(() => Promise.resolve({ data: [token('ci', { routes: [readRoute('/releases')] })] }))
      })
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Add route')
      await wrapper.find('input[name="route-path"]').setValue('/releases')

      await clickButton(wrapper, 'Add')

      expect(wrapper.text()).toContain('already exists')
    })

    it('does not overwrite the existing route when the new path is a duplicate', async () => {
      session.client = fakeClient({
        list: vi.fn(() => Promise.resolve({ data: [token('ci', { routes: [readRoute('/releases')] })] }))
      })
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Add route')
      await wrapper.find('input[name="route-path"]').setValue('/releases')

      await clickButton(wrapper, 'Add')

      expect(session.client.tokens.update).not.toHaveBeenCalled()
    })

    it('shows the routes of a manager token as inactive, with the reason', async () => {
      session.client = fakeClient({
        list: vi.fn(() => Promise.resolve({
          data: [token('root', {
            permissions: [{ identifier: 'access-token:manager' }],
            routes: [readRoute('/releases')]
          })]
        }))
      })
      wrapper = await mountView()
      await flushPromises()

      expect(wrapper.text()).toMatch(/inactive while the token has manager access/i)
    })
  })

  describe('unsaved drafts', () => {
    const twoTokens = () => fakeClient({
      list: vi.fn(() => Promise.resolve({ data: [token('a', { description: 'old' }), token('b', { description: 'other' })] }))
    })

    it('asks for confirmation before opening another editor over unsaved changes', async () => {
      session.client = twoTokens()
      wrapper = await mountView()
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')
      await wrapper.find('input[name="description"]').setValue('changed')

      await wrapper.find('[aria-label="Edit token b"]').trigger('click')

      expect(wrapper.text()).toMatch(/unsaved changes/i)
    })

    it('keeps the unsaved draft while the confirmation is pending', async () => {
      session.client = twoTokens()
      wrapper = await mountView()
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')
      await wrapper.find('input[name="description"]').setValue('changed')

      await wrapper.find('[aria-label="Edit token b"]').trigger('click')

      expect(wrapper.find('input[name="description"]').element.value).toBe('changed')
    })

    it('opens the other editor once the user discards the changes', async () => {
      session.client = twoTokens()
      wrapper = await mountView()
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')
      await wrapper.find('input[name="description"]').setValue('changed')
      await wrapper.find('[aria-label="Edit token b"]').trigger('click')

      await clickButton(wrapper, 'Discard changes')

      expect(wrapper.find('input[name="description"]').element.value).toBe('other')
    })

    it('opens the other editor without asking when nothing was changed', async () => {
      session.client = twoTokens()
      wrapper = await mountView()
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')

      await wrapper.find('[aria-label="Edit token b"]').trigger('click')

      expect(wrapper.text()).not.toMatch(/unsaved changes/i)
    })
  })

  describe('confirmation steps', () => {
    it('says that revoking cannot be undone', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('a')] })) })
      wrapper = await mountView()
      await flushPromises()

      await wrapper.find('[aria-label="Revoke token a"]').trigger('click')

      expect(wrapper.text()).toContain('Revoking cannot be undone.')
    })

    it('says that the old secret stops working when regenerating', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('a')] })) })
      wrapper = await mountView()
      await flushPromises()

      await wrapper.find('[aria-label="Regenerate the secret of token a"]').trigger('click')

      expect(wrapper.text()).toContain('The old secret stops working immediately.')
    })
  })

  describe('token creation', () => {
    it('shows an inline error when the token name is already taken', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('ci')] })) })
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Generate token')
      await wrapper.find('input[name="token-name"]').setValue('ci')

      await clickButton(wrapper, 'Generate')

      expect(wrapper.text()).toContain("A token named 'ci' already exists")
    })

    it('does not create a token whose name is already taken', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('ci')] })) })
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Generate token')
      await wrapper.find('input[name="token-name"]').setValue('ci')

      await clickButton(wrapper, 'Generate')

      expect(session.client.tokens.createOrUpdate).not.toHaveBeenCalled()
    })

    it('explains what persistent and temporary tokens mean', async () => {
      wrapper = await mountView()
      await flushPromises()
      await clickButton(wrapper, '+ Generate token')

      expect(wrapper.text()).toMatch(/survive a restart/i)
    })
  })

  describe('expiry date', () => {
    const editExpiry = async (date) => {
      const client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('a')] })) })
      session.client = client
      wrapper = await mountView({ now: NOW })
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')
      await wrapper.find('input[name="expires"]').setValue(date)
      await clickButton(wrapper, 'Save')
      await flushPromises()
      return client
    }

    it('rejects a date before today with an inline error', async () => {
      const client = await editExpiry('2026-06-14')

      expect(wrapper.text()).toContain('Choose today or a later date')
      expect(client.tokens.update).not.toHaveBeenCalled()
    })

    it('accepts today as the expiry date', async () => {
      const client = await editExpiry('2026-06-15')

      expect(wrapper.text()).not.toContain('Choose today or a later date')
      expect(client.tokens.update).toHaveBeenCalledTimes(1)
    })

    it('accepts a future expiry date', async () => {
      const client = await editExpiry('2026-07-01')

      expect(wrapper.text()).not.toContain('Choose today or a later date')
      expect(client.tokens.update).toHaveBeenCalledTimes(1)
    })

    it('offers today as the earliest date in the picker', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('a')] })) })
      wrapper = await mountView({ now: NOW })
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')

      expect(wrapper.find('input[name="expires"]').attributes('min')).toBe('2026-06-15')
    })
  })

  describe('row actions', () => {
    const tokenWithRoute = () => fakeClient({
      list: vi.fn(() => Promise.resolve({ data: [token('a', { routes: [readRoute('/x')] })] }))
    })

    it('renders the edit, regenerate and revoke actions of a token row', async () => {
      session.client = tokenWithRoute()
      wrapper = await mountView({ now: NOW })
      await flushPromises()

      for (const label of ['Edit token a', 'Regenerate the secret of token a', 'Revoke token a']) {
        expect(wrapper.find(`[aria-label="${label}"]`).exists(), label).toBe(true)
      }
    })

    it('makes the token row actions focusable buttons', async () => {
      session.client = tokenWithRoute()
      wrapper = await mountView({ now: NOW })
      await flushPromises()

      for (const label of ['Edit token a', 'Regenerate the secret of token a', 'Revoke token a']) {
        const element = wrapper.find(`[aria-label="${label}"]`).element
        expect(element.tagName, label).toBe('BUTTON')
        expect(element.disabled, label).toBe(false)
        expect(element.tabIndex, label).toBeGreaterThanOrEqual(0)
      }
    })

    it('renders the route row actions in the DOM as focusable buttons', async () => {
      session.client = tokenWithRoute()
      wrapper = await mountView({ now: NOW })
      await flushPromises()

      const edit = wrapper.find('[aria-label="Edit route /x"]').element
      expect(edit.tagName).toBe('BUTTON')
      expect(edit.disabled).toBe(false)
    })
  })

  describe('confirmation buttons', () => {
    const unsavedDraft = async () => {
      session.client = fakeClient({
        list: vi.fn(() => Promise.resolve({ data: [token('a', { description: 'old' }), token('b')] }))
      })
      wrapper = await mountView({ now: NOW })
      await flushPromises()
      await wrapper.find('[aria-label="Edit token a"]').trigger('click')
      await wrapper.find('input[name="description"]').setValue('changed')
      await wrapper.find('[aria-label="Edit token b"]').trigger('click')
    }

    it('makes keep editing the tonal action of the unsaved-changes notice', async () => {
      await unsavedDraft()

      expect(buttonWithText(wrapper, 'Keep editing').attributes('data-variant')).toBe('tonal')
    })

    it('makes discard changes a text button in the error colour', async () => {
      await unsavedDraft()

      const discard = buttonWithText(wrapper, 'Discard changes')
      expect(discard.attributes('data-variant')).toBe('text')
      expect(discard.classes()).toContain('text-error!')
    })

    it('makes cancel the tonal action of the revoke confirmation', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('a')] })) })
      wrapper = await mountView({ now: NOW })
      await flushPromises()
      await wrapper.find('[aria-label="Revoke token a"]').trigger('click')

      expect(buttonWithText(wrapper, 'Cancel').attributes('data-variant')).toBe('tonal')
    })

    it('makes revoke a text button in the error colour', async () => {
      session.client = fakeClient({ list: vi.fn(() => Promise.resolve({ data: [token('a')] })) })
      wrapper = await mountView({ now: NOW })
      await flushPromises()
      await wrapper.find('[aria-label="Revoke token a"]').trigger('click')

      const revoke = buttonWithText(wrapper, 'Revoke')
      expect(revoke.attributes('data-variant')).toBe('text')
      expect(revoke.classes()).toContain('text-error!')
    })
  })
})
