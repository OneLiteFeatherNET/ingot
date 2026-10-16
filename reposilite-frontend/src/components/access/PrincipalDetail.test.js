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
import { setClock, resetClock } from '../../helpers/clock'
import PrincipalDetail from './PrincipalDetail.vue'
import { NODES, answer, failure, makeNodesApi } from './teamTestKit'

// The API sends an Instant as epoch seconds: the fixtures carry the same number.
const wireInstant = (iso) => Date.parse(iso) / 1000

// The session is replaced by a ref of the client, so each test decides what the server answers.
const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const shell = { render: () => null }

const makeRouter = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
  })
  await router.push('/access/users')
  await router.isReady()
  return router
}

const ALICE = {
  id: 2,
  kind: 'USER',
  name: 'alice',
  displayName: 'Alice Example',
  disabled: false,
  createdAt: wireInstant('2026-10-02T09:00:00Z'),
  lastSeenAt: wireInstant('2026-10-12T12:00:00Z'),
  teams: ['developers', 'release'],
  instanceRole: true,
  tokens: []
}

const CI = {
  id: 4,
  kind: 'MACHINE',
  name: 'ci-bot',
  displayName: 'CI bot',
  disabled: true,
  createdAt: wireInstant('2026-10-04T09:00:00Z'),
  lastSeenAt: null,
  teams: ['developers'],
  instanceRole: false,
  tokens: [{ name: 'deploy', lastUsedAt: wireInstant('2026-10-14T12:00:00Z'), lastUsedIp: '192.0.2.10' }]
}

// The team tree behind the principals: developers inherits from staff, release stands alone.
const TEAM_TREE = {
  developers: { parents: ['staff'], nodes: [{ node: 'ingot.repository.releases.read', allowed: true }] },
  release: { parents: [], nodes: [{ node: 'ingot.repository.releases.deploy', allowed: true }] },
  staff: { parents: [], nodes: [{ node: 'ingot.admin.dashboard', allowed: false }] }
}

const LEGACY_TOKEN = {
  name: 'ci-bot',
  routes: [
    { path: '/releases', permission: { identifier: 'route:read' } },
    { path: '/releases', permission: { identifier: 'route:write' } },
    { path: '/snapshots', permission: { identifier: 'route:read' } }
  ]
}

