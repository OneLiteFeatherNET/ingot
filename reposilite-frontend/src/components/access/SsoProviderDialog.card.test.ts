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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import SsoProviderDialog from './SsoProviderDialog.vue'
import { SECRET_PLACEHOLDER, type OidcProvider } from './loginSettings'

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
  mfaAcrValues: [],
  mfaAmrValues: []
}

let wrapper: VueWrapper | undefined

// The modal teleports to the body and mounts its content after a tick, so the test waits for it.
const openDialog = async (): Promise<void> => {
  wrapper = mount(SsoProviderDialog, {
    attachTo: document.body,
    props: { modelValue: true, provider: KEYCLOAK, takenIds: [], productionUrl: 'http://localhost:3000/' }
  })
  await flushPromises()
}

const registerSection = (): HTMLElement | null =>
  document.querySelector<HTMLElement>('[aria-labelledby="sso-register-title"]')

describe('SsoProviderDialog register card', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('draws the provider registration as a shared card', async () => {
    await openDialog()

    const section = registerSection()
    expect(section, 'the registration section is shown for an existing provider').not.toBeNull()
    expect(section?.dataset.ingotComponent, 'the registration section is an md-card').toBe('md-card')
    expect(section?.tagName, 'the card keeps the section element and its label').toBe('SECTION')
  })

  it('keeps the callback URL and its copy button inside the card', async () => {
    await openDialog()

    const section = registerSection()
    expect(section?.querySelector('[data-test="callback-url"]'), 'the callback URL sits in the card').not.toBeNull()
    expect(section?.querySelector('[aria-label="Copy callback URL"]'), 'the copy button sits in the card').not.toBeNull()
  })
})
