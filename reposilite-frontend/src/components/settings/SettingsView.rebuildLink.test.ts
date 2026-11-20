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

import { stubRoutes } from './fixtures/routes'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'
import search from './fixtures/search.schema.json'

type ClientRef = Parameters<typeof createConfigurationStore>[0]

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: Error | undefined) => error?.message ?? `${error}`
}))

// The dashboard is served below a sub-path, so the router is built with the same base (see MdBreadcrumb.test.ts).
const BASE = '/repo/'

const SAVED = {
  verifyEnabled: true,
  verifyAt: '02:30',
  verifySampleSize: 2000,
  rebuildSchedule: { enabled: false, at: '03:30', everyDays: 7 },
  gapRebuild: true
}

const fakeClient = () => ({
  settings: {
    domains: () => Promise.resolve({ data: ['search'] }),
    schema: () => Promise.resolve({ data: search }),
    fetch: () => Promise.resolve({ data: structuredClone(SAVED) }),
    update: vi.fn(() => Promise.resolve({ data: {} }))
  }
})

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
  window.history.replaceState(null, '', '/')
})

// Mounts the Search index page with its configuration loaded, below the base of the dashboard.
const setup = async () => {
  const store = createConfigurationStore(ref(fakeClient()) as unknown as ClientRef)
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  const router = createRouter({ history: createWebHistory(BASE), routes: stubRoutes() })
  await router.push({ name: 'settings', params: { domain: 'search' } })
  await router.isReady()
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', domain: 'search' },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const REBUILD_TEXT = 'Full rebuild. Rebuilding drops and refills the whole index from storage. It runs on the Maintenance page, where running jobs are shown.'

// The panel of the General section: the tab that holds the rebuild note and the loose fields of the page.
const generalPanel = (page: VueWrapper) => {
  const general = page.findAll('[role="tab"]').find(tab => tab.text() === 'General')
  return document.getElementById(general!.attributes('aria-controls')!)!
}

describe('the rebuild of the search index on the Search index page', () => {
  it('explains what a full rebuild does and where it runs, in the General section', async () => {
    const page = await setup()

    expect(generalPanel(page).textContent).toContain(REBUILD_TEXT)
  })

  it('offers the rebuild as an Open Maintenance link inside the General section', async () => {
    const page = await setup()

    const links = page.findAll('a').filter(anchor => anchor.text().trim() === 'Open Maintenance')
    expect(links, 'one link named Open Maintenance').toHaveLength(1)
    expect(generalPanel(page).contains(links[0].element), 'the link is in the General section').toBe(true)
  })

  it('leads the Open Maintenance link to the Index tab of Maintenance, where the rebuild runs', async () => {
    const page = await setup()

    const [link] = page.findAll('a').filter(anchor => anchor.text().trim() === 'Open Maintenance')
    expect(link.attributes('href')).toBe('/repo/maintenance?tab=index')
  })

  it('draws no rebuild link in the page header, so the link has its context beside it', async () => {
    const page = await setup()

    const header = page.get('header')
    expect(header.findAll('a').map(link => link.text().trim())).not.toContain('Rebuild now on Maintenance')
    expect(header.findAll('a').map(link => link.text().trim())).not.toContain('Open Maintenance')
  })
})
