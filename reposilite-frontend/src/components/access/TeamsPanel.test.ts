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
import { ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router, type RouteRecordRaw } from 'vue-router'
import { ROUTES } from '../../router/routes'
import TeamsPanel from './TeamsPanel.vue'
import { selectedKey } from './rowSelection'

vi.mock('./rowSelection', async importOriginal => {
  const actual = await importOriginal<typeof import('./rowSelection')>()
  return { ...actual, selectedKey: vi.fn(actual.selectedKey) }
})

const holder = vi.hoisted((): { session: unknown } => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: { response?: { data?: { message?: string } }, message?: string } | undefined) => error?.response?.data?.message ?? error?.message ?? String(error?.message)
}))

const shell = { render: () => null }

const TeamDetailStub = {
  props: ['name'],
  template: '<div data-test="detail">{{ name }}</div>'
}

const TEAMS = [
  { name: 'developers', description: 'Everyone who writes code', externalRef: null, memberCount: 4, nodeCount: 2, instanceAdmin: false },
  { name: 'admins', description: 'Runs the instance', externalRef: null, memberCount: 1, nodeCount: 0, instanceAdmin: true },
  { name: 'release', description: 'Ships the artifacts', externalRef: 'idp:release', memberCount: 1, nodeCount: 1, instanceAdmin: false }
]

