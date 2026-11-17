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
import { createErrorToast, createSuccessToast } from '../../helpers/toast'

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
  adminGroups: ['ingot-admins']
}

const CALLBACK_URL = 'http://localhost:3000/api/auth/oidc/keycloak/callback'

let wrapper: VueWrapper | undefined

// The modal teleports to the body and mounts its content after a tick, so the test waits for it.
const openDialog = async (): Promise<void> => {
  wrapper = mount(SsoProviderDialog, {
    attachTo: document.body,
    props: { modelValue: true, provider: KEYCLOAK, takenIds: [], productionUrl: 'http://localhost:3000/' }
  })
  await flushPromises()
}

const copyButton = (): HTMLElement =>
  document.querySelector<HTMLElement>('[aria-label="Copy callback URL"]')!

describe('SsoProviderDialog copy of the callback URL', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('does not report a copy when the clipboard API is missing', async () => {
    vi.stubGlobal('navigator', { clipboard: undefined })
    await openDialog()

    copyButton().click()
    await flushPromises()

    expect(createSuccessToast, 'no copied message is shown when nothing reached the clipboard').not.toHaveBeenCalled()
    expect(createErrorToast, 'the user is told the copy failed').toHaveBeenCalledWith(
      expect.stringMatching(/could not copy the callback url/i)
    )
  })

  it('reports the copy and writes the callback URL when the clipboard works', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await openDialog()

    copyButton().click()
    await flushPromises()

    expect(writeText, 'the callback URL is written to the clipboard').toHaveBeenCalledWith(CALLBACK_URL)
    expect(createSuccessToast, 'the success message is still shown').toHaveBeenCalledWith('Callback URL copied')
    expect(createErrorToast, 'no failure message is shown after a copy').not.toHaveBeenCalled()
  })
})
