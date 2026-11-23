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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AuthenticationGroupForm from './AuthenticationGroupForm.vue'
import { authenticationClient, authenticationStore } from './authenticationTestKit'
import { configurationStoreKey } from '../../store/configuration'

// The configuration store reads the session when it is imported.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))

vi.mock('../../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn()
}))

const rejection = (status: number) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: {} } })

describe('AuthenticationGroupForm states', () => {
  let wrapper: VueWrapper | undefined

  const mountForm = async (client: ReturnType<typeof authenticationClient>, group = 'ldap') => {
    wrapper = mount(AuthenticationGroupForm, {
      props: { group },
      attachTo: document.body,
      global: { provide: { [configurationStoreKey]: authenticationStore(client) } }
    }) as VueWrapper
    await flushPromises()
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('shows skeleton lines in a busy panel while the settings load', async () => {
    const client = authenticationClient()
    client.settings.domains = () => new Promise(() => {})
    await mountForm(client)

    const panel = wrapper!.get('[aria-busy="true"]')
    expect(panel.findAll('[aria-hidden="true"]').length, 'the loading panel draws skeleton lines').toBeGreaterThan(0)
    expect(wrapper!.find('form').exists(), 'no form is drawn before the settings arrive').toBe(false)
  })

  it('shows a failed load as an alert with the reason and a tonal Retry', async () => {
    const client = authenticationClient()
    client.settings.domains = () => Promise.reject(rejection(503))
    await mountForm(client)

    const panel = wrapper!.get('[role="alert"]')
    expect(panel.text(), 'the panel names the failed settings').toContain('Could not load the authentication settings')
    expect(panel.text(), 'the panel gives the reason').toContain('Cannot load configuration')
    const retry = panel.findAll('button').find(button => button.text() === 'Retry')!
    expect(retry.classes(), 'Retry is a tonal button').toContain('bg-secondary-container')
  })

  it('loads the settings again on Retry and draws the form', async () => {
    const client = authenticationClient()
    const domains = vi.fn()
      .mockRejectedValueOnce(rejection(503))
      .mockResolvedValue({ data: ['authentication'] })
    client.settings.domains = domains
    await mountForm(client)

    await wrapper!.get('[role="alert"]').findAll('button').find(button => button.text() === 'Retry')!.trigger('click')
    await flushPromises()

    expect(domains, 'Retry asks the server again').toHaveBeenCalledTimes(2)
    expect(wrapper!.find('[role="alert"]').exists(), 'the error is gone after the retry').toBe(false)
    expect(wrapper!.text(), 'the form is drawn').toContain('Save')
  })

  it('says so when the server has no such group of settings', async () => {
    await mountForm(authenticationClient(), 'missingGroup')

    expect(wrapper!.text()).toContain('These settings are not available on this server.')
  })
})
