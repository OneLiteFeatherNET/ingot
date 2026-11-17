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
        { source: 'TEAM', team: 'developers', role: 'DEPLOY', pathPrefix: null },
        { source: 'TEAM', team: 'docs', role: 'READ', pathPrefix: 'com/example' }
      ]
    },
    {
      principal: 'ci-bot',
      kind: 'MACHINE',
      disabled: true,
      instanceAdmin: false,
      effective: ['READ', 'WRITE', 'DELETE'],
      via: [
        { source: 'LEGACY', team: 'machine:ci-bot', role: 'LEGACY_READ', pathPrefix: '/releases/net/theevilreaper/dartpoet' },
        { source: 'LEGACY', team: 'machine:ci-bot', role: 'LEGACY_WRITE', pathPrefix: '/releases' }
      ]
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

const entryWith = (entries) => ({ repository: 'releases', visibility: 'PRIVATE', entries })

const principalOf = (overrides) => ({
  principal: 'alice',
  kind: 'USER',
  disabled: false,
  instanceAdmin: false,
  effective: ['READ'],
  via: [{ source: 'TEAM', team: 'developers', role: 'READ', pathPrefix: null }],
  ...overrides
})

describe('RepositoryAccessCard', () => {
  let wrapper

  const rows = () => wrapper.findAll('tbody tr')
  const row = (principal) => {
    const match = rows().find(entry => entry.get('[data-test="principal"]').text() === principal)
    if (!match) throw new Error(`no row for the principal ${principal}`)
    return match
  }
  const texts = (scope, selector) => scope.findAll(selector).map(node => node.text())

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

    it('shows the access as a table with a caption that names the repository', () => {
      expect(wrapper.get('table caption').text()).toBe('Principals with access to releases')
    })

    it('titles the four columns in plain words', () => {
      expect(texts(wrapper, 'thead th')).toEqual(['Principal', 'Access', 'Granted through', 'Scope'])
    })

    it('shows one table row per principal', () => {
      expect(rows()).toHaveLength(3)
      expect(rows().map(entry => entry.get('[data-test="principal"]').text())).toEqual(['root', 'alice', 'ci-bot'])
    })

    it('names the kind of a user and of a machine account', () => {
      expect(row('alice').get('[data-test="kind"]').text()).toBe('User')
      expect(row('ci-bot').get('[data-test="kind"]').text()).toBe('Machine')
    })

    it('marks a disabled principal', () => {
      expect(row('ci-bot').get('[data-test="disabled"]').text()).toBe('Disabled')
      expect(row('alice').find('[data-test="disabled"]').exists(), 'an enabled principal has no marker').toBe(false)
    })

    it('lists the effective actions in the fixed order Read, Write, Delete, Manage', () => {
      expect(texts(row('ci-bot'), '[data-test="action"]')).toEqual(['Read', 'Write', 'Delete'])
      expect(texts(row('alice'), '[data-test="action"]')).toEqual(['Read', 'Write'])
    })

    it('shows Instance admin once in the access, as "Instance admin (all)" and nothing else', () => {
      expect(texts(row('root'), '[data-test="action"]')).toEqual(['Instance admin (all)'])
    })

    it('shows an action it has no label for as it is, after the known ones', async () => {
      install({ access: entryWith([principalOf({ effective: ['AUDIT', 'WRITE', 'READ'] })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="action"]')).toEqual(['Read', 'Write', 'AUDIT'])
    })

    it('lists one line per grant under "Granted through", with the team linked and its role', () => {
      expect(texts(row('alice'), '[data-test="via"]')).toEqual(['Team developers (Deploy)', 'Team docs (Read)'])
    })

    it('links a team to its page', () => {
      expect(row('alice').get('[data-test="via"] a').attributes('href')).toBe('/access/teams/developers')
    })

    it('says "Token route, read" and "Token route, write" for the legacy read and write routes', () => {
      expect(texts(row('ci-bot'), '[data-test="via"]')).toEqual(['Token route, read', 'Token route, write'])
    })

    it('never shows the raw legacy role names', () => {
      expect(wrapper.text()).not.toContain('LEGACY')
    })

    it('shows the path of a legacy route relative to the repository, without the repository name', () => {
      expect(texts(row('ci-bot'), '[data-test="via-prefix"]')).toEqual(['net/theevilreaper/dartpoet', 'Whole repository'])
    })

    it('shows the path of a team grant as it is, since it is already relative to the repository', () => {
      expect(texts(row('alice'), '[data-test="via-prefix"]')).toEqual(['Whole repository', 'com/example'])
    })

    it('lists the grants of one principal as lines of its row, not as rows of their own', () => {
      expect(rows().filter(entry => entry.get('[data-test="principal"]').text() === 'ci-bot')).toHaveLength(1)
    })

    it('shows an instance admin as granted through "Instance admin" over the whole repository', () => {
      expect(texts(row('root'), '[data-test="via"]')).toEqual(['Instance admin'])
      expect(texts(row('root'), '[data-test="via-prefix"]')).toEqual(['Whole repository'])
    })

    it('does not mark a principal that is no instance admin as one', () => {
      expect(texts(row('alice'), '[data-test="via"]').join(' ')).not.toContain('Instance admin')
      expect(texts(row('alice'), '[data-test="action"]')).not.toContain('Instance admin (all)')
    })
  })

  describe('a grant that has no team', () => {
    it('shows "Instance admin" for the instance admin source', async () => {
      install({ access: entryWith([principalOf({ instanceAdmin: true, via: [{ source: 'INSTANCE_ADMIN', team: null, role: null, pathPrefix: null }] })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via"]')).toEqual(['Instance admin'])
    })

    it('shows "Instance role" when the source says so', async () => {
      install({ access: entryWith([principalOf({ instanceAdmin: true, via: [{ source: 'INSTANCE_ROLE', team: null, role: null, pathPrefix: null }] })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via"]')).toEqual(['Instance role'])
    })

    it('shows "Instance admin" for a team-less grant without a source', async () => {
      install({ access: entryWith([principalOf({ instanceAdmin: true, via: [{ team: null, role: null, pathPrefix: null }] })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via"]')).toEqual(['Instance admin'])
    })

    it('shows the whole repository as its scope, and no role', async () => {
      install({ access: entryWith([principalOf({ instanceAdmin: true, via: [{ source: 'INSTANCE_ADMIN', team: null, role: null, pathPrefix: null }] })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via-prefix"]')).toEqual(['Whole repository'])
      expect(wrapper.find('[data-test="via-role"]').exists()).toBe(false)
    })

    it('shows "Instance admin" once when the admin has no grant of its own', async () => {
      install({ access: entryWith([principalOf({ instanceAdmin: true, via: null })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via"]')).toEqual(['Instance admin'])
    })

    it('keeps the team lines of the other grants of the same principal next to it', async () => {
      install({
        access: entryWith([principalOf({
          instanceAdmin: true,
          via: [
            { source: 'TEAM', team: 'developers', role: 'DEPLOY', pathPrefix: null },
            { source: 'INSTANCE_ADMIN', team: null, role: null, pathPrefix: null }
          ]
        })])
      })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via"]')).toEqual(['Team developers (Deploy)', 'Instance admin'])
      expect(wrapper.findAll('[data-test="via"] a').map(link => link.attributes('href'))).toEqual(['/access/teams/developers'])
    })
  })

  describe('a machine account grant that is not a legacy route', () => {
    it('says "Direct grant" with the role, and does not link the hidden team', async () => {
      install({ access: entryWith([principalOf({ kind: 'MACHINE', via: [{ source: 'TEAM', team: 'machine:deploy-bot', role: 'DEPLOY', pathPrefix: null }] })]) })
      wrapper = await mountCard()

      expect(texts(wrapper, '[data-test="via"]')).toEqual(['Direct grant (Deploy)'])
      expect(wrapper.find('[data-test="via"] a').exists(), 'machine:* is hidden from the team pages').toBe(false)
    })
  })

  describe('the order of the principals', () => {
    it('puts the instance admins first and sorts each group by name', async () => {
      install({
        access: entryWith([
          { ...principalOf({ kind: 'USER' }), principal: 'zed' },
          { ...principalOf({ instanceAdmin: true, effective: ['READ'] }), principal: 'root' },
          { ...principalOf({ instanceAdmin: true, effective: ['READ'] }), principal: 'anna' },
          { ...principalOf({ kind: 'USER' }), principal: 'bob' }
        ])
      })
      wrapper = await mountCard()

      expect(rows().map(entry => entry.get('[data-test="principal"]').text())).toEqual(['anna', 'root', 'bob', 'zed'])
    })
  })

  describe('the filter of the principals', () => {
    const manyPrincipals = () => entryWith([
      { ...principalOf({ kind: 'USER' }), principal: 'root', instanceAdmin: true },
      ...Array.from({ length: 11 }, (_, index) => ({
        ...principalOf({ kind: 'USER' }),
        principal: `user-${String(index + 1).padStart(2, '0')}`
      }))
    ])

    it('is not shown while there are ten principals or fewer', async () => {
      install()
      wrapper = await mountCard()

      expect(wrapper.find('input[type="search"]').exists()).toBe(false)
    })

    it('is shown with the label "Filter principals" when there are more than ten', async () => {
      install({ access: manyPrincipals() })
      wrapper = await mountCard()

      expect(wrapper.get('input[type="search"]').attributes('id')).toBeTruthy()
      expect(wrapper.get('label[for]').text()).toBe('Filter principals')
    })

    it('keeps only the principals whose name contains the query', async () => {
      install({ access: manyPrincipals() })
      wrapper = await mountCard()

      await wrapper.get('input[type="search"]').setValue('USER-07')

      expect(rows().map(entry => entry.get('[data-test="principal"]').text())).toEqual(['user-07'])
    })

    it('says so when no principal matches the query', async () => {
      install({ access: manyPrincipals() })
      wrapper = await mountCard()

      await wrapper.get('input[type="search"]').setValue('nobody')

      expect(rows()).toHaveLength(0)
      expect(wrapper.get('[data-test="no-match"]').text()).toBe('No principal matches the filter.')
    })
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

  it('still renders an entry whose via is missing', async () => {
    const entry = { principal: 'alice', kind: 'USER', disabled: false, instanceAdmin: false, effective: ['READ'] }
    install({ access: { repository: 'releases', visibility: 'PUBLIC', entries: [entry] } })
    wrapper = await mountCard()

    expect(row('alice').get('[data-test="principal"]').text()).toBe('alice')
    expect(row('alice').findAll('[data-test="via"]')).toHaveLength(0)
  })

  it('still renders an entry whose via is null and whose effective list is missing', async () => {
    const entry = { principal: 'alice', kind: 'USER', disabled: false, instanceAdmin: true, via: null }
    install({ access: { repository: 'releases', visibility: 'PUBLIC', entries: [entry] } })
    wrapper = await mountCard()

    expect(texts(row('alice'), '[data-test="via"]')).toEqual(['Instance admin'])
    expect(texts(row('alice'), '[data-test="action"]')).toEqual(['Instance admin (all)'])
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
