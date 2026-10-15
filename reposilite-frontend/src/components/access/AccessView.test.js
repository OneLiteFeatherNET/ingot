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

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

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

  const mountView = async (props, url = '/access/users') => {
    router = createRouter({
      history: createMemoryHistory(),
      routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
    })
    await router.push(url)
    await router.isReady()
    wrapper = mount(AccessView, { props, attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()
  }

  const tabs = () => wrapper.findAll('[role="tab"]')

  beforeEach(() => {
    principals = {
      list: vi.fn().mockResolvedValue({ data: [] }),
      get: vi.fn()
    }
    teams = {
      list: vi.fn().mockResolvedValue({ data: [] }),
      get: vi.fn()
    }
    holder.session = {
      client: ref({
        identity: { principals, teams },
        settings: { fetch: vi.fn().mockResolvedValue({ data: {} }), update: vi.fn() }
      })
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('offers users, machine accounts and teams as tabs', async () => {
    await mountView({ tab: 'users' })

    expect(tabs().map(tab => tab.text())).toEqual(['Users', 'Machine accounts', 'Teams', 'SSO', 'LDAP', 'Check'])
  })

  it('opens the single sign on page from the SSO tab and shows its panel', async () => {
    await mountView({ tab: 'sso' }, '/access/sso')

    expect(tabs().filter(tab => tab.attributes('aria-selected') === 'true').map(tab => tab.text())).toEqual(['SSO'])
    expect(wrapper.find('[data-test="sso"]').exists()).toBe(true)
    expect(principals.list, 'the single sign on page lists no principals').not.toHaveBeenCalled()
  })

  it('opens the LDAP page from the LDAP tab and shows its form', async () => {
    await mountView({ tab: 'ldap' }, '/access/ldap')

    expect(tabs().filter(tab => tab.attributes('aria-selected') === 'true').map(tab => tab.text())).toEqual(['LDAP'])
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

  it('moves to the check route from the Check tab', async () => {
    await mountView({ tab: 'users' })

    await tabs()[5].trigger('click')
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

    expect(tabs().filter(tab => tab.attributes('aria-selected') === 'true').map(tab => tab.text()))
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
