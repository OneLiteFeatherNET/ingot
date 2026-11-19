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
import SsoProviderDialog from './SsoProviderDialog.vue'
import { SECRET_PLACEHOLDER, type OidcProvider } from './loginSettings'

vi.mock('../../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn()
}))

const KEYCLOAK: OidcProvider = {
  id: 'keycloak',
  enabled: true,
  label: 'Keycloak',
  icon: 'KEYCLOAK',
  issuerUrl: 'https://sso.example.org/realms/ingot',
  clientId: 'ingot',
  clientSecret: SECRET_PLACEHOLDER,
  scopes: 'openid profile email',
  usernameClaim: 'preferred_username',
  displayNameClaim: 'name',
  emailClaim: 'email',
  groupsClaim: 'groups',
  allowedEmailDomains: [],
  autoCreateUsers: true,
  adminGroups: ['ingot-admins'],
  mfaAcrValues: [],
  mfaAmrValues: []
}

let wrapper: VueWrapper | undefined

// The modal teleports to the body and mounts its content after a tick, so the test waits for it.
const openForm = async (): Promise<HTMLElement> => {
  wrapper = mount(SsoProviderDialog, {
    attachTo: document.body,
    props: { modelValue: true, provider: KEYCLOAK, takenIds: [], productionUrl: 'http://localhost:3000/' }
  })
  await flushPromises()
  return document.querySelector<HTMLElement>('#sso-provider-form')!
}

// The visible names of the controls in the order they are drawn: each field label, and the callback URL.
const fieldNames = (form: HTMLElement): string[] =>
  [...form.querySelectorAll<HTMLElement>('label, [aria-label="Callback URL"]')]
    .map(element => element.getAttribute('aria-label') ?? element.textContent.trim())

describe('SsoProviderDialog field order', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('asks for the id and the label before the issuer', async () => {
    const form = await openForm()

    const names = fieldNames(form)
    expect(names.indexOf('Id'), 'the id comes first').toBe(0)
    expect(names.indexOf('Label on the sign in button'), 'the label follows the id').toBe(1)
  })

  it('shows the callback URL after the issuer it is registered with, so the id it is built from comes first', async () => {
    const form = await openForm()

    const names = fieldNames(form)
    expect(names.indexOf('Issuer URL'), 'the issuer is asked before the callback URL is shown').toBeLessThan(names.indexOf('Callback URL'))
    expect(names.indexOf('Callback URL'), 'the callback URL comes before the client details it is registered for').toBeLessThan(names.indexOf('Client id'))
  })

  it('lists every field in the order the sign in is filled in', async () => {
    const form = await openForm()

    expect(fieldNames(form)).toEqual([
      'Id',
      'Label on the sign in button',
      'Issuer URL',
      'Callback URL',
      'Client id',
      'Client secret',
      'Scopes',
      'Allowed email domains',
      'Admin groups',
      'Create an account at the first sign in',
      'Username claim',
      'Display name claim',
      'Email claim',
      'Groups claim',
      'Trusted ACR values',
      'Trusted AMR values',
      'Enabled: offer this provider at sign in'
    ])
  })
})
