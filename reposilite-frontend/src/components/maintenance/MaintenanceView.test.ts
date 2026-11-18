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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { shallowReactive } from 'vue'
import MaintenanceView from './MaintenanceView.vue'

// Reactive, as the store is: a permission that arrives after the page is drawn must change the tabs.
const session = shallowReactive({ client: null as unknown as ReturnType<typeof fakeClient>, mayEdit: true })

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } }, can: () => session.mayEdit })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error: Error | undefined) => error?.message ?? `${error}`
}))

const POLICY = {
  id: 'snapshots-weekly',
  enabled: true,
  repositories: ['snapshots'],
  scope: 'SNAPSHOTS',
  keepLatest: 3,
  olderThanDays: null,
  notDownloadedForDays: null,
  groupFilter: null,
  schedule: 'manual'
}

const fakeClient = () => ({
  maven: {
    storage: vi.fn(() => Promise.resolve({
      data: { computedAt: '2026-10-15T09:30:00Z', computing: false, totalBytes: 0, repositories: [] }
    })),
    searchIndex: vi.fn(() => Promise.resolve({
      data: { state: 'READY', lagSeconds: 0, rows: 0, outbox: { pending: 0, deadLetters: 0, droppedEnqueues: 0 }, repositories: [], runs: [] }
    }))
  },
  settings: {
    fetch: vi.fn((name: string) => Promise.resolve({
      data: name === 'cleanup' ? { policies: [POLICY] } : { repositories: [{ id: 'snapshots' }] }
    })),
    update: vi.fn(() => Promise.resolve({ data: '' }))
  },
  readOnly: {
    get: vi.fn(() => Promise.resolve({ status: 200, data: { enabled: false, source: 'stored' } }))
  },
  cleanup: {
    runs: vi.fn(() => Promise.resolve({ data: [] })),
    dryRun: vi.fn(),
    run: vi.fn(() => Promise.resolve({
      data: { id: 1, policyId: 'snapshots-weekly', trigger: 'MANUAL', startedAt: '2026-10-15T10:00:00Z', status: 'SUCCEEDED', deletedCount: 0, deletedBytes: 0, failures: [] }
    }))
  }
})

const BLANK = { render: () => null }

let wrapper: VueWrapper

beforeEach(() => {
  session.client = fakeClient()
  session.mayEdit = true
})

afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
})

// Mounts the page at an address. Every mount gets its own memory router, so the tab in the address is read per test.
const mountAt = async (address: string) => {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/maintenance', name: 'maintenance', component: BLANK },
      { path: '/settings/system-info', name: 'system-info', component: BLANK },
      { path: '/settings/jobs', name: 'jobs', component: BLANK },
    ],
  })
  await router.push(address)
  await router.isReady()
  wrapper = mount(MaintenanceView, { attachTo: document.body, global: { plugins: [router] } }) as VueWrapper
  await flushPromises()
  return { router, wrapper }
}

const tabsOf = (view: VueWrapper) => view.findAll('[role="tab"]')
const selectedTabOf = (view: VueWrapper) => tabsOf(view).find(tab => tab.attributes('aria-selected') === 'true')
const shownPanelOf = (view: VueWrapper) => view.find('[role="tabpanel"]:not([hidden])')
const tabNamed = (view: VueWrapper, label: string) => tabsOf(view).find(tab => tab.text() === label)!

