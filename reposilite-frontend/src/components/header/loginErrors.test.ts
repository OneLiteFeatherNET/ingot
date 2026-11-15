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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent, h } from 'vue'
import { createErrorToast } from '../../helpers/toast'
import { loginErrorMessage, loginErrorOf, useLoginErrorNotice } from './loginErrors'

vi.mock('../../helpers/toast', () => ({ createErrorToast: vi.fn() }))

// A page that shows the notice the way the dashboard does: from its own setup.
const Page = defineComponent({
  setup() {
    useLoginErrorNotice()
    return () => h('div')
  }
})

const openAt = async (address) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: Page }]
  })
  await router.push(address)
  mount(Page, { global: { plugins: [router] } })
  await flushPromises()
  return router
}

describe('loginErrorMessage', () => {
  it.each([
    'access_denied', 'invalid_state', 'provider_error', 'token_exchange_failed', 'invalid_id_token',
    'email_domain_not_allowed', 'user_disabled', 'user_not_provisioned', 'username_taken', 'invalid_username',
    'setup_required', 'provider_unavailable', 'no_database'
  ])(
    'says in words what happened for %s, without the code',
    (code) => {
      const message = loginErrorMessage(code)

      expect(message, 'a visitor reads a sentence, not a code').not.toContain(code)
      expect(message.length).toBeGreaterThan(20)
    }
  )

  it('names the domain rule in the message for a domain that is not allowed', () => {
    expect(loginErrorMessage('email_domain_not_allowed')).toBe('Your email domain is not allowed to sign in to this instance.')
  })

  it('falls back to a generic sentence for a code it does not know', () => {
    expect(loginErrorMessage('something_new')).toBe('The sign in did not complete. Try again, or use another sign in method.')
  })
})

describe('loginErrorOf', () => {
  it('reads the code from the query', () => {
    expect(loginErrorOf({ loginError: 'access_denied' })).toBe('access_denied')
  })

  it('reads the first value of a repeated parameter', () => {
    expect(loginErrorOf({ loginError: ['invalid_state', 'provider_error'] })).toBe('invalid_state')
  })

  it('finds no code when the parameter is missing or empty', () => {
    expect(loginErrorOf({})).toBeUndefined()
    expect(loginErrorOf({ loginError: '' })).toBeUndefined()
  })
})

describe('useLoginErrorNotice', () => {
  beforeEach(() => {
    createErrorToast.mockClear()
  })

  it('shows the readable message of the provider failure once', async () => {
    await openAt('/?loginError=access_denied')

    expect(createErrorToast).toHaveBeenCalledTimes(1)
    expect(createErrorToast).toHaveBeenCalledWith(loginErrorMessage('access_denied'))
  })

  it('takes the code out of the address, so that a reload does not show it again', async () => {
    const router = await openAt('/?loginError=provider_error')

    expect(router.currentRoute.value.query.loginError).toBeUndefined()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('keeps the other parameters of the address', async () => {
    const router = await openAt('/access/users?tab=2&loginError=invalid_state')

    expect(router.currentRoute.value.path).toBe('/access/users')
    expect(router.currentRoute.value.query).toEqual({ tab: '2' })
  })

  it('shows nothing when the address names no failure', async () => {
    await openAt('/access/users?tab=2')

    expect(createErrorToast).not.toHaveBeenCalled()
  })
})