describe('PrincipalDetail', () => {
  let wrapper
  let principals
  let tokensApi
  let legacyGet
  let nodes
  let teams
  let router

  const mountDetail = async (props) => {
    router = await makeRouter()
    wrapper = mount(PrincipalDetail, { props, attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()
  }

  beforeEach(() => {
    setClock(() => new Date(Date.UTC(2026, 9, 15, 12)))
    principals = {
      get: vi.fn(async (name) => ({ data: name === 'alice' ? ALICE : CI, headers: { etag: '"1"' } })),
      update: vi.fn(),
      setPassword: vi.fn(),
      grantInstanceAdmin: vi.fn(),
      revokeInstanceAdmin: vi.fn(),
      remove: vi.fn()
    }
    tokensApi = {
      list: vi.fn().mockResolvedValue({ data: CI.tokens }),
      create: vi.fn(),
      revoke: vi.fn()
    }
    legacyGet = vi.fn().mockResolvedValue({ data: LEGACY_TOKEN })
    nodes = makeNodesApi()
    nodes.get.mockImplementation(async (kind, name) => answer({
      nodes: (kind === 'teams' ? TEAM_TREE[name].nodes : NODES).map(entry => ({ ...entry }))
    }, '"n1"'))
    teams = {
      parents: vi.fn(async (name) => answer({ parents: [...TEAM_TREE[name].parents] }, '"p1"'))
    }
    holder.session = {
      client: ref({
        identity: { principals, principalTokens: vi.fn(() => tokensApi), nodes, teams },
        tokens: { get: legacyGet }
      })
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
    resetClock()
  })

  describe('a user', () => {
    it('reads the detail of the named principal', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(principals.get).toHaveBeenCalledWith('alice')
      expect(wrapper.find('#principal-title').text()).toBe('Alice Example')
      expect(wrapper.find('[data-test="username"]').text()).toBe('alice')
    })

    it('shows the teams as chips that link to the team', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      const chips = wrapper.findAll('[data-test="team"]')

      expect(chips.map(chip => chip.text())).toEqual(['developers', 'release'])
      expect(chips.map(chip => chip.attributes('href'))).toEqual(['/access/teams/developers', '/access/teams/release'])
    })

    it('shows the instance admin state and the badge', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="instance-admin"]').text()).toBe('Yes')
      expect(wrapper.find('[data-test="admin-badge"]').exists()).toBe(true)
    })

    it('shows the active state of an enabled user', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="status"]').text()).toBe('Active')
      expect(wrapper.find('[data-test="disabled-badge"]').exists()).toBe(false)
    })

    it('shows the last sign-in relative to the clock with the absolute time as tooltip', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      const activity = wrapper.find('[data-test="last-activity"]')

      expect(activity.text()).toBe('3 days ago')
      expect(activity.attributes('title')).toBe('2026-10-12 12:00 UTC')
    })

    describe('permission nodes', () => {
      it('shows a Permission nodes card with the node editor', async () => {
        await mountDetail({ name: 'alice', kind: 'USER' })

        const card = wrapper.find('[data-test="permission-nodes"]')

        expect(card.find('h4').text()).toBe('Permission nodes')
        expect(card.find('[data-test="node-editor"]').exists()).toBe(true)
      })

      it('loads the nodes of the user from the principals collection', async () => {
        await mountDetail({ name: 'alice', kind: 'USER' })

        expect(nodes.get).toHaveBeenCalledWith('principals', 'alice')
        expect(wrapper.findAll('[data-test="node-row"]')).toHaveLength(NODES.length)
      })

      it('saves the edited nodes with the entity tag of the nodes', async () => {
        await mountDetail({ name: 'alice', kind: 'USER' })

        await wrapper.find('button[aria-label="Remove ingot.repository.private.read"]').trigger('click')
        await wrapper.findAll('button').find(button => button.text() === 'Save').trigger('click')
        await flushPromises()

        expect(nodes.set).toHaveBeenCalledWith(
          'principals',
          'alice',
          [{ node: 'ingot.repository.releases.read', allowed: true }],
          '"n1"'
        )
      })

      it('reads the user again after the nodes were saved and tells the list', async () => {
        await mountDetail({ name: 'alice', kind: 'USER' })
        principals.get.mockClear()

        await wrapper.find('button[aria-label="Remove ingot.repository.private.read"]').trigger('click')
        await wrapper.findAll('button').find(button => button.text() === 'Save').trigger('click')
        await flushPromises()

        expect(principals.get).toHaveBeenCalledTimes(1)
        expect(wrapper.emitted('changed')).toHaveLength(1)
      })

      it('keeps the actions menu next to the card', async () => {
        await mountDetail({ name: 'alice', kind: 'USER' })

        expect(wrapper.find('[data-test="actions"]').exists()).toBe(true)
      })

      it('has no role or grant editor', async () => {
        await mountDetail({ name: 'alice', kind: 'USER' })

        for (const selector of ['grants', 'add-grant', 'edit-grant', 'save-grants']) {
          expect(wrapper.find(`[data-test="${selector}"]`).exists(), selector).toBe(false)
        }
      })
    })

    it('has the actions of a user and no token section', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="actions"]').exists()).toBe(true)
      expect(wrapper.text()).not.toContain('Create token')
      expect(legacyGet, 'a user has no migrated access token').not.toHaveBeenCalled()
    })

    it('says so when the user does not exist', async () => {
      principals.get.mockRejectedValue(Object.assign(new Error('missing'), { response: { status: 404 } }))
      await mountDetail({ name: 'ghost', kind: 'USER' })

      expect(wrapper.find('[data-test="missing"]').text()).toContain('ghost')
    })

    it('asks the list to close the detail from the back button', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      await wrapper.find('[data-test="back"]').trigger('click')

      expect(wrapper.emitted('back')).toHaveLength(1)
    })
  })

  describe('the check permission action', () => {
    it('links a user to the permission check with the principal in the query', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="check-permission"]').attributes('href')).toBe('/access/check?principal=alice')
    })

    it('navigates to the permission check for a machine account', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      await wrapper.find('[data-test="check-permission"]').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('access-check')
      expect(router.currentRoute.value.query).toEqual({ principal: 'ci-bot' })
    })

    it('is not a filled button', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="check-permission"]').attributes('data-variant')).toBe('outlined')
    })
  })

  describe('a machine account', () => {
    it('lists the tokens of the machine through the principal token api', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      expect(holder.session.client.value.identity.principalTokens).toHaveBeenCalledWith('ci-bot')
      expect(tokensApi.list).toHaveBeenCalledTimes(1)
      expect(wrapper.findAll('[data-test="token-row"]')).toHaveLength(1)
    })

    it('offers no rotate on a token, because the principal token api has none', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      expect(wrapper.find('[aria-label="Rotate deploy"]').exists()).toBe(false)
      expect(wrapper.find('[aria-label="Revoke deploy"]').exists()).toBe(true)
    })

    it('shows the last use and the address of a token', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      const row = wrapper.find('[data-test="token-row"]')

      expect(row.find('[data-test="last-used"]').text()).toContain('1 day ago')
      expect(row.find('[data-test="last-used"]').attributes('title')).toBe('2026-10-14 12:00 UTC')
      expect(row.find('[data-test="last-used-ip"]').text()).toContain('192.0.2.10')
    })

    it('shows the last use of the machine account in the facts', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      expect(wrapper.find('[data-test="last-activity"]').text()).toBe('1 day ago')
    })

    it('shows the disabled state', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      expect(wrapper.find('[data-test="status"]').text()).toBe('Disabled')
      expect(wrapper.find('[data-test="disabled-badge"]').exists()).toBe(true)
    })

    it('shows the teams as chips that link to the team', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      expect(wrapper.find('[data-test="team"]').attributes('href')).toBe('/access/teams/developers')
    })

    describe('permission nodes', () => {
      it('loads the nodes of the machine account from the principals collection', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        expect(nodes.get).toHaveBeenCalledWith('principals', 'ci-bot')
        expect(wrapper.find('[data-test="permission-nodes"] [data-test="node-editor"]').exists()).toBe(true)
      })

      it('shows the nodes card before the legacy routes', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        const html = wrapper.html()

        expect(html.indexOf('data-test="permission-nodes"')).toBeLessThan(html.indexOf('data-test="legacy-grants"'))
      })
    })

    describe('legacy routes', () => {
      it('reads the routes of the migrated token by the machine name', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        expect(legacyGet).toHaveBeenCalledWith('ci-bot')
      })

      it('shows the panel labeled Legacy routes with the routes', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        const panel = wrapper.find('[data-test="legacy-grants"]')
        const grants = panel.findAll('[data-test="legacy-grant"]')

        expect(panel.find('h3').text()).toBe('Legacy routes')
        expect(grants.map(grant => grant.text())).toEqual(['/releasesReadWrite', '/snapshotsRead'])
      })

      it('says that the routes are read only and still apply', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        const panel = wrapper.find('[data-test="legacy-grants"]')

        expect(panel.text()).toContain('still apply')
        expect(panel.text()).toContain('read only')
      })

      it('is read only: it holds no input, no button, no link and no editor', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        const panel = wrapper.find('[data-test="legacy-grants"]')

        expect(panel.findAll('input, button, textarea, select, a')).toHaveLength(0)
      })

      it('shows no team hint next to the legacy panel', async () => {
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        expect(wrapper.find('[data-test="team-rights-hint"]').exists()).toBe(false)
      })

      it('shows the hint that the rights come from the teams when there is no migrated token', async () => {
        legacyGet.mockRejectedValue(Object.assign(new Error('missing'), { response: { status: 404 } }))
        await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

        expect(wrapper.find('[data-test="team-rights-hint"]').text())
          .toBe("This token's rights come from the teams of this machine account.")
        expect(wrapper.find('[data-test="legacy-grants"]').exists()).toBe(false)
      })
    })
  })

  describe('inherited nodes', () => {
    const inheritedRows = () => wrapper.findAll('[data-test="inherited-row"]').map(row => ({
      node: row.attributes('data-node'),
      source: row.find('[data-test="inherited-source"]').text(),
      state: row.find('[data-test="inherited-state"]').text()
    }))

    it('shows the nodes of the teams of a user as from the team, with the ancestors one level up', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(inheritedRows()).toEqual([
        { node: 'ingot.repository.releases.read', source: 'from team developers', state: 'Allowed' },
        { node: 'ingot.repository.releases.deploy', source: 'from team release', state: 'Allowed' },
        { node: 'ingot.admin.dashboard', source: 'from team staff, 1 level up', state: 'Denied' }
      ])
    })

    it('shows the same for a machine account', async () => {
      await mountDetail({ name: 'ci-bot', kind: 'MACHINE' })

      expect(inheritedRows().map(row => row.source)).toEqual(['from team developers', 'from team staff, 1 level up'])
    })

    it('takes the teams from the detail of the principal and reads each team and its parents', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(nodes.get).toHaveBeenCalledWith('teams', 'developers')
      expect(nodes.get).toHaveBeenCalledWith('teams', 'release')
      expect(nodes.get).toHaveBeenCalledWith('teams', 'staff')
      expect(teams.parents).toHaveBeenCalledWith('developers')
    })

    it('keeps the direct nodes apart from the inherited ones', async () => {
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.findAll('[data-test="node-row"]')).toHaveLength(NODES.length)
    })

    it('shows no inherited section for a principal without teams', async () => {
      principals.get.mockResolvedValue({ data: { ...ALICE, teams: [] }, headers: { etag: '"1"' } })
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="inherited-section"]').exists()).toBe(false)
    })

    it('warns about a team that cannot be read and still shows the page and the other teams', async () => {
      nodes.get.mockImplementation(async (kind, name) => {
        if (kind === 'teams' && name === 'release') throw failure(404, "Team 'release' not found")
        return answer({ nodes: kind === 'teams' ? TEAM_TREE[name].nodes : NODES }, '"n1"')
      })
      await mountDetail({ name: 'alice', kind: 'USER' })

      expect(wrapper.find('[data-test="inherited-warning"]').text())
        .toBe("Could not read the nodes of team release: Team 'release' not found")
      expect(inheritedRows().map(row => row.source)).toEqual(['from team developers', 'from team staff, 1 level up'])
      expect(wrapper.find('#principal-title').text()).toBe('Alice Example')
    })
  })
})
