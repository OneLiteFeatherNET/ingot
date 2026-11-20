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
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'
import { sectionTabsOf } from '../../navigation/registry'
import search from './fixtures/search.schema.json'
import frontend from './fixtures/frontend.schema.json'

type ClientRef = Parameters<typeof createConfigurationStore>[0]

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: Error | undefined) => error?.message ?? `${error}`
}))

const SCHEMAS = { frontend, search }

const SAVED = {
  verifyEnabled: true,
  verifyAt: '02:30',
  verifySampleSize: 2000,
  rebuildSchedule: { enabled: false, at: '03:30', everyDays: 7 },
  gapRebuild: true
}

// The search settings as a test edits them: any field can be set to any value, valid or not.
type Draft = Record<string, unknown> & { rebuildSchedule: Record<string, unknown> }

// The server answers with a fresh copy of the saved settings. `update` records every write.
const fakeClient = () => {
  const update = vi.fn<(domain: string, body: Draft) => Promise<{ data: object }>>(() => Promise.resolve({ data: {} }))
  return {
    update,
    client: {
      settings: {
        domains: () => Promise.resolve({ data: Object.keys(SCHEMAS) }),
        schema: (domain: keyof typeof SCHEMAS) => Promise.resolve({ data: SCHEMAS[domain] }),
        fetch: (domain: string) => Promise.resolve({ data: domain === 'search' ? structuredClone(SAVED) : {} }),
        update
      }
    }
  }
}

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: stubRoutes()
})

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// Mounts the settings page on one domain, with the address `?tab=` when one is given.
const setup = async ({ domain = 'search', tab }: { domain?: string; tab?: string } = {}) => {
  const fake = fakeClient()
  const store = createConfigurationStore(ref(fake.client) as unknown as ClientRef)
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  const router = namedRouter()
  await router.push({ name: 'settings', params: { domain }, query: tab === undefined ? {} : { tab } })
  await router.isReady()
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', domain },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { store, router, update: fake.update }
}

const tabsOf = () => wrapper!.findAll('[role="tablist"] [role="tab"]')
const tabLabelled = (label: string) => tabsOf().find(tab => tab.text() === label)
// The text of the panel of the selected tab. Panels of other tabs are hidden and their content is not drawn.
const shownPanel = () => {
  const selected = tabsOf().find(tab => tab.attributes('aria-selected') === 'true')
  return document.getElementById(selected!.attributes('aria-controls')!)!.textContent
}
const buttonLabelled = (label: string) => wrapper!.findAll('button').find(button => button.text() === label)

// Writes a draft the way an edit does: the domain's data is replaced, then validated as the page does.
const editDraft = (store: ReturnType<typeof createConfigurationStore>, change: (draft: Draft) => void) => {
  const draft = JSON.parse(JSON.stringify(store.configurations.value.search)) as Draft
  change(draft)
  store.setDomainData('search', draft)
  store.validateDomain('search')
}

describe('the sections of a settings page as tabs', () => {
  it('draws one tab per section, the loose fields first in General', async () => {
    await setup()

    expect(tabsOf().map(tab => tab.text())).toEqual(['General', 'Rebuild schedule'])
  })

  it('draws no tabs for a domain that has not opted in, even when it has sections', async () => {
    expect(sectionTabsOf('search'), 'the search domain opts in').toBe(true)
    expect(sectionTabsOf('frontend'), 'the frontend domain has not opted in').toBe(false)

    await setup({ domain: 'frontend' })

    expect(tabsOf()).toHaveLength(0)
  })

  it('shows only the fields of the General section until another tab is chosen', async () => {
    await setup()

    expect(shownPanel()).toContain('Verify sample size')
    expect(shownPanel()).not.toContain('Every days')
  })

  it('selects the section named by the tab parameter of the address', async () => {
    await setup({ tab: 'rebuild-schedule' })

    expect(tabLabelled('Rebuild schedule')!.attributes('aria-selected')).toBe('true')
    expect(shownPanel()).toContain('Every days')
  })

  it('falls back to General when the tab parameter names no section', async () => {
    await setup({ tab: 'no-such-section' })

    expect(tabLabelled('General')!.attributes('aria-selected')).toBe('true')
    expect(shownPanel()).toContain('Verify sample size')
  })

  it('writes the chosen section into the address', async () => {
    const { router } = await setup()

    await tabLabelled('Rebuild schedule')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.tab).toBe('rebuild-schedule')
  })

  it('marks a tab with an error in its label and counts its errors, while the tab is not shown', async () => {
    const { store } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()

    expect(tabLabelled('Rebuild schedule (1 error)'), 'the label names the error count').toBeDefined()
    expect(tabLabelled('General')!.text(), 'General has no error').toBe('General')
  })

  it('keeps Save disabled while a section has an error', async () => {
    const { store } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()

    expect(buttonLabelled('Save')!.attributes('disabled')).toBeDefined()
  })

  it('switches to the first tab with an error when the save is refused', async () => {
    const { store, router } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()
    await wrapper!.get('[data-test="first-error-link"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.tab).toBe('rebuild-schedule')
    expect(tabLabelled('Rebuild schedule (1 error)')!.attributes('aria-selected')).toBe('true')
  })

  it('names the first tab with an error in the save bar, and links to its address', async () => {
    const { store } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()
    const link = wrapper!.get('[data-test="first-error-link"]')

    expect(link.text()).toBe('Show the first error: Rebuild schedule')
    expect(link.attributes('href'), 'the link names the tab in the address').toContain('tab=rebuild-schedule')
  })

  it('sends every section in one save, whichever tab is shown', async () => {
    const { store, update } = await setup()

    editDraft(store, draft => {
      draft.verifySampleSize = 5000
      draft.rebuildSchedule.enabled = true
    })
    await flushPromises()
    await tabLabelled('Rebuild schedule')!.trigger('click')
    await flushPromises()
    await buttonLabelled('Save')!.trigger('click')
    await flushPromises()

    expect(update).toHaveBeenCalledTimes(1)
    const [domain, body] = update.mock.calls[0]
    expect(domain).toBe('search')
    expect(body.verifySampleSize, 'the General section is saved too').toBe(5000)
    expect(body.rebuildSchedule.enabled, 'the Rebuild schedule section is saved').toBe(true)
  })

  it('discards the edits of every section with one Discard', async () => {
    const { store } = await setup()

    editDraft(store, draft => {
      draft.verifySampleSize = 5000
      draft.rebuildSchedule.enabled = true
    })
    await flushPromises()
    await buttonLabelled('Discard')!.trigger('click')
    await flushPromises()

    expect(store.configurations.value.search.verifySampleSize).toBe(2000)
    expect((store.configurations.value.search as Draft).rebuildSchedule.enabled).toBe(false)
  })
})
