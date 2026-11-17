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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import SecurityPanel from './SecurityPanel.vue'
import { signInClient, signInStore } from './signInTestKit'
import { configurationStoreKey } from '../../store/configuration'

// The status of the instance decides whether the account throttle switch reads On or Off. Each test sets it.
const status = vi.hoisted(() => ({ instance: vi.fn() }))

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: { status: { instance: status.instance } } } }) }))

vi.mock('../../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../helpers/toast')>()),
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn()
}))

const BLANK = { render: () => null }

describe('SecurityPanel', () => {
  let wrapper: VueWrapper | undefined
  let update: Mock

  // Mounts the panel at an address, with a router of its own, so the tab in the address is read per test.
  const mountPanel = async (address = '/access/security') => {
    const router: Router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/access/security', name: 'access-security', component: BLANK }]
    })
    await router.push(address)
    await router.isReady()
    const store = signInStore(signInClient({ update }))
    wrapper = mount(SecurityPanel, {
      attachTo: document.body,
      global: {
        plugins: [router],
        provide: { [configurationStoreKey]: store }
      }
    }) as VueWrapper
    await flushPromises()
    return router
  }

  const tabLabels = () => wrapper!.findAll('[role="tab"]').map(tab => tab.text())
  const selectedTab = () => wrapper!.find('[role="tab"][aria-selected="true"]').text()
  const openTab = async (label: string) => {
    await wrapper!.findAll('[role="tab"]').find(tab => tab.text() === label)!.trigger('click')
    await flushPromises()
  }
  const buttonNamed = (label: string) => wrapper!.findAll('button').find(button => button.text() === label)
  const inputWithValue = (value: string) => wrapper!.findAll('input').find(input => input.element.value === value)

  beforeEach(() => {
    update = vi.fn(() => Promise.resolve({ data: {} }))
    status.instance.mockResolvedValue({ data: {} })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
    status.instance.mockReset()
  })

  describe('tabs', () => {
    it('shows the four tabs in order: IP addresses, Accounts, Passwords and Sessions', async () => {
      await mountPanel()

      expect(tabLabels()).toEqual(['IP addresses', 'Accounts', 'Passwords', 'Sessions'])
    })

    it('opens the IP addresses tab when the address names no tab', async () => {
      await mountPanel()

      expect(selectedTab()).toBe('IP addresses')
    })

    it('opens the tab named by the tab query parameter', async () => {
      await mountPanel('/access/security?tab=passwords')

      expect(selectedTab()).toBe('Passwords')
      expect(wrapper!.text()).toContain('Minimum Length')
    })

    it('falls back to the IP addresses tab for an unknown tab name', async () => {
      await mountPanel('/access/security?tab=nope')

      expect(selectedTab()).toBe('IP addresses')
      expect(wrapper!.text()).toContain('Max Attempts')
    })

    it('writes the chosen tab into the address', async () => {
      const router = await mountPanel()

      await openTab('Sessions')

      expect(router.currentRoute.value.query.tab).toBe('sessions')
    })
  })

  describe('fields of each tab', () => {
    it('the IP addresses tab draws the brute force protection fields of the authentication domain', async () => {
      await mountPanel()

      const text = wrapper!.text()
      for (const label of ['Enabled', 'Max Attempts', 'Ban Duration Seconds']) {
        expect(text, `the field "${label}"`).toContain(label)
      }
      expect(inputWithValue('10'), 'the stored maximum of attempts').toBeDefined()
      expect(inputWithValue('300'), 'the stored ban duration').toBeDefined()
    })

    it('the IP addresses tab does not render the LDAP fields, which belong to the Maven and Gradle clients', async () => {
      await mountPanel()

      expect(wrapper!.text()).not.toContain('Search-User DN')
    })

    it('the Accounts tab draws the dashboard lockout and the account throttle fields', async () => {
      await mountPanel('/access/security?tab=accounts')

      const text = wrapper!.text()
      for (const label of ['Maximum Failed Attempts', 'Lockout Minutes', 'Threshold', 'Quiet Minutes', 'Maximum Delay Seconds', 'Known Address Days']) {
        expect(text, `the field "${label}"`).toContain(label)
      }
      expect(text).not.toContain('Minimum Length')
    })

    it('the Passwords tab draws the minimum length and the breached password check', async () => {
      await mountPanel('/access/security?tab=passwords')

      const text = wrapper!.text()
      for (const label of ['Minimum Length', 'Mode', 'File', 'Range URL', 'Fail Closed']) {
        expect(text, `the field "${label}"`).toContain(label)
      }
      expect(text).not.toContain('Max Attempts')
    })

    it('the Sessions tab draws the idle timeout', async () => {
      await mountPanel('/access/security?tab=sessions')

      expect(wrapper!.text()).toContain('Idle Timeout Minutes')
      expect(wrapper!.text()).not.toContain('Minimum Length')
    })
  })

  describe('saving', () => {
    it('each tab names the settings domain its save button writes', async () => {
      await mountPanel()
      expect(buttonNamed('Save IP address limits'), 'IP addresses').toBeDefined()

      await openTab('Accounts')
      expect(buttonNamed('Save account limits'), 'Accounts').toBeDefined()

      await openTab('Passwords')
      expect(buttonNamed('Save password rules'), 'Passwords').toBeDefined()

      await openTab('Sessions')
      expect(buttonNamed('Save session limits'), 'Sessions').toBeDefined()
    })

    it('the IP addresses save writes the authentication domain with the LDAP group unchanged', async () => {
      await mountPanel()
      await inputWithValue('10')!.setValue('25')

      await buttonNamed('Save IP address limits')!.trigger('click')
      await flushPromises()

      expect(update).toHaveBeenCalledTimes(1)
      const [domain, body, etag] = update.mock.calls[0] as unknown as [string, { bruteForceProtection: unknown, ldap: { hostname: string } }, string]
      expect(domain, 'the write goes to the authentication domain').toBe('authentication')
      expect(body.bruteForceProtection).toEqual({ enabled: false, maxAttempts: 25, banDurationSeconds: 300 })
      expect(body.ldap.hostname, 'the LDAP group is sent back as it was read').toBe('ldap.domain.com')
      expect(etag, 'the write carries the tag it was read with').toBe('"v1"')
    })

    it('the Passwords save writes the login domain with the new minimum length and the rest unchanged', async () => {
      await mountPanel('/access/security?tab=passwords')
      await inputWithValue('12')!.setValue('15')

      await buttonNamed('Save password rules')!.trigger('click')
      await flushPromises()

      expect(update).toHaveBeenCalledTimes(1)
      const [domain, body] = update.mock.calls[0] as unknown as [string, { passwordPolicy: { minLength: number }, accountThrottle: { threshold: number } }]
      expect(domain, 'the write goes to the login domain').toBe('login')
      expect(body.passwordPolicy.minLength).toBe(15)
      expect(body.accountThrottle.threshold, 'the account throttle is sent back as it was read').toBe(10)
    })

    it('says that the form holds changes that are not saved yet', async () => {
      await mountPanel()
      expect(wrapper!.text()).not.toContain('You have unsaved changes.')

      await inputWithValue('10')!.setValue('25')

      expect(wrapper!.text()).toContain('You have unsaved changes.')
    })
  })

  describe('settings that only the environment sets', () => {
    it('the Accounts tab names the environment variable of the account throttle switch', async () => {
      await mountPanel('/access/security?tab=accounts')

      const notice = wrapper!.find('[data-test="account-throttle-switch"]')
      expect(notice.exists(), 'the switch notice').toBe(true)
      expect(notice.text()).toContain('INGOT_LOGIN_ACCOUNT_THROTTLE')
      expect(notice.text(), 'the fallback variable').toContain('REPOSILITE_LOGIN_ACCOUNT_THROTTLE')
    })

    it('the switch reads On when the instance reports the sign-in counters', async () => {
      status.instance.mockResolvedValue({ data: { signIn: {} } })
      await mountPanel('/access/security?tab=accounts')

      expect(wrapper!.find('[data-test="account-throttle-switch"]').text()).toContain('On')
    })

    it('the switch reads Off when the instance reports no sign-in counters', async () => {
      await mountPanel('/access/security?tab=accounts')

      expect(wrapper!.find('[data-test="account-throttle-switch"]').text()).toContain('Off')
    })

    it('the switch is read-only: it has no input of its own', async () => {
      await mountPanel('/access/security?tab=accounts')

      expect(wrapper!.find('[data-test="account-throttle-switch"]').find('input').exists()).toBe(false)
    })
  })
})
