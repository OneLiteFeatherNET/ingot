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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'
import { sectionTabsOf } from '../../navigation/registry'
import search from './fixtures/search.schema.json'
import frontend from './fixtures/frontend.schema.json'

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMAS = { frontend, search }

const SAVED = {
  verifyEnabled: true,
  verifyAt: '02:30',
  verifySampleSize: 2000,
  rebuildSchedule: { enabled: false, at: '03:30', everyDays: 7 },
  gapRebuild: true
}

// The server answers with a fresh copy of the saved settings. `update` records every write.
const fakeClient = () => {
  const update = vi.fn(async () => ({ data: {} }))
  return {
    update,
    client: {
      settings: {
        domains: async () => ({ data: Object.keys(SCHEMAS) }),
        schema: async (domain) => ({ data: SCHEMAS[domain] }),
        fetch: async (domain) => ({ data: domain === 'search' ? structuredClone(SAVED) : {} }),
        update
      }
    }
  }
}

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: stubRoutes()
})

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// Mounts the settings page on one domain, with the address `?tab=` when one is given.
const setup = async ({ domain = 'search', tab } = {}) => {
  const fake = fakeClient()
  const store = createConfigurationStore(ref(fake.client))
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

const tabsOf = () => wrapper.findAll('[data-ingot-component="app-tabs"] [role="tab"]')
const tabLabelled = (label) => tabsOf().find(tab => tab.text() === label)
const shownPanel = () => wrapper.get('[data-ingot-component="app-tabs"] [role="tabpanel"]:not([hidden])').text()
const buttonLabelled = (label) => wrapper.findAll('button').find(button => button.text() === label)

// Writes a draft the way an edit does: the domain's data is replaced, then validated as the page does.
const editDraft = (store, change) => {
  const draft = JSON.parse(JSON.stringify(store.configurations.value.search))
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

    expect(wrapper.find('[data-ingot-component="app-tabs"]').exists()).toBe(false)
  })

  it('shows only the fields of the General section until another tab is chosen', async () => {
    await setup()

    expect(shownPanel()).toContain('Verify sample size')
    expect(shownPanel()).not.toContain('Every days')
  })

  it('selects the section named by the tab parameter of the address', async () => {
    await setup({ tab: 'rebuild-schedule' })

    expect(tabLabelled('Rebuild schedule').attributes('aria-selected')).toBe('true')
    expect(shownPanel()).toContain('Every days')
  })

  it('falls back to General when the tab parameter names no section', async () => {
    await setup({ tab: 'no-such-section' })

    expect(tabLabelled('General').attributes('aria-selected')).toBe('true')
    expect(shownPanel()).toContain('Verify sample size')
  })

  it('writes the chosen section into the address', async () => {
    const { router } = await setup()

    await tabLabelled('Rebuild schedule').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.tab).toBe('rebuild-schedule')
  })

  it('marks a tab with an error in its label and counts its errors, while the tab is not shown', async () => {
    const { store } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()

    expect(tabLabelled('Rebuild schedule (1 error)'), 'the label names the error count').toBeDefined()
    expect(tabLabelled('General').text(), 'General has no error').toBe('General')
  })

  it('keeps Save disabled while a section has an error', async () => {
    const { store } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()

    expect(buttonLabelled('Save').attributes('disabled')).toBeDefined()
  })

  it('switches to the first tab with an error when the save is refused', async () => {
    const { store, router } = await setup()

    editDraft(store, draft => { draft.rebuildSchedule.everyDays = 'often' })
    await flushPromises()
    await buttonLabelled('Show the first error').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.tab).toBe('rebuild-schedule')
    expect(tabLabelled('Rebuild schedule (1 error)').attributes('aria-selected')).toBe('true')
  })

  it('sends every section in one save, whichever tab is shown', async () => {
    const { store, update } = await setup()

    editDraft(store, draft => {
      draft.verifySampleSize = 5000
      draft.rebuildSchedule.enabled = true
    })
    await flushPromises()
    await tabLabelled('Rebuild schedule').trigger('click')
    await flushPromises()
    await buttonLabelled('Save').trigger('click')
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
    await buttonLabelled('Discard').trigger('click')
    await flushPromises()

    expect(store.configurations.value.search.verifySampleSize).toBe(2000)
    expect(store.configurations.value.search.rebuildSchedule.enabled).toBe(false)
  })
})
