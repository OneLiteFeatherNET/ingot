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
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { siGithub, siKeycloak } from 'simple-icons'
import SsoProvidersPanel from './SsoProvidersPanel.vue'
import { SECRET_PLACEHOLDER } from './loginSettings'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn()
}))

const KEYCLOAK = {
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

const GITHUB = { ...KEYCLOAK, id: 'github', label: 'GitHub', icon: 'GITHUB', issuerUrl: 'https://github.example.org', adminGroups: [] }

const LDAP = {
  enabled: false, label: 'LDAP', url: '', startTls: false, bindDn: '', bindPassword: '', userBaseDn: '',
  userFilter: '(uid={0})', usernameAttribute: 'uid', displayNameAttribute: 'cn', emailAttribute: 'mail',
  groupBaseDn: '', groupFilter: '(objectClass=groupOfNames)', groupNameAttribute: 'cn', autoCreateUsers: true, adminGroups: []
}

const answer = (data, etag = '"v1"') => ({ data, headers: { etag } })
const rejection = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: {} } })

const dialog = () => document.querySelector('[role="dialog"]')
const field = (id) => document.getElementById(id)
const buttonIn = (root, label) => [...root.querySelectorAll('button')].find(button => button.textContent.trim() === label)
const dialogButton = (label) => buttonIn(dialog(), label)

const type = (id, value) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
}

