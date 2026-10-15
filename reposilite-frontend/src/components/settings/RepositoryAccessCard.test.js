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
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../router/routes'
import RepositoryAccessCard from './RepositoryAccessCard.vue'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const ACCESS = {
  repository: 'releases',
  visibility: 'PRIVATE',
  entries: [
    {
      principal: 'alice',
      kind: 'USER',
      disabled: false,
      instanceAdmin: false,
      effective: ['READ', 'WRITE'],
      via: [
        { team: 'developers', role: 'DEPLOY', pathPrefix: null },
        { team: 'docs', role: 'READ', pathPrefix: 'com/example' }
      ]
    },
    {
      principal: 'ci-bot',
      kind: 'MACHINE',
      disabled: true,
      instanceAdmin: false,
      effective: ['READ'],
      via: [{ team: 'machine:ci-bot', role: 'READ', pathPrefix: null }]
    },
    {
      principal: 'root',
      kind: 'USER',
      disabled: false,
      instanceAdmin: true,
      effective: ['READ', 'WRITE', 'DELETE', 'MANAGE_REPOSITORY', 'INSTANCE_ADMIN'],
      via: []
    }
  ]
}

const shell = { render: () => null }

const install = ({ admin = true, access = ACCESS } = {}) => {
  const repositoryAccess = vi.fn(async () => ({ data: access }))
  holder.session = {
    isManager: ref(admin),
    client: ref({ identity: { repositoryAccess } })
  }
  return repositoryAccess
}

const makeRouter = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
  })
  await router.push('/settings/maven')
  await router.isReady()
  return router
}

const mountCard = async (repository = 'releases') => {
  const router = await makeRouter()
  const wrapper = mount(RepositoryAccessCard, { props: { repository }, attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('RepositoryAccessCard', () => {
  let wrapper

  const rows = () => wrapper.findAll('[data-test="access-row"]')
  const row = (principal) => wrapper.get(`[data-test="access-row"][data-principal="${principal}"]`)

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('as an instance admin', () => {
    let repositoryAccess

    beforeEach(async () => {
      repositoryAccess = install()
      wrapper = await mountCard()
    })

    it('asks the server for the access of the repository by its name', () => {
      expect(repositoryAccess).toHaveBeenCalledTimes(1)
      expect(repositoryAccess).toHaveBeenCalledWith('releases')
    })

    it('titles the section "Who has access"', () => {
      expect(wrapper.get('[data-test="repository-access"] h4').text()).toBe('Who has access')
    })

    it('shows the visibility as a chip', () => {
      expect(wrapper.get('[data-test="visibility"]').text()).toBe('Private')
    })

    it('shows one row per principal with its name', () => {
      expect(rows().map(entry => entry.get('[data-test="principal"]').text())).toEqual(['alice', 'ci-bot', 'root'])
    })

    it('names the kind of a user and of a machine account', () => {
      expect(row('alice').get('[data-test="kind"]').text()).toBe('User')
      expect(row('ci-bot').get('[data-test="kind"]').text()).toBe('Machine')
    })

    it('shows the effective actions as chips', () => {
      expect(row('alice').findAll('[data-test="action"]').map(chip => chip.text())).toEqual(['READ', 'WRITE'])
    })

    it('marks a disabled principal', () => {
      expect(row('ci-bot').get('[data-test="disabled"]').text()).toBe('Disabled')
      expect(row('alice').find('[data-test="disabled"]').exists(), 'an enabled principal has no chip').toBe(false)
    })

    it('lists the team, the role and the path prefix of every grant', () => {
      const via = row('alice').findAll('[data-test="via"]')

      expect(via).toHaveLength(2)
      expect(via[0].get('[data-test="via-team"]').text()).toBe('developers')
      expect(via[0].get('[data-test="via-role"]').text()).toBe('Deploy')
      expect(via[0].get('[data-test="via-prefix"]').text()).toBe('Whole repository')
      expect(via[1].get('[data-test="via-prefix"]').text()).toBe('com/example')
    })

    it('links a team to its page', () => {
      const link = row('alice').get('[data-test="via"] a')

      expect(link.attributes('href')).toBe('/access/teams/developers')
    })

    it('does not link a machine account team and calls it a machine account grant', () => {
      const via = row('ci-bot').get('[data-test="via"]')

      expect(via.find('a').exists(), 'machine:* is hidden from the team pages').toBe(false)
      expect(via.get('[data-test="via-team"]').text()).toBe('Machine account grant')
    })

    it('marks an instance admin and explains where the access comes from', () => {
      const admin = row('root')

      expect(admin.get('[data-test="instance-admin"]').text()).toBe('Instance admin')
      expect(admin.get('[data-test="via-instance-admin"]').text()).toBe('Via instance admin')
      expect(admin.findAll('[data-test="action"]').map(chip => chip.text())).toContain('INSTANCE_ADMIN')
    })

    it('does not mark a principal that is no instance admin', () => {
      expect(row('alice').find('[data-test="instance-admin"]').exists()).toBe(false)
      expect(row('alice').find('[data-test="via-instance-admin"]').exists()).toBe(false)
    })
  })

  it('says that no one has access through identity grants when there are no entries', async () => {
    install({ access: { repository: 'releases', visibility: 'PUBLIC', entries: [] } })
    wrapper = await mountCard()

    expect(wrapper.get('[data-test="empty"]').text()).toBe('No one has access through identity grants yet.')
    expect(rows()).toHaveLength(0)
  })

  it('still shows the visibility when there are no entries', async () => {
    install({ access: { repository: 'releases', visibility: 'PUBLIC', entries: [] } })
    wrapper = await mountCard()

    expect(wrapper.get('[data-test="visibility"]').text()).toBe('Public')
  })

  it('shows a loading state while the answer is on its way', async () => {
    install()
    holder.session.client.value.identity.repositoryAccess = vi.fn(() => new Promise(() => {}))
    wrapper = await mountCard()

    expect(wrapper.get('[data-test="loading"]').text()).toContain('Loading')
  })

  it('shows the error with a retry that asks again', async () => {
    const repositoryAccess = install()
    repositoryAccess.mockRejectedValueOnce(Object.assign(new Error('boom'), { response: { status: 500, data: { message: 'Server error' } } }))
    wrapper = await mountCard()

    expect(wrapper.get('[role="alert"]').text()).toContain('Server error')

    await wrapper.get('[data-test="retry"]').trigger('click')
    await flushPromises()

    expect(repositoryAccess).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists(), 'the error is gone after a good answer').toBe(false)
    expect(rows()).toHaveLength(3)
  })

  it('asks again when the repository changes', async () => {
    const repositoryAccess = install()
    wrapper = await mountCard()

    await wrapper.setProps({ repository: 'snapshots' })
    await flushPromises()

    expect(repositoryAccess).toHaveBeenLastCalledWith('snapshots')
  })

  it('is not shown to someone who is no instance admin, and asks for nothing', async () => {
    const repositoryAccess = install({ admin: false })
    wrapper = await mountCard()

    expect(wrapper.find('[data-test="repository-access"]').exists()).toBe(false)
    expect(repositoryAccess).not.toHaveBeenCalled()
  })
})