describe('TeamsPanel', () => {
  let wrapper: VueWrapper | undefined
  let router: Router
  let teams: { list: Mock, get: Mock, create: Mock }

  const mountPanel = async (props: { name?: string } = {}, url = '/access/teams') => {
    router = createRouter({
      history: createMemoryHistory(),
      routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell })) as RouteRecordRaw[]
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

  const names = () => wrapper!.findAll('[data-test="row"] [data-test="team-name"]').map(node => node.text())
  const row = (name: string) => wrapper!.findAll('[data-test="row"]').find(node => node.find('[data-test="team-name"]').text() === name)

  const type = async (value: string) => {
    await wrapper!.find('[data-test="search"]').setValue(value)
    await flushPromises()
  }

  beforeEach(() => {
    teams = {
      list: vi.fn().mockResolvedValue({ data: TEAMS }),
      get: vi.fn((name: string) => Promise.resolve({ data: { ...TEAMS.find(team => team.name === name), instanceRole: name === 'admins' } })),
      create: vi.fn((body: { name: string }) => Promise.resolve({ data: { ...body, members: [], grants: [], instanceRole: false } }))
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

  it('shows the member count and the node count of a team', async () => {
    await mountPanel()

    expect(row('developers')!.find('[data-test="counts"]').text()).toBe('4 members, 2 nodes')
    expect(row('release')!.find('[data-test="counts"]').text()).toBe('1 member, 1 node')
  })

  it('shows the description of a team', async () => {
    await mountPanel()

    expect(row('release')!.find('[data-test="team-description"]').text()).toBe('Ships the artifacts')
  })

  it('shows the instance admin chip on a team that holds the role, and only there', async () => {
    await mountPanel()

    expect(row('admins')!.find('[data-test="admin-badge"]').text()).toBe('Instance admin')
    expect(row('developers')!.find('[data-test="admin-badge"]').exists()).toBe(false)
  })

  it('reads the list in one request and no detail of any team', async () => {
    await mountPanel()

    expect(teams.list, 'one list request').toHaveBeenCalledTimes(1)
    expect(teams.get, 'no per-row detail request').not.toHaveBeenCalled()
  })

  it('shows no instance admin chip when the list entry lacks the flag', async () => {
    teams.list.mockResolvedValue({ data: [{ name: 'developers', description: '', externalRef: null, memberCount: 4, nodeCount: 2 }] })
    await mountPanel()

    expect(row('developers')!.find('[data-test="admin-badge"]').exists()).toBe(false)
    expect(teams.get, 'a missing flag is not looked up').not.toHaveBeenCalled()
  })

  it('draws a failed load as an error state panel and reads the list again on Retry', async () => {
    teams.list.mockRejectedValueOnce({ message: 'Store unavailable' })
    await mountPanel()

    const panel = wrapper!.find('[data-test="list-error"]')
    expect(panel.exists(), 'the error is an error state panel').toBe(true)
    expect(panel.text()).toContain('Store unavailable')

    await panel.find('button').trigger('click')
    await flushPromises()

    expect(wrapper!.find('[data-test="list-error"]').exists(), 'the retry cleared the error').toBe(false)
    expect(names()).toEqual(['developers', 'admins', 'release'])
  })

  it('draws the loading line as a busy state panel', async () => {
    teams.list.mockReturnValue(new Promise(() => undefined))
    await mountPanel()

    const panel = wrapper!.find('[data-test="list-loading"]')
    expect(panel.exists(), 'the loading state panel is drawn').toBe(true)
    expect(panel.attributes('aria-busy')).toBe('true')
    expect(wrapper!.text()).not.toContain('Loading...')
  })

  it('draws the empty list as an empty state panel', async () => {
    teams.list.mockResolvedValue({ data: [] })
    await mountPanel()

    const panel = wrapper!.find('[data-test="list-empty"]')
    expect(panel.exists(), 'the empty state panel is drawn').toBe(true)
    expect(panel.text()).toContain('There are no teams yet.')
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

    expect(wrapper!.text()).toContain('Nothing matches "zzz".')
  })

  it('opens a team in the address when its row is clicked', async () => {
    await mountPanel()

    await row('release')!.find('a').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/access/teams/release')
  })

  it('builds the team rows from the shared list rows', async () => {
    await mountPanel()

    expect(wrapper!.findComponent({ name: 'MdList' }).exists(), 'the rows sit in an MdList').toBe(true)
    const items = wrapper!.findAllComponents({ name: 'MdListItem' })
    expect(items.map(item => item.attributes('data-test')), 'every team row is an MdListItem').toEqual(['row', 'row', 'row'])
  })

  it('marks the row of the open team as current, and no other', async () => {
    await mountPanel({ name: 'release' }, '/access/teams/release')

    const current = wrapper!.findAll('[data-test="row"] [aria-current="true"]')
    expect(current, 'exactly one row is current').toHaveLength(1)
    expect(current[0].find('[data-test="team-name"]').text(), 'the current row is the open team').toBe('release')
  })

  it('asks for the selected row once per render of the list', async () => {
    await mountPanel({ name: 'release' }, '/access/teams/release')
    vi.mocked(selectedKey).mockClear()

    await wrapper!.find('[data-test="search"]').setValue('a')

    expect(selectedKey, 'one answer per render, whatever the number of rows').toHaveBeenCalledTimes(1)
  })

  it('marks no row as current while no team is open', async () => {
    await mountPanel()

    expect(wrapper!.findAll('[data-test="row"] [aria-current]'), 'the list alone has no current row').toHaveLength(0)
  })

  it('shows the detail of the team named by the address', async () => {
    await mountPanel({ name: 'release' }, '/access/teams/release')

    expect(wrapper!.find('[data-test="detail"]').text()).toBe('release')
  })

  it('keeps the same inset on the detail pane at every width', async () => {
    await mountPanel({ name: 'release' }, '/access/teams/release')

    const pane = wrapper!.find('.expanded\\:p-6')
    expect(pane.exists(), 'the detail pane takes the wide inset from 840 pixels').toBe(true)
    expect(pane.classes(), 'the detail pane has an inset below 840 pixels too').toContain('p-4')
    expect(pane.classes(), 'the detail pane is never hidden below 840 pixels').not.toContain('max-expanded:hidden')
  })

  it('offers the permission check next to New team on the list when no team is selected', async () => {
    await mountPanel()

    const link = wrapper!.findAll('a').find(item => item.text() === 'Check a permission')
    expect(link?.attributes('href'), 'the list toolbar opens the check').toBe('/access/check')
  })

  it('draws no permission check link beside the detail of a team', async () => {
    await mountPanel({ name: 'release' }, '/access/teams/release')

    expect(wrapper!.findAll('a').map(item => item.text())).not.toContain('Check a permission')
  })

  it('opens the new team after the create dialog made it', async () => {
    await mountPanel()

    await wrapper!.find('[data-test="new-team"]').trigger('click')
    await flushPromises()
    const input = document.getElementById('new-team-name') as HTMLInputElement
    input.value = 'qa'
    input.dispatchEvent(new Event('input'))
    document.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(teams.create).toHaveBeenCalledWith({ name: 'qa' })
    expect(router.currentRoute.value.fullPath).toBe('/access/teams/qa')
  })

  it('reloads the list after a team was created', async () => {
    await mountPanel()

    await wrapper!.find('[data-test="new-team"]').trigger('click')
    await flushPromises()
    const input = document.getElementById('new-team-name') as HTMLInputElement
    input.value = 'qa'
    input.dispatchEvent(new Event('input'))
    document.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(teams.list).toHaveBeenCalledTimes(2)
  })

  it('offers a retry when the list cannot be loaded', async () => {
    teams.list.mockRejectedValueOnce(new Error('offline'))
    await mountPanel()

    expect(wrapper!.find('[role="alert"]').text()).toContain('offline')
  })
})
