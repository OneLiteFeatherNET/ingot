import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import AuditEventRow from './AuditEventRow.vue'

const event = (overrides = {}) => ({
  id: 7,
  timestamp: '2026-10-15T09:00:00Z',
  actor: 'ci-deployer',
  actorAddress: '203.0.113.10',
  action: 'ARTIFACT_DEPLOYED',
  repository: 'releases',
  path: 'org/example/app-1.0.jar',
  details: null,
  success: true,
  occurrences: 1,
  ...overrides
})

const mountRow = async (props) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/browse/:qualifier(.*)*', name: 'browse', component: { render: () => null } },
      { path: '/tokens/:name', name: 'token', component: { render: () => null } },
      { path: '/access/users/:name', component: { render: () => null } },
      { path: '/access/machines/:name', component: { render: () => null } },
    ]
  })
  await router.push('/')
  return mount(AuditEventRow, { props, global: { plugins: [router] } })
}

describe('AuditEventRow actor', () => {
  it('links an actor to its user page when the kind is not recorded', async () => {
    const row = await mountRow({ event: event() })

    const link = row.findAll('a').find(anchor => anchor.text() === 'ci-deployer')
    expect(link.attributes('href')).toBe('/access/users/ci-deployer')
  })

  it('links a machine account actor to its machine page', async () => {
    const row = await mountRow({ event: event({ actorKind: 'MACHINE' }) })

    const link = row.findAll('a').find(anchor => anchor.text() === 'ci-deployer')
    expect(link.attributes('href')).toBe('/access/machines/ci-deployer')
  })

  it('shows the anonymous and system actors as plain text', async () => {
    const anonymous = await mountRow({ event: event({ actor: 'anonymous' }) })
    const system = await mountRow({ event: event({ actor: 'system', action: 'CLEANUP_RUN', repository: null, path: null }) })

    expect(anonymous.findAll('a').some(anchor => anchor.text() === 'anonymous')).toBe(false)
    expect(system.findAll('a').some(anchor => anchor.text() === 'system')).toBe(false)
    expect(system.text()).toContain('system')
  })

  it('does not link the name of a failed login, because the caller typed it', async () => {
    const row = await mountRow({ event: event({ actor: 'admin', action: 'LOGIN_FAILED', success: false, repository: null, path: null }) })

    expect(row.findAll('a').some(anchor => anchor.text() === 'admin')).toBe(false)
    expect(row.text()).toContain('admin')
  })

  it('names the address the actor came from', async () => {
    const row = await mountRow({ event: event() })

    expect(row.text()).toContain('from 203.0.113.10')
  })
})

describe('AuditEventRow result', () => {
  it('marks a failed event as failed', async () => {
    const row = await mountRow({ event: event({ success: false }) })

    expect(row.text()).toContain('Failed')
  })

  it('counts the attempts of a folded failed login', async () => {
    const row = await mountRow({ event: event({ action: 'LOGIN_FAILED', success: false, occurrences: 7, repository: null, path: null }) })

    expect(row.text()).toContain('7 attempts')
  })

  it('does not count a single attempt', async () => {
    const row = await mountRow({ event: event() })

    expect(row.text()).not.toContain('attempts')
  })
})

describe('AuditEventRow details', () => {
  it('hides the details until the row is opened', async () => {
    const row = await mountRow({ event: event({ details: { token: 'deploy-bot' } }), expanded: false })

    expect(row.find('pre').exists()).toBe(false)
    expect(row.find('button').attributes('aria-expanded')).toBe('false')
  })

  it('shows the recorded details as JSON when the row is open', async () => {
    const row = await mountRow({ event: event({ details: { token: 'deploy-bot', replaced: false } }), expanded: true })

    expect(row.find('pre').text()).toContain('"token": "deploy-bot"')
    expect(row.find('button').attributes('aria-expanded')).toBe('true')
  })

  it('reports the toggle with the id of its event', async () => {
    const row = await mountRow({ event: event({ id: 42 }) })

    await row.find('button').trigger('click')

    expect(row.emitted('toggle')).toEqual([[42]])
  })
})

describe('AuditEventRow identity events', () => {
  const identityEvent = (action, details) => event({ action, actor: 'admin', repository: null, path: null, details })
  const hrefs = (row) => row.findAll('a').map(anchor => anchor.attributes('href'))

  it('shows the label of an identity action', async () => {
    const row = await mountRow({ event: identityEvent('TEAM_MEMBERSHIP_CHANGED', { team: 'devs' }) })

    expect(row.text()).toContain('Team members changed')
  })

  it('links a user target to its user page', async () => {
    const row = await mountRow({ event: identityEvent('PRINCIPAL_UPDATED', { principal: 'alice', kind: 'USER' }) })

    expect(hrefs(row)).toContain('/access/users/alice')
  })

  it('links a machine target to its machine page', async () => {
    const row = await mountRow({ event: identityEvent('PRINCIPAL_CREATED', { principal: 'ci-bot', kind: 'MACHINE' }) })

    expect(hrefs(row)).toContain('/access/machines/ci-bot')
  })

  it('links a team target to its team page', async () => {
    const row = await mountRow({ event: identityEvent('TEAM_UPDATED', { team: 'devs' }) })

    expect(hrefs(row)).toContain('/access/teams/devs')
  })

  it('shows a private machine team as plain text', async () => {
    const row = await mountRow({ event: identityEvent('TEAM_GRANTS_CHANGED', { team: 'machine:ci-bot' }) })

    expect(hrefs(row).some(href => href.startsWith('/access/teams')), 'no link to a team page').toBe(false)
    expect(row.text()).toContain('machine:ci-bot')
  })
})
