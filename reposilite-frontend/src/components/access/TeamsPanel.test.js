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
import TeamsPanel from './TeamsPanel.vue'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const shell = { render: () => null }

const TeamDetailStub = {
  props: ['name'],
  template: '<div data-test="detail">{{ name }}</div>'
}

const TEAMS = [
  { name: 'developers', description: 'Everyone who writes code', externalRef: null, memberCount: 4, grantCount: 2 },
  { name: 'admins', description: 'Runs the instance', externalRef: null, memberCount: 1, grantCount: 0 },
  { name: 'release', description: 'Ships the artifacts', externalRef: 'idp:release', memberCount: 1, grantCount: 1 }
]

describe('TeamsPanel', () => {
  let wrapper
  let router
  let teams

  const mountPanel = async (props = {}, url = '/access/teams') => {
    router = createRouter({
      history: createMemoryHistory(),
      routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
    })
    await router.push(url)
    await router.isReady()
    wrapper = mount(TeamsPanel, {
      props,
      attachTo: document.body,
      global: { plugins: [router], stubs: { TeamDetail: TeamDetailStub } }
    })
    await flushPromises()
  }

  const names = () => wrapper.findAll('[data-test="row"] [data-test="team-name"]').map(node => node.text())
  const row = (name) => wrapper.findAll('[data-test="row"]').find(node => node.find('[data-test="team-name"]').text() === name)

  const type = async (value) => {
    await wrapper.find('[data-test="search"]').setValue(value)
    await flushPromises()
  }

  beforeEach(() => {
    teams = {
      list: vi.fn().mockResolvedValue({ data: TEAMS }),
      get: vi.fn(async (name) => ({ data: { ...TEAMS.find(team => team.name === name), instanceRole: name === 'admins' } })),
      create: vi.fn(async (body) => ({ data: { ...body, members: [], grants: [], instanceRole: false } }))
    }
    holder.session = { client: ref({ identity: { teams } }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('lists the teams of the server', async () => {
    await mountPanel()

    expect(names()).toEqual(['developers', 'admins', 'release'])
  })

  it('shows the member count and the grant count of a team', async () => {
    await mountPanel()

    expect(row('developers').find('[data-test="counts"]').text()).toBe('4 members, 2 grants')
    expect(row('release').find('[data-test="counts"]').text()).toBe('1 member, 1 grant')
  })

  it('shows the description of a team', async () => {
    await mountPanel()

    expect(row('release').find('[data-test="team-description"]').text()).toBe('Ships the artifacts')
  })

  it('shows the instance admin chip on a team that holds the role, and only there', async () => {
    await mountPanel()

    expect(row('admins').find('[data-test="admin-badge"]').text()).toBe('Instance admin')
    expect(row('developers').find('[data-test="admin-badge"]').exists()).toBe(false)
  })

  it('takes the instance admin role from the list entry when it carries it', async () => {
    teams.list.mockResolvedValue({ data: [{ ...TEAMS[0], instanceRole: true }] })
    await mountPanel()

    expect(row('developers').find('[data-test="admin-badge"]').exists()).toBe(true)
    expect(teams.get, 'no detail request for an entry that already says').not.toHaveBeenCalled()
  })

  it('filters by name', async () => {
    await mountPanel()

    await type('DEVEL')

    expect(names()).toEqual(['developers'])
  })

  it('filters by description', async () => {
    await mountPanel()

    await type('ships')

    expect(names()).toEqual(['release'])
  })

  it('says so when nothing matches', async () => {
    await mountPanel()

    await type('zzz')

    expect(wrapper.text()).toContain('Nothing matches "zzz".')
  })

  it('opens a team in the address when its row is clicked', async () => {
    await mountPanel()

    await row('release').find('a').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/teams/release')
  })

  it('shows the detail of the team named by the address', async () => {
    await mountPanel({ name: 'release' }, '/access/teams/release')

    expect(wrapper.find('[data-test="detail"]').text()).toBe('release')
  })

  it('opens the new team after the create dialog made it', async () => {
    await mountPanel()

    await wrapper.find('[data-test="new-team"]').trigger('click')
    await flushPromises()
    const input = document.getElementById('new-team-name')
    input.value = 'qa'
    input.dispatchEvent(new Event('input'))
    document.querySelector('[role="dialog"] form').dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(teams.create).toHaveBeenCalledWith({ name: 'qa' })
    expect(router.currentRoute.value.fullPath).toBe('/access/teams/qa')
  })

  it('reloads the list after a team was created', async () => {
    await mountPanel()

    await wrapper.find('[data-test="new-team"]').trigger('click')
    await flushPromises()
    const input = document.getElementById('new-team-name')
    input.value = 'qa'
    input.dispatchEvent(new Event('input'))
    document.querySelector('[role="dialog"] form').dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(teams.list).toHaveBeenCalledTimes(2)
  })

  it('offers a retry when the list cannot be loaded', async () => {
    teams.list.mockRejectedValueOnce(new Error('offline'))
    await mountPanel()

    expect(wrapper.find('[role="alert"]').text()).toContain('offline')
  })
})
