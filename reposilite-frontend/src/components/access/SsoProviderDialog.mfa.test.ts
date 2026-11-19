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
  adminGroups: [],
  mfaAcrValues: ['urn:example:loa:mfa'],
  mfaAmrValues: ['mfa']
}

let wrapper: VueWrapper | undefined

// The modal teleports to the body and mounts its content after a tick, so the test waits for it.
const openDialog = async (provider: OidcProvider = KEYCLOAK): Promise<void> => {
  wrapper = mount(SsoProviderDialog, {
    attachTo: document.body,
    props: { modelValue: true, provider, takenIds: [], productionUrl: 'http://localhost:3000/' }
  })
  await flushPromises()
}

// The chips of one trusted list, read from the list the chip input labels with its field name.
const chipsOf = (label: string): string[] =>
  [...document.querySelectorAll<HTMLElement>(`ul[aria-label="${label} chips"] [data-test="chip"]`)]
    .map(chip => chip.textContent.trim())

describe('SsoProviderDialog trusted second factor values', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('shows the stored acr values of the provider as chips', async () => {
    await openDialog()

    expect(chipsOf('Trusted ACR values'), 'the stored acr value is shown as a chip').toEqual(['urn:example:loa:mfa'])
  })

  it('shows the stored amr values of the provider as chips', async () => {
    await openDialog()

    expect(chipsOf('Trusted AMR values'), 'the stored amr value is shown as a chip').toEqual(['mfa'])
  })

  it('saves an acr value that was typed into the field', async () => {
    await openDialog()

    const input = document.getElementById('sso-mfa-acr') as HTMLInputElement
    input.value = 'urn:example:loa:otp,'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    document.querySelector<HTMLFormElement>('#sso-provider-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    const saved = wrapper!.emitted('save')![0][0] as { provider: OidcProvider }
    expect(saved.provider.mfaAcrValues, 'the typed acr value is part of the saved provider').toEqual(['urn:example:loa:mfa', 'urn:example:loa:otp'])
  })

  it('draws an empty trusted list for a provider saved before the lists existed', async () => {
    const older: Partial<OidcProvider> = { ...KEYCLOAK }
    delete older.mfaAcrValues
    delete older.mfaAmrValues
    await openDialog(older as OidcProvider)

    expect(chipsOf('Trusted ACR values'), 'no acr value is trusted without a stored one').toEqual([])
    expect(chipsOf('Trusted AMR values'), 'no amr value is trusted without a stored one').toEqual([])
  })
})