describe('MaintenanceView', () => {
  it('shows the read-only switch in a section labelled Writes, above the other panels', async () => {
    await mountAt('/maintenance')

    const section = wrapper.find('section[aria-labelledby]')
    expect(section.exists(), 'the switch sits in its own section').toBe(true)
    expect(section.text(), 'the section is labelled Writes').toContain('Writes')
    expect(section.find('[role="switch"]').exists(), 'a principal with the maintenance area sees the switch').toBe(true)
    const storageHeading = wrapper.findAll('h3').find(heading => heading.text().includes('Storage usage'))
    expect(section.element.compareDocumentPosition(storageHeading!.element) & Node.DOCUMENT_POSITION_FOLLOWING, 'the section comes before the storage panel').toBeTruthy()
  })

  it('hides the Writes section and the switch when the probe answers 403', async () => {
    session.client = {
      ...fakeClient(),
      readOnly: {
        get: vi.fn(() => Promise.reject(Object.assign(new Error('Forbidden'), { response: { status: 403, data: {} } })))
      }
    }
    await mountAt('/maintenance')

    expect(wrapper.find('#read-only-switch').exists(), 'a principal without the maintenance area sees no read-only switch').toBe(false)
    expect(wrapper.text(), 'and no Writes section either').not.toContain('Writes')
  })

  it('shows the tabs in the order Storage usage, Search index, Cleanup policies and Settings backup', async () => {
    await mountAt('/maintenance')

    expect(tabsOf(wrapper).map(tab => tab.text())).toEqual(['Storage usage', 'Search index', 'Cleanup policies', 'Settings backup'])
  })

  it('opens the storage usage tab when the address has no tab', async () => {
    await mountAt('/maintenance')

    expect(selectedTabOf(wrapper)?.text(), 'the first tab is selected').toBe('Storage usage')
    expect(shownPanelOf(wrapper).text(), 'the storage figures are shown').toContain('Storage usage')
  })

  it('opens the search index panel for the address ?tab=index', async () => {
    await mountAt('/maintenance?tab=index')

    expect(selectedTabOf(wrapper)?.text(), 'the search index tab is selected').toBe('Search index')
    expect(shownPanelOf(wrapper).find('#search-index-title').exists(), 'the search index panel is the one shown').toBe(true)
  })

  it('opens the storage usage tab for an unknown tab in the address', async () => {
    await mountAt('/maintenance?tab=bogus')

    expect(selectedTabOf(wrapper)?.text(), 'an unknown tab falls back to the first tab').toBe('Storage usage')
    expect(session.client.maven.storage, 'the storage figures are read for the first tab').toHaveBeenCalledTimes(1)
  })

  it('loads the search index only when its tab is first opened', async () => {
    await mountAt('/maintenance')
    expect(session.client.maven.searchIndex, 'the index is not read while its tab is closed').not.toHaveBeenCalled()

    await tabNamed(wrapper, 'Search index').trigger('click')
    await flushPromises()
    expect(session.client.maven.searchIndex, 'the index is read once its tab is opened').toHaveBeenCalledTimes(1)

    await tabNamed(wrapper, 'Storage usage').trigger('click')
    await flushPromises()
    await tabNamed(wrapper, 'Search index').trigger('click')
    await flushPromises()
    expect(session.client.maven.searchIndex, 'switching back does not read it again').toHaveBeenCalledTimes(1)
  })

  it('loads the cleanup runs only when the cleanup policies tab is first opened', async () => {
    await mountAt('/maintenance')
    expect(session.client.cleanup.runs, 'the runs are not read while the tab is closed').not.toHaveBeenCalled()

    await tabNamed(wrapper, 'Cleanup policies').trigger('click')
    await flushPromises()

    expect(session.client.cleanup.runs, 'the runs are read once the tab is opened').toHaveBeenCalledTimes(1)
  })

  it('does not read the storage usage while another tab is opened from the address', async () => {
    await mountAt('/maintenance?tab=index')

    expect(session.client.maven.storage, 'the storage figures wait for their tab').not.toHaveBeenCalled()
  })

  it('reloads the storage figures once a run has finished', async () => {
    await mountAt('/maintenance')
    await tabNamed(wrapper, 'Cleanup policies').trigger('click')
    await flushPromises()
    void wrapper.findAll('button').find(button => button.attributes('aria-label') === 'Run snapshots-weekly now')!.trigger('click')
    await flushPromises()
    const runInput = document.getElementById('run-now-input') as HTMLInputElement
    runInput.value = 'snapshots-weekly'
    runInput.dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmButton = document.getElementById('run-now-confirm') as HTMLElement
    confirmButton.click()
    await flushPromises()

    expect(session.client.maven.storage).toHaveBeenCalledTimes(2)
  })
})

describe('the Settings backup tab', () => {
  it('shows no Settings backup tab to a principal without the settings node', async () => {
    session.mayEdit = false
    await mountAt('/maintenance')

    expect(tabsOf(wrapper).map(tab => tab.text()), 'the three other tabs remain').toEqual(['Storage usage', 'Search index', 'Cleanup policies'])
  })

  it('opens the first tab for ?tab=backup to a principal without the settings node', async () => {
    session.mayEdit = false
    await mountAt('/maintenance?tab=backup')

    expect(selectedTabOf(wrapper)?.text(), 'the backup tab does not exist for this principal').toBe('Storage usage')
  })

  it('shows the Settings backup tab once the permission arrives after a direct visit to ?tab=backup', async () => {
    session.mayEdit = false
    await mountAt('/maintenance?tab=backup')
    expect(tabsOf(wrapper).map(tab => tab.text())).not.toContain('Settings backup')

    session.mayEdit = true
    await flushPromises()

    expect(tabsOf(wrapper).map(tab => tab.text()), 'the backup tab is added').toContain('Settings backup')
    expect(selectedTabOf(wrapper)?.text(), 'the address opens the backup tab').toBe('Settings backup')
  })

  it('opens the Settings backup tab for the address ?tab=backup', async () => {
    await mountAt('/maintenance?tab=backup')

    expect(selectedTabOf(wrapper)?.text(), 'the settings backup tab is selected').toBe('Settings backup')
    expect(shownPanelOf(wrapper).exists(), 'the tab has a panel').toBe(true)
  })

  it('draws Import settings and Download as JSON on the Settings backup tab', async () => {
    await mountAt('/maintenance?tab=backup')

    const labels = shownPanelOf(wrapper).findAll('button').map(button => button.text())
    expect(labels, 'both actions of the whole configuration').toEqual(expect.arrayContaining(['Import settings', 'Download as JSON']))
  })

  it('explains the download and the import in one sentence each', async () => {
    await mountAt('/maintenance?tab=backup')

    const text = shownPanelOf(wrapper).text()
    expect(text, 'the download sentence says how secrets are handled').toContain('encrypted with a password')
    expect(text, 'the import sentence says what it replaces').toContain('Replaces')
  })

  it('links to the factory reset on System and support', async () => {
    await mountAt('/maintenance?tab=backup')

    const link = shownPanelOf(wrapper).findAll('a').find(anchor => anchor.text().includes('factory reset'))
    expect(link?.text(), 'the sentence names the factory reset on System and support').toBe('To set every setting back to its default, use the factory reset on System and support')
    expect(link?.attributes('href'), 'the link opens the Overview of System and support').toBe('/settings/system-info?tab=overview')
  })
})
