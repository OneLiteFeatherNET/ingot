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

import { describe, it, expect, afterEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import ConnectStep from './ConnectStep.vue'
import { ApiProblem } from '../../store/migration/migrationApi'
import { fakeApi, mountWith, cleanup, okConnection } from './testing'

const URL_VALUE = 'https://nexus.example.test'
const USER = 'importer'
const PASSWORD = 'literal-pw-7731'

afterEach(cleanup)

const fill = async (wrapper, { url = URL_VALUE, user = USER, password = PASSWORD } = {}) => {
  await wrapper.find('#migration-connect-url').setValue(url)
  await wrapper.find('#migration-connect-user').setValue(user)
  if (password !== null) await wrapper.find('#migration-connect-password').setValue(password)
}

const submit = async (wrapper) => {
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('the Connect step', () => {
  it('sends the literal password in the body of the connection test', async () => {
    const { wrapper, api } = await mountWith(ConnectStep)
    await fill(wrapper)

    await submit(wrapper)

    expect(api.testConnection).toHaveBeenCalledWith({ url: URL_VALUE, user: USER, password: PASSWORD })
  })

  it('keeps the password out of the address, the storage and the page after the test', async () => {
    const { wrapper, router } = await mountWith(ConnectStep)
    await fill(wrapper)

    await submit(wrapper)

    expect(router.currentRoute.value.fullPath, 'the address carries no password').not.toContain(PASSWORD)
    expect(window.localStorage.length, 'nothing is written to local storage').toBe(0)
    expect(window.sessionStorage.length, 'nothing is written to session storage').toBe(0)
    expect(wrapper.html(), 'the password is not rendered').not.toContain(PASSWORD)
  })

  it('empties the password field and says what the credential may read after a successful test', async () => {
    const { wrapper } = await mountWith(ConnectStep)
    await fill(wrapper)

    await submit(wrapper)

    expect(wrapper.find('#migration-connect-password').element.value, 'the password field is empty').toBe('')
    expect(wrapper.text()).toContain('3.70.1')
    expect(wrapper.text(), 'the readable areas are named').toMatch(/list repositories/i)
  })

  it('stores the connection in the page without the password', async () => {
    const { wrapper, wizard } = await mountWith(ConnectStep)
    await fill(wrapper)

    await submit(wrapper)

    expect(wizard.state.connection).toEqual(expect.objectContaining({ url: URL_VALUE, user: USER, passwordMode: 'literal' }))
    expect(JSON.stringify(wizard.state.connection)).not.toContain(PASSWORD)
    expect(wizard.passwords.recall(), 'the password is held in memory while the wizard is open').toBe(PASSWORD)
  })

  it('accepts a secret reference as text and sends it as the password', async () => {
    const { wrapper, api } = await mountWith(ConnectStep)
    const referenceChoice = wrapper.findAll('label').find(entry => entry.text().includes('Use a secret reference'))
    await referenceChoice.find('input').setValue(true)
    await fill(wrapper, { password: null })
    await wrapper.find('#migration-connect-reference').setValue('${env:NEXUS_PASSWORD}')

    await submit(wrapper)

    expect(api.testConnection).toHaveBeenCalledWith({ url: URL_VALUE, user: USER, password: '${env:NEXUS_PASSWORD}' })
  })

  it('offers no control for the trust of the certificate', async () => {
    const { wrapper } = await mountWith(ConnectStep)

    const control = wrapper.find('input[id*="tls" i], input[name*="tls" i], input[id*="trust" i], input[name*="trust" i], input[id*="insecure" i]')

    expect(control.exists(), 'TLS trust is the JVM trust store, never a page choice').toBe(false)
  })

  it('names INGOT_EXTRA_CA_CERTS when the certificate chain is not trusted', async () => {
    const api = fakeApi({
      testConnection: async () => okConnection({ reachable: false, statusCode: null, problem: 'PKIX path building failed: unable to find valid certification path' }),
    })
    const { wrapper } = await mountWith(ConnectStep, { api })
    await fill(wrapper)

    await submit(wrapper)

    expect(wrapper.text()).toContain('INGOT_EXTRA_CA_CERTS')
  })

  it('shows the title and the detail of a problem beside the button and keeps the entered values', async () => {
    const api = fakeApi({
      testConnection: async () => {
        throw new ApiProblem({ status: 400, title: 'Bad Request', detail: 'The URL carries user information' })
      },
    })
    const { wrapper } = await mountWith(ConnectStep, { api })
    await fill(wrapper)

    await submit(wrapper)

    expect(wrapper.text()).toContain('Bad Request')
    expect(wrapper.text()).toContain('The URL carries user information')
    expect(wrapper.find('#migration-connect-url').element.value, 'the URL is kept').toBe(URL_VALUE)
  })

  it('stores no connection after a failed test', async () => {
    const api = fakeApi({
      testConnection: async () => { throw new ApiProblem({ status: 403, title: 'Forbidden', detail: 'refused' }) },
    })
    const { wrapper, wizard } = await mountWith(ConnectStep, { api })
    await fill(wrapper)

    await submit(wrapper)

    expect(wizard.state.connection).toBeNull()
  })

  it('asks for the URL, the user name and the password before it calls anything', async () => {
    const { wrapper, api } = await mountWith(ConnectStep)

    await submit(wrapper)

    expect(api.testConnection, 'no call without the form').not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Enter the Nexus URL')
  })
})
