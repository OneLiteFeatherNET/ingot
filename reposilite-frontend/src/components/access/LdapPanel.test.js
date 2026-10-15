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
import LdapPanel from './LdapPanel.vue'
import { SECRET_PLACEHOLDER } from './loginSettings'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', async (importOriginal) => ({
  ...(await importOriginal()),
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn()
}))

const OIDC = [{ id: 'keycloak', enabled: true, label: 'Keycloak', icon: 'KEYCLOAK', issuerUrl: 'https://sso.example.org/realms/ingot', clientId: 'ingot', clientSecret: SECRET_PLACEHOLDER, scopes: ['openid'], usernameClaim: 'preferred_username', displayNameClaim: 'name', emailClaim: 'email', groupsClaim: 'groups', allowedEmailDomains: [], autoCreateUsers: true, adminGroups: [] }]

const LDAP = {
  enabled: true,
  label: 'Corporate LDAP',
  url: 'ldaps://ldap.example.org:636',
  startTls: false,
  bindDn: 'cn=svc,dc=example,dc=org',
  bindPassword: SECRET_PLACEHOLDER,
  userBaseDn: 'ou=people,dc=example,dc=org',
  userFilter: '(uid={username})',
  usernameAttribute: 'uid',
  displayNameAttribute: 'cn',
  emailAttribute: 'mail',
  groupBaseDn: 'ou=groups,dc=example,dc=org',
  groupFilter: '(member={dn})',
  groupNameAttribute: 'cn',
  autoCreateUsers: true,
  adminGroups: ['ingot-admins']
}

const answer = (data, etag = '"v1"') => ({ data, headers: { etag } })
const rejection = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: {} } })

const field = (id) => document.getElementById(id)
const type = (id, value) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
}
const buttonNamed = (wrapper, label) => wrapper.findAll('button').find(button => button.text() === label)

