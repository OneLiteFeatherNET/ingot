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
import { ref, type Ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import LdapPanel from './LdapPanel.vue'
import { SECRET_PLACEHOLDER } from './loginSettings'
import { authenticationClient, authenticationStore } from './authenticationTestKit'
import { configurationStoreKey } from '../../store/configuration'

const holder = vi.hoisted(() => ({ session: null as null | { client: Ref<{ settings: Record<string, Mock>; auth: Record<string, Mock> }> } }))

// The configuration store reads the session when it is imported, before the test sets the holder.
vi.mock('../../store/session', () => ({ useSession: () => holder.session ?? { client: { value: null } } }))

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

const answer = (data: unknown, etag = '"v1"') => ({ data, headers: { etag } })
const rejection = (status: number) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: {} } })

const field = (id: string) => document.getElementById(id) as HTMLInputElement
const type = (id: string, value: string) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
}
const buttonNamed = (wrapper: VueWrapper, label: string) => wrapper.findAll('button').find(button => button.text() === label)!

describe('LdapPanel', () => {
  let wrapper: VueWrapper
  let settings: Record<string, Mock>
  let auth: Record<string, Mock>
  let authUpdate: Mock

  const mountPanel = async (client = authenticationClient({ update: authUpdate })) => {
    const store = authenticationStore(client)
    wrapper = mount(LdapPanel, {
      attachTo: document.body,
      global: { provide: { [configurationStoreKey]: store } }
    }) as VueWrapper
    await flushPromises()
  }

  const save = async () => {
    await wrapper.find('#ldap-form').trigger('submit')
    await flushPromises()
  }

  beforeEach(() => {
    settings = {
      fetch: vi.fn().mockResolvedValue(answer({ oidcProviders: OIDC, ldap: LDAP })),
      update: vi.fn().mockResolvedValue({})
    }
    authUpdate = vi.fn(async () => ({ data: {} }))
    auth = {
      testLdapProvider: vi.fn().mockResolvedValue({ data: { ok: true, userDn: 'uid=ldapuser,ou=people,dc=example,dc=org', groups: ['ingot-admins', 'staff'] } })
    }
    holder.session = { client: ref({ settings, auth }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined as unknown as VueWrapper
    document.body.innerHTML = ''
  })

  describe('headings', () => {
    it('titles both sections with level 2 headings in the title-medium role, under the level 1 page heading', async () => {
      await mountPanel()

      const titles = wrapper.findAll('h2')
      expect(titles.map(heading => heading.text()), 'both sections are level 2').toEqual(['Dashboard sign in', 'Maven and Gradle clients'])
      for (const title of titles) {
        expect(title.classes(), `"${title.text()}" uses md-title-medium`).toContain('md-title-medium')
      }
    })

    it('titles the parts of the sign in as level 3 headings in the title-small role, and draws no level 4 heading', async () => {
      await mountPanel()

      const titles = wrapper.findAll('h3')
      expect(titles.map(heading => heading.text()), 'the parts of the sign in are level 3')
        .toEqual(expect.arrayContaining(['Connection', 'Bind', 'User search', 'Groups', 'Test connection']))
      for (const title of titles) {
        expect(title.classes(), `"${title.text()}" uses md-title-small`).toContain('md-title-small')
      }
      expect(wrapper.find('h4').exists(), 'the panel has no level 4 heading').toBe(false)
    })
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
        ldap: { ...LDAP, userBaseDn: 'ou=staff,dc=example,dc=org' },
        mfa: { requiredForAdmins: false, requiredForTeams: [], graceDays: 14, stepUpMinutes: 5, passkeyFirstFactor: false }
      }, '"v1"')
    })

    it('keeps the stored bind password when nothing is typed in its place', async () => {
      await mountPanel()
      type('ldap-user-base', 'ou=staff,dc=example,dc=org')

      await save()

      expect((settings.update.mock.calls[0][1] as { ldap: { bindPassword: string } }).ldap.bindPassword).toBe(SECRET_PLACEHOLDER)
    })

    it('sends a new bind password that was typed', async () => {
      await mountPanel()
      type('ldap-bind-password', 'rotated-bind')

      await save()

      expect((settings.update.mock.calls[0][1] as { ldap: { bindPassword: string } }).ldap.bindPassword).toBe('rotated-bind')
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

    it('says that the Maven and Gradle directory is set below and does not change the dashboard sign in', async () => {
      await mountPanel()

      expect(wrapper.text()).toContain('Maven and Gradle clients')
      expect(wrapper.text()).toContain('does not change this one')
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

  describe('the Maven and Gradle clients section', () => {
    it('renders the dashboard sign in first and the Maven and Gradle clients below it', async () => {
      await mountPanel()

      const headings = wrapper.findAll('h2').map(heading => heading.text())
      expect(headings, 'both sections have a heading').toEqual(expect.arrayContaining(['Dashboard sign in', 'Maven and Gradle clients']))
      expect(headings.indexOf('Dashboard sign in'), 'the dashboard sign in comes first').toBeLessThan(headings.indexOf('Maven and Gradle clients'))
    })

    it('renders the LDAP fields of the authentication domain', async () => {
      await mountPanel()

      const section = wrapper.get('[data-test="ldap-clients"]').text()
      for (const label of ['Hostname', 'Port', 'Base DN', 'Search-User DN', 'User Filter', 'User Type']) {
        expect(section, `the LDAP field "${label}"`).toContain(label)
      }
      expect(wrapper.findAll('input').find(input => input.element.value === 'ldap.domain.com'), 'the stored hostname').toBeDefined()
    })

    it('saves the Maven and Gradle LDAP through the authentication domain, and leaves the dashboard directory alone', async () => {
      await mountPanel()
      const hostname = wrapper.findAll('input').find(input => input.element.value === 'ldap.domain.com')!
      await hostname.setValue('ldap2.domain.com')

      await wrapper.findAll('button').find(button => button.text() === 'Save Maven and Gradle LDAP')!.trigger('click')
      await flushPromises()

      expect(authUpdate).toHaveBeenCalledTimes(1)
      expect(authUpdate.mock.calls[0][0]).toBe('authentication')
      expect((authUpdate.mock.calls[0][1] as { ldap: { hostname: string } }).ldap.hostname).toBe('ldap2.domain.com')
      expect(settings.update, 'the dashboard settings are not written').not.toHaveBeenCalled()
    })
  })

  describe('state panels', () => {
    const signIn = () => wrapper.get('[aria-labelledby="ldap-title"]')
    const clients = () => wrapper.get('[data-test="ldap-clients"]')

    it('shows the loading sign in settings as a loading state panel', async () => {
      settings.fetch.mockReturnValue(new Promise(() => {}))
      await mountPanel()

      const panel = signIn().find('[aria-busy="true"]')
      expect(panel.exists(), 'a busy panel stands in for the form').toBe(true)
      expect(panel.attributes('data-ingot-component'), 'the loading line is an MdStatePanel').toBe('md-state-panel')
      expect(panel.attributes('data-ingot-state'), 'the panel is in the loading state').toBe('loading')
    })

    it('shows a failed load of the sign in settings as an error state panel with a tonal Retry', async () => {
      settings.fetch.mockRejectedValue(rejection(503))
      await mountPanel()

      const panel = signIn().get('[role="alert"]')
      expect(panel.attributes('data-ingot-component'), 'the failure is an MdStatePanel').toBe('md-state-panel')
      expect(panel.attributes('data-ingot-state'), 'the panel is in the error state').toBe('error')
      expect(panel.text(), 'the panel says what failed').toContain('Could not load the LDAP settings')
      const retry = panel.findAll('button').find(button => button.text() === 'Retry')!
      expect(retry.classes(), 'Retry is a tonal button').toContain('bg-secondary-container')
    })

    it('loads the sign in settings again on Retry', async () => {
      settings.fetch.mockRejectedValueOnce(rejection(503))
      await mountPanel()

      await signIn().get('[role="alert"]').findAll('button').find(button => button.text() === 'Retry')!.trigger('click')
      await flushPromises()

      expect(settings.fetch, 'Retry asks the server again').toHaveBeenCalledTimes(2)
      expect(field('ldap-user-base').value, 'the form is shown after the retry').toBe('ou=people,dc=example,dc=org')
    })

    it('shows a refused save of the sign in settings as an error alert', async () => {
      settings.update.mockRejectedValue(rejection(400))
      await mountPanel()
      type('ldap-user-base', 'ou=staff,dc=example,dc=org')

      await save()

      const alert = signIn().get('[role="alert"]')
      expect(alert.attributes('data-ingot-component'), 'the save failure is an MdAlert').toBe('md-alert')
      expect(alert.attributes('data-tone'), 'the alert is in the error tone').toBe('error')
    })

    it('shows a failed load of the Maven and Gradle settings as an error state panel', async () => {
      const client = authenticationClient()
      client.settings.domains = () => Promise.reject(new Error('unreachable'))
      await mountPanel(client)

      const panel = clients().get('[role="alert"]')
      expect(panel.attributes('data-ingot-component'), 'the failure is an MdStatePanel').toBe('md-state-panel')
      expect(panel.text(), 'the panel says what failed').toContain('Could not load the authentication settings')
    })

    it('shows a refused save of the Maven and Gradle settings as an error alert', async () => {
      authUpdate.mockRejectedValue(rejection(400))
      await mountPanel()
      await wrapper.findAll('input').find(input => input.element.value === 'ldap.domain.com')!.setValue('ldap2.domain.com')

      await wrapper.findAll('button').find(button => button.text() === 'Save Maven and Gradle LDAP')!.trigger('click')
      await flushPromises()

      const alert = clients().get('[role="alert"]')
      expect(alert.attributes('data-ingot-component'), 'the save failure is an MdAlert').toBe('md-alert')
    })
  })

  describe('shared section and card components', () => {
    it.each([
      ['ldap-title', 'Dashboard sign in'],
      ['ldap-clients-title', 'Maven and Gradle clients'],
      ['ldap-connection-title', 'Connection'],
      ['ldap-users-title', 'User search'],
      ['ldap-groups-title', 'Groups']
    ])('draws the heading "%s" as the title of an md-section', async (id, title) => {
      await mountPanel()

      const heading = document.getElementById(id)
      expect(heading?.textContent?.trim(), `the heading with id ${id}`).toBe(title)
      expect(heading?.closest('[data-test="section-header"]'), `the title "${title}" belongs to an md-section`).not.toBeNull()
    })

    it('draws the test connection block as an md-card', async () => {
      await mountPanel()

      const card = wrapper.get('[data-test="ldap-test"]')
      expect(card.attributes('data-variant'), 'the test connection block is drawn by md-card').toBe('low')
    })

    it('draws the test connection title as an md-section inside that md-card', async () => {
      await mountPanel()

      const card = wrapper.get('[data-test="ldap-test"]')
      const header = card.get('[data-test="section-header"]')
      expect(header.element.contains(document.getElementById('ldap-test-title')), 'the test connection title is the header of an md-section inside the md-card').toBe(true)
    })
  })

  describe('the connection section', () => {
    it('asks whether to offer LDAP, then the server address, then StartTLS, then the label', async () => {
      await mountPanel()

      const section = wrapper.get('section[aria-labelledby="ldap-connection-title"]').element as HTMLElement
      const names = [...section.querySelectorAll('label')].map(label => label.textContent.trim())
      expect(names, 'the order of the connection fields').toEqual([
        'Enabled: offer LDAP sign in',
        'Server address',
        'Upgrade the connection with StartTLS',
        'Label on the sign in form'
      ])
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

      await buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(auth.testLdapProvider).toHaveBeenCalledWith({})
    })

    it('looks up the given test user and password', async () => {
      await mountPanel()
      type('ldap-test-username', 'ldapuser')
      type('ldap-test-password', 'ldap-password-12')

      await buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(auth.testLdapProvider).toHaveBeenCalledWith({ username: 'ldapuser', password: 'ldap-password-12' })
    })

    it('shows the user it found and the groups it has', async () => {
      await mountPanel()

      await buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      const result = wrapper.find('[data-test="ldap-test-result"]').text()
      expect(result).toContain('User found: uid=ldapuser,ou=people,dc=example,dc=org.')
      expect(result).toContain('Groups: ingot-admins, staff.')
    })

    it('shows the reason when the directory cannot be reached', async () => {
      auth.testLdapProvider.mockResolvedValue({ data: { ok: false, error: 'Connection refused' } })
      await mountPanel()

      await buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-test="ldap-test-result"]').text()).toContain('The test failed: Connection refused')
    })

    it('shows a failed request as a failed test', async () => {
      auth.testLdapProvider.mockRejectedValue(rejection(503))
      await mountPanel()

      await buttonNamed(wrapper, 'Test connection').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-test="ldap-test-result"]').text()).toContain('The test failed: Request failed with status 503')
    })
  })
})
