import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import download from 'downloadjs'
import AuditView from './AuditView.vue'

// The client is swapped per test through this holder, as in the token view tests.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

vi.mock('downloadjs', () => ({ default: vi.fn() }))

// A fixed reference time, so the presets never depend on the day the suite runs.
const NOW = Date.parse('2026-10-15T12:00:00Z')

const event = (overrides = {}) => ({
  id: 1,
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

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

const page = (events, nextCursor = null) => Promise.resolve({ data: { events, nextCursor } })

const fakeClient = (overrides = {}) => ({
  audit: {
    list: vi.fn(() => page([])),
    exportCsv: vi.fn(() => Promise.resolve({ data: new Blob(['csv']) })),
    ...overrides
  }
})

const createTestRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'overview', component: { render: () => null } },
    { path: '/browse/:qualifier(.*)*', name: 'browse', component: { render: () => null } },
    { path: '/tokens/:name', name: 'token', component: { render: () => null } },
    { path: '/access/users/:name', component: { render: () => null } },
    { path: '/access/machines/:name', component: { render: () => null } },
    { path: '/audit', name: 'audit', component: { render: () => null } },
  ]
})

const mountView = async (props = {}) => {
  const router = createTestRouter()
  await router.push('/audit')
  const wrapper = mount(AuditView, {
    props: { query: {}, now: NOW, ...props },
    global: { plugins: [router] },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  session.client = fakeClient()
  vi.mocked(download).mockClear()
})

describe('AuditView loading', () => {
  it('asks for the events of the last 24 hours when the address names that preset', async () => {
    session.client = fakeClient()

    await mountView({ query: { range: '24h' } })

    expect(session.client.audit.list).toHaveBeenCalledWith({ from: '2026-10-14T12:00:00.000Z' })
  })

  it('sends no time bounds for all time and no filters for an empty address', async () => {
    session.client = fakeClient()

    await mountView({ query: {} })

    expect(session.client.audit.list).toHaveBeenCalledWith({})
  })

  it('shows each event with its action and links the actor and the target', async () => {
    session.client = fakeClient({
      list: vi.fn(() => page([
        event({ id: 1 }),
        event({ id: 2, action: 'TOKEN_CREATED', actor: 'admin', repository: null, path: null, details: { token: 'deploy-bot' } }),
      ])),
    })

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Artifact deployed')
    expect(wrapper.text()).toContain('Token created')
    const hrefs = wrapper.findAll('a').map(link => link.attributes('href'))
    expect(hrefs).toContain('/browse/releases/org/example/app-1.0.jar')
    expect(hrefs).toContain('/access/users/admin')
    expect(hrefs).toContain('/tokens/deploy-bot')
  })

  it('says so when no event matches the filters', async () => {
    const wrapper = await mountView({ query: { action: 'LOGIN_FAILED' } })

    expect(wrapper.text()).toContain('No events match these filters.')
  })

  it('shows the error with a retry, and loads the list again on retry', async () => {
    session.client = fakeClient({
      list: vi.fn()
        .mockImplementationOnce(() => Promise.reject(new Error('Network is down')))
        .mockImplementationOnce(() => page([event()])),
    })
    const wrapper = await mountView()

    expect(wrapper.find('[role="alert"]').text()).toContain('Network is down')

    await wrapper.findAll('button').find(button => button.text() === 'Retry').trigger('click')
    await flushPromises()

    expect(session.client.audit.list).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Artifact deployed')
  })
})

describe('AuditView paging', () => {
  it('loads the next page with its cursor and adds the rows after the ones on screen', async () => {
    session.client = fakeClient({
      list: vi.fn()
        .mockImplementationOnce(() => page([event({ id: 1 })], 'cursor-2'))
        .mockImplementationOnce(() => page([event({ id: 2, action: 'CLEANUP_RUN', actor: 'system', repository: null, path: null })])),
    })
    const wrapper = await mountView()

    await wrapper.findAll('button').find(button => button.text() === 'Load more').trigger('click')
    await flushPromises()

    expect(session.client.audit.list).toHaveBeenLastCalledWith({ cursor: 'cursor-2' })
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(wrapper.text()).toContain('Cleanup run')
    expect(wrapper.findAll('button').some(button => button.text() === 'Load more')).toBe(false)
  })

  it('ignores a page that answers after a newer request has started', async () => {
    const slow = deferred()
    session.client = fakeClient({
      list: vi.fn()
        .mockImplementationOnce(() => slow.promise)
        .mockImplementationOnce(() => page([event({ id: 9, action: 'TOKEN_DELETED', actor: 'admin', repository: null, path: null, details: { token: 'old-bot' } })])),
    })
    const wrapper = await mountView()

    await wrapper.setProps({ query: { action: 'TOKEN_DELETED' } })
    await flushPromises()
    slow.resolve({ data: { events: [event({ id: 1 })], nextCursor: null } })
    await flushPromises()

    const rows = wrapper.findAll('li')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('Token deleted')
  })
})

describe('AuditView rows', () => {
  it('opens the details of one row and keeps the others closed', async () => {
    session.client = fakeClient({
      list: vi.fn(() => page([
        event({ id: 1, details: { token: 'first' } }),
        event({ id: 2, details: { token: 'second' } }),
      ])),
    })
    const wrapper = await mountView()

    const toggles = wrapper.findAll('button').filter(button => button.text() === 'Details')
    await toggles[0].trigger('click')

    expect(toggles[0].attributes('aria-expanded')).toBe('true')
    expect(toggles[1].attributes('aria-expanded')).toBe('false')
    expect(wrapper.text()).toContain('"token": "first"')
    expect(wrapper.text()).not.toContain('"token": "second"')
  })
})

describe('AuditView filters', () => {
  it('reports a chosen time range through the address and keeps the other filters', async () => {
    const wrapper = await mountView({ query: { actor: 'ci-deployer' } })

    await wrapper.findAll('button').find(button => button.text() === '7 days').trigger('click')

    expect(wrapper.emitted('update:query')).toEqual([[{ actor: 'ci-deployer', range: '7d' }]])
  })

  it('reports a chosen action through the address', async () => {
    const wrapper = await mountView()

    await wrapper.find('select').setValue('LOGIN_FAILED')

    expect(wrapper.emitted('update:query')).toEqual([[{ action: 'LOGIN_FAILED' }]])
  })

  it.each([
    ['Accounts', 'PRINCIPAL_CREATED,PRINCIPAL_UPDATED,PRINCIPAL_DISABLED,PRINCIPAL_DELETED'],
    ['Teams', 'TEAM_CREATED,TEAM_UPDATED,TEAM_DELETED,TEAM_MEMBERSHIP_CHANGED,TEAM_GRANTS_CHANGED'],
    ['Instance admin', 'INSTANCE_ADMIN_GRANTED,INSTANCE_ADMIN_REVOKED'],
  ])('filters by the actions of the %s chip and keeps the other filters', async (chip, actions) => {
    const wrapper = await mountView({ query: { actor: 'admin' } })

    await wrapper.findAll('button').find(button => button.text() === chip).trigger('click')

    expect(wrapper.emitted('update:query')).toEqual([[{ actor: 'admin', action: actions }]])
  })

  it('marks the chip whose actions are the filter, and clears the filter when it is chosen again', async () => {
    const wrapper = await mountView({ query: { action: 'INSTANCE_ADMIN_GRANTED,INSTANCE_ADMIN_REVOKED' } })
    const chip = wrapper.findAll('button').find(button => button.text() === 'Instance admin')

    expect(chip.attributes('aria-checked')).toBe('true')
    await chip.trigger('click')

    expect(wrapper.emitted('update:query')).toEqual([[{}]])
  })

  it('asks the log for the actions of the chip named in the address', async () => {
    session.client = fakeClient()

    await mountView({ query: { action: 'INSTANCE_ADMIN_GRANTED,INSTANCE_ADMIN_REVOKED' } })

    expect(session.client.audit.list).toHaveBeenCalledWith({ action: 'INSTANCE_ADMIN_GRANTED,INSTANCE_ADMIN_REVOKED' })
  })

  it('reports the typed search text when the filters are applied', async () => {
    const wrapper = await mountView()

    await wrapper.find('form input').setValue('  deploy-bot ')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('update:query')).toEqual([[{ q: 'deploy-bot' }]])
  })

  it('shows the custom range fields only for the custom range', async () => {
    const wrapper = await mountView({ query: { range: 'custom', from: '2026-10-01T00:00:00.000Z' } })

    const inputs = wrapper.findAll('input[type="datetime-local"]')
    expect(inputs).toHaveLength(2)
    expect(inputs[0].element.value).not.toBe('')
  })
})

describe('AuditView export', () => {
  it('exports the events that match the filters as a CSV file', async () => {
    const wrapper = await mountView({ query: { action: 'ARTIFACT_DEPLOYED', range: '24h' } })

    await wrapper.findAll('button').find(button => button.text() === 'Export CSV').trigger('click')
    await flushPromises()

    expect(session.client.audit.exportCsv).toHaveBeenCalledWith({
      action: 'ARTIFACT_DEPLOYED',
      from: '2026-10-14T12:00:00.000Z',
    })
    expect(download).toHaveBeenCalledWith(expect.any(Blob), 'ingot-audit.csv', 'text/csv')
  })
})
