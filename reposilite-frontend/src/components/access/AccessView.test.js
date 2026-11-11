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
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../router/routes'
import AccessView from './AccessView.vue'
import { authenticationStore } from './authenticationTestKit'
import { configurationStoreKey } from '../../store/configuration'
import { ADMIN_NODES, INSTANCE_ADMIN_NODE } from '../../store/nodeAccess'

const holder = vi.hoisted(() => ({ session: null }))

// The configuration store reads the session when it is imported, before the test sets the holder.
vi.mock('../../store/session', () => ({ useSession: () => holder.session ?? { client: { value: null } } }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const shell = { render: () => null }

describe('AccessView', () => {
  let wrapper
  let router
  let principals
  let teams
  let settingsDomains

  const mountView = async (props, url = '/access/users') => {
    router = createRouter({
      history: createMemoryHistory(),
      routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
    })
    await router.push(url)
    await router.isReady()
    wrapper = mount(AccessView, {
      props,
      attachTo: document.body,
      global: { plugins: [router], provide: { [configurationStoreKey]: authenticationStore() } }
    })
    await flushPromises()
  }

  const tabs = () => wrapper.findAll('[aria-label="Administration"] a[href^="/access"]')

  beforeEach(() => {
    principals = {
      list: vi.fn().mockResolvedValue({ data: [] }),
      get: vi.fn()
    }
    teams = {
      list: vi.fn().mockResolvedValue({ data: [] }),
      get: vi.fn()
    }
    settingsDomains = {
      domains: vi.fn().mockResolvedValue({ data: ['web', 'maven'] }),
      schema: vi.fn().mockResolvedValue({ data: { title: 'Web' } })
    }
    holder.session = {
      client: ref({
        identity: { principals, teams },
        settings: { fetch: vi.fn().mockResolvedValue({ data: {} }), update: vi.fn(), ...settingsDomains }
      }),
      can: () => true
    }
  })

  it('draws one Administration heading with the Access description', async () => {
    await mountView({ tab: 'users' })

    expect(wrapper.findAll('h1').map(heading => heading.text())).toEqual(['Administration'])
    expect(wrapper.text()).toContain('Who can sign in to this instance and what they can reach.')
  })

  it('lists the settings domains in the Administration tree, as the other Administration pages do', async () => {
    await mountView({ tab: 'users' })

    const labels = wrapper.findAll('[aria-label="Administration"] a').map(link => link.text())
    expect(labels, 'the settings domains are in the tree with their titles').toContain('Web')
  })

  it('makes no request for the settings domains to a visitor without the settings node', async () => {
    holder.session.can = (node) => node !== ADMIN_NODES.settings

    await mountView({ tab: 'users' })

    expect(settingsDomains.domains, 'the domain list is not requested without the settings node').not.toHaveBeenCalled()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('offers users, machine accounts and teams as tabs', async () => {
    await mountView({ tab: 'users' })

    expect(tabs().map(tab => tab.text())).toEqual(['Users', 'Machine accounts', 'Teams', 'SSO', 'LDAP', 'Security settings', 'Permission check'])
  })

  it('opens the security page from the Security tab and shows its panel', async () => {
    await mountView({ tab: 'security' }, '/access/security')

    expect(tabs().filter(tab => tab.attributes('aria-current') === 'page').map(tab => tab.text())).toEqual(['Security settings'])
    expect(wrapper.find('[data-test="security"]').exists()).toBe(true)
  })

  it('moves to the security page through the router', async () => {
    await mountView({ tab: 'teams' }, '/access/teams')

    await tabs()[5].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/security')
  })

  it('hides the Security tab from a user without the instance admin node', async () => {
    holder.session.can = (node) => node !== INSTANCE_ADMIN_NODE

    await mountView({ tab: 'teams' }, '/access/teams')

    expect(tabs().map(tab => tab.text()), 'the tabs that need the instance admin node are hidden, not greyed out').toEqual(['Users', 'Machine accounts', 'Teams', 'Permission check'])
  })

  it('opens the single sign on page from the SSO tab and shows its panel', async () => {
    await mountView({ tab: 'sso' }, '/access/sso')

    expect(tabs().filter(tab => tab.attributes('aria-current') === 'page').map(tab => tab.text())).toEqual(['SSO'])
    expect(wrapper.find('[data-test="sso"]').exists()).toBe(true)
    expect(principals.list, 'the single sign on page lists no principals').not.toHaveBeenCalled()
  })

  it('opens the LDAP page from the LDAP tab and shows its form', async () => {
    await mountView({ tab: 'ldap' }, '/access/ldap')

    expect(tabs().filter(tab => tab.attributes('aria-current') === 'page').map(tab => tab.text())).toEqual(['LDAP'])
    expect(wrapper.find('[data-test="ldap"]').exists()).toBe(true)
  })

  it('moves to the LDAP page through the router', async () => {
    await mountView({ tab: 'teams' }, '/access/teams')

    await tabs()[4].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/ldap')
  })

  it('moves to the single sign on page through the router', async () => {
    await mountView({ tab: 'teams' }, '/access/teams')
    await tabs()[3].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/sso')
  })

  it('hides the tabs whose node the user lacks', async () => {
    holder.session.can = (node) => node === 'ingot.admin.teams'

    await mountView({ tab: 'teams' }, '/access/teams')

    expect(tabs().map(tab => tab.text()), 'a tab is hidden, not greyed out').toEqual(['Teams', 'Permission check'])
  })

  it('offers the Machine accounts tab to a user with ingot.admin.users', async () => {
    holder.session.can = (node) => node === 'ingot.admin.users'

    await mountView({ tab: 'machines' }, '/access/machines')

    expect(tabs().map(tab => tab.text()), 'machine accounts are listed through the users area').toEqual(['Users', 'Machine accounts', 'Permission check'])
  })

  it('hides the Machine accounts tab from a user with only ingot.admin.tokens', async () => {
    holder.session.can = (node) => node === 'ingot.admin.tokens'

    await mountView({ tab: 'users' })

    expect(tabs().map(tab => tab.text()), 'the list endpoint needs ingot.admin.users, so the tab stays hidden').toEqual(['Permission check'])
  })

  it('moves to the check route from the Check tab', async () => {
    await mountView({ tab: 'users' })

    await tabs()[6].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('access-check')
  })

  it('shows the permission check on the check tab', async () => {
    await mountView({ tab: 'check' }, '/access/check')

    expect(wrapper.find('[data-test="check-form"]').exists()).toBe(true)
  })

  it('lists the principals of every kind for the check picker', async () => {
    await mountView({ tab: 'check' }, '/access/check')

    expect(principals.list, 'the check tab asks for all kinds').toHaveBeenCalledWith()

    await tabs()[3].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/sso')
  })

  it('moves to the check route from the Check tab', async () => {
    await mountView({ tab: 'users' })

    await tabs()[6].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('access-check')
  })

  it('shows the permission check on the check tab', async () => {
    await mountView({ tab: 'check' }, '/access/check')

    expect(wrapper.find('[data-test="check-form"]').exists()).toBe(true)
  })

  it('lists the principals of every kind for the check picker', async () => {
    await mountView({ tab: 'check' }, '/access/check')

    expect(principals.list, 'the check tab asks for all kinds').toHaveBeenCalledWith()
  })

  it('marks the tab of the address as selected', async () => {
    await mountView({ tab: 'machines' }, '/access/machines')

    expect(tabs().filter(tab => tab.attributes('aria-current') === 'page').map(tab => tab.text()))
      .toEqual(['Machine accounts'])
  })

  it('lists users on the users tab', async () => {
    await mountView({ tab: 'users' })

    expect(principals.list).toHaveBeenCalledWith('USER')
  })

  it('lists machine accounts on the machines tab', async () => {
    await mountView({ tab: 'machines' }, '/access/machines')

    expect(principals.list).toHaveBeenCalledWith('MACHINE')
  })

  it('moves between the tabs through the router', async () => {
    await mountView({ tab: 'users' })

    await tabs()[1].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/machines')
  })

  it('opens the teams route from the Teams tab', async () => {
    await mountView({ tab: 'users' })

    await tabs()[2].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('access-teams')
  })

  it('lists the teams on the teams tab', async () => {
    await mountView({ tab: 'teams' }, '/access/teams')

    expect(teams.list).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-test="teams"]').exists()).toBe(true)
  })

  it('loads no principals for the teams tab', async () => {
    await mountView({ tab: 'teams' }, '/access/teams')

    expect(principals.list, 'the team list needs no principals').not.toHaveBeenCalled()
  })
})