describe('SsoProvidersPanel', () => {
  let wrapper
  let settings
  let auth

  const mountPanel = async () => {
    wrapper = mount(SsoProvidersPanel, { attachTo: document.body })
    await flushPromises()
  }

  const rows = () => wrapper.findAll('[data-test="sso-row"]')
  const rowOf = (label) => rows().find(row => row.text().includes(label))

  // Opens the add dialog and picks a kind, which is what the admin does first.
  const pickKind = async (label) => {
    wrapper.find('[data-test="add-provider"]').trigger('click')
    await flushPromises()
    dialogButton(label).click()
    await flushPromises()
  }

  const fillClientDetails = () => {
    type('sso-issuer', 'https://gitlab.example.org')
    type('sso-client-id', 'ingot-client')
    type('sso-client-secret', 's3cret')
  }

  beforeEach(() => {
    settings = {
      fetch: vi.fn().mockResolvedValue(answer({ oidcProviders: [KEYCLOAK, GITHUB], ldap: LDAP })),
      update: vi.fn().mockResolvedValue({})
    }
    auth = {
      testOidcProvider: vi.fn().mockResolvedValue({ data: { ok: true, issuer: 'https://sso.example.org/realms/ingot', endpoints: { authorization: 'https://sso.example.org/auth' } } })
    }
    holder.session = { client: ref({ settings, auth }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('the list', () => {
    it('reads the login domain and lists each provider with its label and issuer', async () => {
      await mountPanel()

      expect(settings.fetch).toHaveBeenCalledWith('login')
      expect(rows().map(row => row.find('p').text())).toEqual(['Keycloak', 'GitHub'])
      expect(rowOf('Keycloak').text()).toContain('https://sso.example.org/realms/ingot')
    })

    it('draws the mark of each provider from its icon', async () => {
      await mountPanel()

      expect(rowOf('Keycloak').find('svg path').attributes('d')).toBe(siKeycloak.path)
      expect(rowOf('GitHub').find('svg path').attributes('d')).toBe(siGithub.path)
    })

    it('says the provider has not been tested in this visit yet', async () => {
      await mountPanel()

      expect(rowOf('Keycloak').find('[data-test="status"]').text()).toBe('Not tested')
    })

    it('shows an empty state with a prompt to add when no provider exists', async () => {
      settings.fetch.mockResolvedValue(answer({ oidcProviders: [], ldap: LDAP }))

      await mountPanel()

      expect(rows()).toHaveLength(0)
      expect(wrapper.text()).toContain('No provider yet')
    })

    it('shows the reason and offers a retry when the domain cannot be read', async () => {
      settings.fetch.mockRejectedValueOnce(rejection(500))

      await mountPanel()

      expect(wrapper.find('[role="alert"]').text()).toContain('Could not load the providers')
      await wrapper.findAll('button').find(button => button.text() === 'Retry').trigger('click')
      await flushPromises()

      expect(settings.fetch).toHaveBeenCalledTimes(2)
      expect(rows()).toHaveLength(2)
    })

    it('turns a provider off with its switch and writes the list with the tag it was read with', async () => {
      await mountPanel()

      await wrapper.find('[aria-label="Enabled: Keycloak"]').trigger('click')
      await flushPromises()

      expect(settings.update).toHaveBeenCalledWith('login', expect.objectContaining({
        oidcProviders: [{ ...KEYCLOAK, enabled: false }, GITHUB],
        ldap: LDAP
      }), '"v1"')
    })
  })

  describe('adding a provider', () => {
    beforeEach(async () => {
      await mountPanel()
    })

    it('opens on the kind picker with one card per kind', async () => {
      wrapper.find('[data-test="add-provider"]').trigger('click')
      await flushPromises()

      const kinds = [...dialog().querySelectorAll('button')].map(button => button.textContent.trim())
      expect(kinds).toEqual(expect.arrayContaining(['GitHub', 'GitLab', 'Google', 'Microsoft', 'Keycloak', 'Authentik', 'Okta', 'Auth0', 'Gitea', 'Generic OIDC']))
    })

    it('prefills the label, the id and the scopes from the chosen kind', async () => {
      await pickKind('GitLab')

      expect(field('sso-label').value).toBe('GitLab')
      expect(field('sso-id').value).toBe('gitlab')
      expect(dialog().textContent).toContain('openid')
    })

    it('prefills GitHub without scopes, because the server applies its own GitHub defaults', async () => {
      await pickKind('GitHub')

      expect(dialog().textContent).toContain('Blank uses read:user user:email read:org')
      expect(dialog().textContent).toContain('Leave empty for GitHub itself')
    })

    it('shows the callback URL for the id it will have, under the dashboard origin', async () => {
      await pickKind('GitLab')

      expect(dialog().querySelector('[data-test="callback-url"]').textContent)
        .toBe('http://localhost:3000/api/auth/oidc/gitlab/callback')
    })

    it('shows the registration steps of the chosen kind', async () => {
      await pickKind('GitLab')

      expect(dialog().textContent).toContain('In GitLab, create an application')
    })

    it('copies the callback URL with a button that names it', async () => {
      const writeText = vi.fn().mockResolvedValue()
      vi.stubGlobal('navigator', { clipboard: { writeText } })
      await pickKind('GitLab')

      dialog().querySelector('[aria-label="Copy callback URL"]').click()
      await flushPromises()

      expect(writeText).toHaveBeenCalledWith('http://localhost:3000/api/auth/oidc/gitlab/callback')
      vi.unstubAllGlobals()
    })

    it('refuses an id that is not a slug and an issuer that is not https, and says why', async () => {
      await pickKind('GitLab')
      type('sso-id', 'Git Lab')
      type('sso-issuer', 'http://gitlab.example.org')

      dialogButton('Save').click()
      await flushPromises()

      expect(dialog().textContent).toContain('lowercase letters')
      expect(dialog().textContent).toContain('starting with https://')
      expect(settings.update, 'nothing is written before the form is valid').not.toHaveBeenCalled()
    })

    it('needs the client secret of a GitHub OAuth app, but no secret for a public client', async () => {
      await pickKind('GitHub')
      type('sso-client-id', 'Iv1.ingot')

      dialogButton('Save').click()
      await flushPromises()

      expect(dialog().textContent).toContain('Enter the client secret of the GitHub OAuth app')
      expect(settings.update).not.toHaveBeenCalled()
    })

    it('saves a GitLab client without a secret, because the backend accepts a public client', async () => {
      await pickKind('GitLab')
      type('sso-issuer', 'https://gitlab.example.org')
      type('sso-client-id', 'ingot-client')

      dialogButton('Save').click()
      await flushPromises()

      expect(settings.update).toHaveBeenCalledTimes(1)
    })

    it('saves the new provider with the list it was read from', async () => {
      await pickKind('GitLab')
      fillClientDetails()

      dialogButton('Save').click()
      await flushPromises()

      expect(settings.update).toHaveBeenCalledWith('login', expect.objectContaining({
        oidcProviders: [
          KEYCLOAK,
          GITHUB,
          expect.objectContaining({
            id: 'gitlab',
            label: 'GitLab',
            icon: 'GITLAB',
            issuerUrl: 'https://gitlab.example.org',
            clientId: 'ingot-client',
            clientSecret: 's3cret',
            scopes: 'openid profile email',
            enabled: false
          })
        ],
        ldap: LDAP
      }), '"v1"')
    })

    it('closes after a successful save', async () => {
      await pickKind('GitLab')
      fillClientDetails()

      dialogButton('Save').click()
      await flushPromises()

      expect(document.getElementById('sso-dialog-title'), 'a saved provider closes the dialog').toBeNull()
    })

    it('keeps the dialog open and says why when the server refuses the save', async () => {
      settings.update.mockRejectedValue(rejection(400))
      await pickKind('GitLab')
      fillClientDetails()

      dialogButton('Save').click()
      await flushPromises()

      expect(dialog(), 'the form stays open so it can be corrected').not.toBeNull()
      expect(dialog().querySelector('[role="alert"]').textContent).toBe('Not saved: Request failed with status 400')
    })

    it('says the domain changed elsewhere when the server answers with a precondition failure', async () => {
      settings.update.mockRejectedValue(rejection(412))
      await pickKind('GitLab')
      fillClientDetails()

      dialogButton('Save').click()
      await flushPromises()

      expect(dialog().querySelector('[role="alert"]').textContent).toContain('changed elsewhere')
    })
  })

  describe('editing a provider', () => {
    beforeEach(async () => {
      await mountPanel()
      wrapper.find('[aria-label="Edit Keycloak"]').trigger('click')
      await flushPromises()
    })

    it('shows the stored secret as the placeholder, and keeps it when nothing is typed', async () => {
      expect(field('sso-client-secret').value).toBe(SECRET_PLACEHOLDER)

      dialogButton('Save').click()
      await flushPromises()

      expect(settings.update.mock.calls[0][1].oidcProviders[0].clientSecret).toBe(SECRET_PLACEHOLDER)
    })

    it('sends the secret that was typed in place of the placeholder', async () => {
      type('sso-client-secret', 'rotated-secret')

      dialogButton('Save').click()
      await flushPromises()

      expect(settings.update.mock.calls[0][1].oidcProviders[0].clientSecret).toBe('rotated-secret')
    })

    it('does not let the id change, because the callback URL and team links carry it', async () => {
      expect(field('sso-id').disabled).toBe(true)
    })

    it('explains the external reference that a team takes to be synced from this provider', async () => {
      expect(dialog().textContent).toContain('oidc:keycloak:<group>')
    })

    it('keeps the claims behind an Advanced disclosure that starts closed', async () => {
      const disclosure = dialog().querySelector('details')

      expect(disclosure.open).toBe(false)
      expect(disclosure.querySelector('summary').textContent).toContain('Advanced')
    })

    it('shows the admin groups of the provider as chips', async () => {
      expect(dialog().textContent).toContain('ingot-admins')
    })

    it('deletes after the id is typed, and removes only that provider', async () => {
      dialogButton('Delete provider').click()
      await flushPromises()
      const confirm = document.querySelector('[data-test="confirm-button"]')
      expect(confirm.disabled, 'a delete needs the id typed first').toBe(true)

      document.querySelector('[data-test="confirm-typed"]').value = 'keycloak'
      document.querySelector('[data-test="confirm-typed"]').dispatchEvent(new Event('input'))
      await flushPromises()
      confirm.click()
      await flushPromises()

      expect(settings.update).toHaveBeenCalledWith('login', expect.objectContaining({
        oidcProviders: [GITHUB]
      }), '"v1"')
    })
  })

  describe('test connection', () => {
    beforeEach(async () => {
      await mountPanel()
    })

    const openKeycloak = async () => {
      wrapper.find('[aria-label="Edit Keycloak"]').trigger('click')
      await flushPromises()
    }

    const testButton = () => buttonIn(dialog(), 'Test connection')

    it('tests the saved provider and shows the issuer it reached', async () => {
      await openKeycloak()

      testButton().click()
      await flushPromises()

      expect(auth.testOidcProvider).toHaveBeenCalledWith('keycloak')
      expect(dialog().querySelector('[data-test="test-result"]').textContent)
        .toContain('Connected. Issuer https://sso.example.org/realms/ingot')
    })

    it('is offered only while the form matches the saved provider', async () => {
      await openKeycloak()
      expect(testButton().disabled).toBe(false)

      type('sso-label', 'Keycloak (test)')
      await flushPromises()

      expect(testButton().disabled, 'an unsaved change is not what the test would read').toBe(true)
      expect(dialog().textContent).toContain('Save your changes first')
    })

    it('is not offered for a provider that is not saved yet', async () => {
      await pickKind('GitLab')

      expect(testButton().disabled).toBe(true)
      expect(dialog().textContent).toContain('Save the provider first')
    })

    it('shows the reason when the test fails', async () => {
      auth.testOidcProvider.mockResolvedValue({ data: { ok: false, error: 'Discovery document not found' } })
      await openKeycloak()

      testButton().click()
      await flushPromises()

      expect(dialog().querySelector('[data-test="test-result"]').textContent)
        .toContain('The test failed: Discovery document not found')
      expect(dialog().querySelector('[data-test="test-result"]').classList).toContain('text-error')
    })

    it('shows the failure of the request itself as a failed test', async () => {
      auth.testOidcProvider.mockRejectedValue(rejection(503))
      await openKeycloak()

      testButton().click()
      await flushPromises()

      expect(dialog().querySelector('[data-test="test-result"]').textContent)
        .toContain('The test failed: Request failed with status 503')
    })

    it('shows the last test on the row of the provider', async () => {
      await openKeycloak()
      testButton().click()
      await flushPromises()
      buttonIn(dialog(), 'Cancel').click()
      await flushPromises()

      expect(rowOf('Keycloak').find('[data-test="status"]').text()).toBe('Connected')
    })
  })

  describe('public address', () => {
    const preview = () => wrapper.find('[data-test="callback-preview"]').text()
    const savePublic = async () => {
      buttonIn(wrapper.element, 'Save public address').click()
      await flushPromises()
    }

    it('previews the callback under the dashboard origin while no public address is set', async () => {
      await mountPanel()

      expect(preview()).toContain('http://localhost:3000/api/auth/oidc/<id>/callback')
    })

    it('builds the callback of a provider from the saved public address', async () => {
      settings.fetch.mockResolvedValue(answer({ oidcProviders: [KEYCLOAK, GITHUB], ldap: LDAP, publicUrl: 'https://repo.example.org' }))
      await mountPanel()
      wrapper.find('[data-test="add-provider"]').trigger('click')
      await flushPromises()
      dialogButton('GitLab').click()
      await flushPromises()

      expect(dialog().querySelector('[data-test="callback-url"]').textContent)
        .toBe('https://repo.example.org/api/auth/oidc/gitlab/callback')
    })

    it('refuses an http address of a real host, and saves nothing', async () => {
      await mountPanel()
      type('sso-public-url', 'http://repo.example.org')
      await flushPromises()

      expect(wrapper.text()).toContain('Enter an absolute https:// address')
      expect(preview()).toContain('Fix the public URL to see the callback address')
      await savePublic()
      expect(settings.update).not.toHaveBeenCalled()
    })

    it('saves the public address with the rest of the domain unchanged', async () => {
      await mountPanel()
      type('sso-public-url', 'https://repo.example.org/')
      await flushPromises()
      expect(preview()).toContain('https://repo.example.org/api/auth/oidc/<id>/callback')

      await savePublic()

      expect(settings.update).toHaveBeenCalledWith('login', expect.objectContaining({
        publicUrl: 'https://repo.example.org/',
        oidcProviders: [KEYCLOAK, GITHUB],
        ldap: LDAP
      }), '"v1"')
    })
  })
})
