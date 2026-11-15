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
import SecurityPanel from './SecurityPanel.vue'
import { authenticationClient, authenticationStore } from './authenticationTestKit'
import { configurationStoreKey } from '../../store/configuration'

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))

vi.mock('../../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../helpers/toast')>()),
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn()
}))

describe('SecurityPanel', () => {
  let wrapper: VueWrapper | undefined
  let update: Mock

  const mountPanel = async () => {
    const store = authenticationStore(authenticationClient({ update }))
    wrapper = mount(SecurityPanel, {
      attachTo: document.body,
      global: { provide: { [configurationStoreKey]: store } }
    })
    await flushPromises()
  }

  const inputWithValue = (value: string) => wrapper!.findAll('input').find(input => input.element.value === value)

  beforeEach(() => {
    update = vi.fn(() => Promise.resolve({ data: {} }))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('renders the brute force protection fields of the authentication domain', async () => {
    await mountPanel()

    const text = wrapper!.text()
    for (const label of ['Enabled', 'Max Attempts', 'Ban Duration Seconds']) {
      expect(text, `the field "${label}"`).toContain(label)
    }
    expect(inputWithValue('10'), 'the stored maximum of attempts').toBeDefined()
    expect(inputWithValue('300'), 'the stored ban duration').toBeDefined()
  })

  it('does not render the LDAP fields, which belong to the Maven and Gradle clients', async () => {
    await mountPanel()

    expect(wrapper!.text()).not.toContain('Search-User DN')
  })

  it('saves the edited value through the authentication domain, with the rest of the domain unchanged', async () => {
    await mountPanel()
    await inputWithValue('10')!.setValue('25')

    await wrapper!.findAll('button').find(button => button.text() === 'Save security settings')!.trigger('click')
    await flushPromises()

    expect(update).toHaveBeenCalledTimes(1)
    const [domain, body, etag] = update.mock.calls[0] as [string, { bruteForceProtection: unknown, ldap: { hostname: string } }, string]
    expect(domain, 'the write goes to the authentication domain').toBe('authentication')
    expect(body.bruteForceProtection).toEqual({ enabled: false, maxAttempts: 25, banDurationSeconds: 300 })
    expect(body.ldap.hostname, 'the LDAP group is sent back as it was read').toBe('ldap.domain.com')
    expect(etag, 'the write carries the tag it was read with').toBe('"v1"')
  })

  it('says that the form holds changes that are not saved yet', async () => {
    await mountPanel()
    expect(wrapper!.text()).not.toContain('You have unsaved changes.')

    await inputWithValue('10')!.setValue('25')

    expect(wrapper!.text()).toContain('You have unsaved changes.')
  })
})
