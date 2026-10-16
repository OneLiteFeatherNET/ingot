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
import { computed, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import AccountView from './AccountView.vue'

// The session is replaced by plain refs, so each test decides who is signed in and how.
const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const ALICE = { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' }

const fakeSession = ({ mode = 'cookie', mustChange = false, admin = false, teams = ['developers'] } = {}) => {
  const me = {
    changePassword: vi.fn().mockResolvedValue({ status: 204 }),
    tokens: {
      list: vi.fn().mockResolvedValue({ data: [] }),
      create: vi.fn(),
      rotate: vi.fn(),
      revoke: vi.fn()
    }
  }
  const details = ref({ teams, instanceAdmin: admin, principal: ALICE })
  return {
    me,
    principal: ref(ALICE),
    details,
    instanceAdmin: computed(() => details.value.instanceAdmin === true),
    authMode: ref(mode),
    mustChangePassword: ref(mustChange),
    client: ref({ identity: { me } })
  }
}

describe('AccountView', () => {
  let wrapper

  const mountView = async (options) => {
    holder.session = fakeSession(options)
    wrapper = mount(AccountView, { attachTo: document.body })
    await flushPromises()
    return holder.session
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    holder.session = null
  })

  it('renders the profile with the teams and the instance admin badge', async () => {
    await mountView({ admin: true, teams: ['developers', 'release'] })

    expect(wrapper.findAll('[data-test="team"]').map(chip => chip.text())).toEqual(['developers', 'release'])
    expect(wrapper.find('[data-test="instance-admin"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Alice Example')
  })

  it('shows the password form in a cookie session', async () => {
    await mountView({ mode: 'cookie' })

    expect(wrapper.find('#account-current-password').exists()).toBe(true)
  })

  it('hides the password form in a token session', async () => {
    await mountView({ mode: 'token' })

    expect(wrapper.find('#account-current-password').exists(), 'a token session has no password').toBe(false)
    expect(wrapper.text()).toContain('signed in with an access token')
  })

  it('shows no banner when no new password is required', async () => {
    await mountView()

    expect(wrapper.text()).not.toContain('Set a new password before you continue')
  })

  it('shows the banner and puts the password card at the top when a new password is required', async () => {
    await mountView({ mustChange: true })

    const sections = wrapper.findAll('section').map(section => section.attributes('aria-labelledby'))

    expect(wrapper.find('[data-test="must-change"]').text()).toContain('Set a new password before you continue')
    expect(sections.indexOf('password-title'), 'the password card comes before the profile').toBeLessThan(sections.indexOf('profile-title'))
  })

  it('keeps the password card below the profile when no change is required', async () => {
    await mountView()

    const sections = wrapper.findAll('section').map(section => section.attributes('aria-labelledby'))

    expect(sections.indexOf('profile-title')).toBeLessThan(sections.indexOf('password-title'))
  })

  it('lifts the requirement once the password is changed', async () => {
    const session = await mountView({ mustChange: true })

    await wrapper.find('#account-current-password').setValue('old-password-12')
    await wrapper.find('#account-new-password').setValue('new-password-123')
    await wrapper.find('#account-confirm-password').setValue('new-password-123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(session.me.changePassword).toHaveBeenCalledTimes(1)
    expect(session.mustChangePassword.value, 'other pages open again').toBe(false)
    expect(wrapper.find('[data-test="must-change"]').exists()).toBe(false)
  })

  it('lists the tokens of the signed in user through the self service endpoints', async () => {
    const session = await mountView()

    expect(session.me.tokens.list).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('My tokens')
  })
})