describe('LdapPanel', () => {
  let wrapper
  let settings
  let auth

  const mountPanel = async () => {
    wrapper = mount(LdapPanel, { attachTo: document.body })
    await flushPromises()
  }

  const save = async () => {
    wrapper.find('#ldap-form').trigger('submit')
    await flushPromises()
  }

  beforeEach(() => {
    settings = {
      fetch: vi.fn().mockResolvedValue(answer({ oidcProviders: OIDC, ldap: LDAP })),
      update: vi.fn().mockResolvedValue({})
    }
    auth = {
      testLdapProvider: vi.fn().mockResolvedValue({ data: { ok: true, userDn: 'uid=ldapuser,ou=people,dc=example,dc=org', groups: ['ingot-admins', 'staff'] } })
    }
    holder.session = { client: ref({ settings, auth }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('the form', () => {
    it('reads the login domain and fills the form from the stored directory', async () => {
      await mountPanel()

      expect(settings.fetch).toHaveBeenCalledWith('login')
      expect(field('ldap-label').value).toBe('Corporate LDAP')
      expect(field('ldap-url').value).toBe('ldaps://ldap.example.org:636')
      expect(field('ldap-user-filter').value).toBe('(uid={username})')
    })

    it('shows the stored bind password as the placeholder', async () => {
      await mountPanel()

      expect(field('ldap-bind-password').value).toBe(SECRET_PLACEHOLDER)
    })

    it('saves the edited directory and keeps the other domain parts and the tag it was read with', async () => {
      await mountPanel()
      type('ldap-user-base', 'ou=staff,dc=example,dc=org')

      await save()

      expect(settings.update).toHaveBeenCalledWith('login', {
        publicUrl: '',
        oidcProviders: OIDC,
        ldap: { ...LDAP, userBaseDn: 'ou=staff,dc=example,dc=org' }
      }, '"v1"')
    })

    it('keeps the stored bind password when nothing is typed in its place', async () => {
      await mountPanel()
      type('ldap-user-base', 'ou=staff,dc=example,dc=org')

      await save()

      expect(settings.update.mock.calls[0][1].ldap.bindPassword).toBe(SECRET_PLACEHOLDER)
    })

    it('sends a new bind password that was typed', async () => {
      await mountPanel()
      type('ldap-bind-password', 'rotated-bind')

      await save()

      expect(settings.update.mock.calls[0][1].ldap.bindPassword).toBe('rotated-bind')
    })

    it('says that the form holds changes that are not saved yet', async () => {
      await mountPanel()
      expect(wrapper.text()).not.toContain('unsaved changes')

      type('ldap-label', 'Staff LDAP')
      await flushPromises()

      expect(wrapper.text()).toContain('You have unsaved changes.')
    })

    it('refuses an address that is not ldap or ldaps, and writes nothing', async () => {
      await mountPanel()
      type('ldap-url', 'https://ldap.example.org')

      await save()

      expect(wrapper.text()).toContain('starting with ldap:// or ldaps://')
      expect(settings.update).not.toHaveBeenCalled()
    })

    it('needs the {username} placeholder in the user filter, and writes nothing without it', async () => {
      await mountPanel()
      type('ldap-user-filter', '(uid=alice)')

      await save()

      expect(wrapper.text()).toContain('The user filter must contain {username}')
      expect(settings.update).not.toHaveBeenCalled()
    })

    it('needs {dn} or {username} in the group filter once a group base is given', async () => {
      await mountPanel()
      type('ldap-group-filter', '(objectClass=groupOfNames)')

      await save()

      expect(wrapper.text()).toContain('The group filter must contain {dn}')
      expect(settings.update).not.toHaveBeenCalled()
    })

    it('explains the placeholders of the user and group filter', async () => {
      await mountPanel()

      expect(wrapper.text()).toContain('{username} stands for the username typed at sign in')
      expect(wrapper.text()).toContain('{dn} stands for the user DN')
    })

    it('needs the user base DN while the directory is enabled', async () => {
      await mountPanel()
      type('ldap-user-base', '')

      await save()

      expect(wrapper.text()).toContain('Enter the base DN where users are found')
      expect(settings.update).not.toHaveBeenCalled()
    })

    it('shows the reason and keeps the form when the server refuses the save', async () => {
      settings.update.mockRejectedValue(rejection(400))
      await mountPanel()
      type('ldap-user-base', 'ou=staff,dc=example,dc=org')

      await save()

      expect(wrapper.find('[role="alert"]').text()).toBe('Not saved: Request failed with status 400')
      expect(field('ldap-user-base').value).toBe('ou=staff,dc=example,dc=org')
    })

    it('says that the directory for Maven basic authentication is separate', async () => {
      await mountPanel()

      expect(wrapper.text()).toContain('basic authentication')
      expect(wrapper.text()).toContain('separate')
    })

    it('explains the external reference of a team that is synced from this directory', async () => {
      await mountPanel()

      expect(wrapper.text()).toContain('ldap:<group>')
    })

    it('shows the admin groups as chips', async () => {
      await mountPanel()

      expect(wrapper.text()).toContain('ingot-admins')
    })
  })

  describe('test connection', () => {
    it('is offered for the saved directory', async () => {
      await mountPanel()

      expect(buttonNamed(wrapper, 'Test connection').element.disabled).toBe(false)
    })

    it('is not offered while the form holds changes, because it would read the saved ones', async () => {
      await mountPanel()
      type('ldap-user-base', 'ou=staff,dc=example,dc=org')
      await flushPromises()

      expect(buttonNamed(wrapper, 'Test connection').element.disabled).toBe(true)
      expect(wrapper.text()).toContain('Save your changes first')
    })

    it('checks the connection alone when no test user is given', async () => {
      await mountPanel()

      buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(auth.testLdapProvider).toHaveBeenCalledWith({})
    })

    it('looks up the given test user and password', async () => {
      await mountPanel()
      type('ldap-test-username', 'ldapuser')
      type('ldap-test-password', 'ldap-password-12')

      buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(auth.testLdapProvider).toHaveBeenCalledWith({ username: 'ldapuser', password: 'ldap-password-12' })
    })

    it('shows the user it found and the groups it has', async () => {
      await mountPanel()

      buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      const result = wrapper.find('[data-test="ldap-test-result"]').text()
      expect(result).toContain('User found: uid=ldapuser,ou=people,dc=example,dc=org.')
      expect(result).toContain('Groups: ingot-admins, staff.')
    })

    it('shows the reason when the directory cannot be reached', async () => {
      auth.testLdapProvider.mockResolvedValue({ data: { ok: false, error: 'Connection refused' } })
      await mountPanel()

      buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-test="ldap-test-result"]').text()).toContain('The test failed: Connection refused')
    })

    it('shows a failed request as a failed test', async () => {
      auth.testLdapProvider.mockRejectedValue(rejection(503))
      await mountPanel()

      buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-test="ldap-test-result"]').text()).toContain('The test failed: Request failed with status 503')
    })
  })
})
