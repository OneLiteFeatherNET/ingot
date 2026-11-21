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
import download from 'downloadjs'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import { settingsClockKey } from './settingsExport'
import MaintenanceView from '../maintenance/MaintenanceView.vue'

type ClientRef = Parameters<typeof createConfigurationStore>[0]
type ConfigurationStore = ReturnType<typeof createConfigurationStore>

vi.mock('downloadjs', () => ({ default: vi.fn() }))
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: Error | undefined) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' }
        },
        additionalProperties: false
      }
    }
  }
}

const SERVER: Record<string, unknown> = {
  maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] }
}

// The encrypted file the server returns for an export. The fixture records the password it was asked with.
const ENCRYPTED = { format: 'ingot-settings-export', version: 1, ciphertext: 'opaque' }
const exportRequests: string[] = []

const fakeClient = () => ({
  settings: {
    domains: () => Promise.resolve({ data: Object.keys(SERVER) }),
    schema: () => Promise.resolve({ data: SCHEMA }),
    fetch: (domain: string) => Promise.resolve({ data: JSON.parse(JSON.stringify(SERVER[domain])) as unknown }),
    update: () => Promise.resolve({ data: {} }),
    exportSaved: (password: string) => {
      exportRequests.push(password)
      return Promise.resolve({ data: ENCRYPTED })
    }
  }
})

const FIXED_NOW = new Date(2026, 9, 15, 12, 0)

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: stubRoutes()
})

let wrapper: VueWrapper | undefined

const mountWith = async ({ secretsHidden = false } = {}) => {
  const store = createConfigurationStore(ref(fakeClient()) as unknown as ClientRef, { secretsHidden: () => secretsHidden })
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  // The download is an action of the Settings backup tab, so the view is opened at /maintenance?tab=backup.
  const router = namedRouter()
  await router.push('/maintenance?tab=backup')
  wrapper = mount(MaintenanceView, {
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store, [settingsClockKey]: () => FIXED_NOW }
    },
    attachTo: document.body
  })
  await flushPromises()
  return store
}

const openMenu = async () => {
  await wrapper!.get('button[aria-haspopup="menu"]').trigger('click')
  await flushPromises()
}

// The download menu is teleported to the body, so its items are read from the document.
const menuItem = (name: string) =>
  [...document.querySelectorAll<HTMLElement>('[role="menu"] [role="menuitem"]')].find(entry => entry.textContent?.includes(name))

const choose = async (name: string) => {
  const item = menuItem(name)
  expect(item, `the menu offers "${name}"`).toBeDefined()
  item!.click()
  await flushPromises()
}

const downloadedJson = () => JSON.parse(vi.mocked(download).mock.calls.at(-1)![0] as string) as unknown

// The password dialog is teleported to the body, so its fields are reached through the document.
const passwordDialog = () => document.getElementById('settings-password')?.closest('form') ?? null

const typeInto = (id: string, value: string) => {
  const input = document.getElementById(id) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const fillPassword = async (password: string) => {
  await flushPromises()
  typeInto('settings-password', password)
  typeInto('settings-password-confirmation', password)
  await flushPromises()
  passwordDialog()!.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

const editFirstRepository = (store: ConfigurationStore) => {
  store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE' })
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

// The read-only switch of Maintenance has its own tests; here it is a marker, so these tests read no mode.
vi.mock('../dashboard/ReadOnlyToggle.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div') } }
})

// The status block of Overview has its own tests (DashboardView); here it is a marker, so these tests fetch no status.
vi.mock('../dashboard/DashboardView.vue', async () => {
  const { h } = await import('vue')
  return { default: { render: () => h('div', { 'data-test': 'status-block' }) } }
})

describe('Settings backup download', () => {
  it('asks for a password before the saved state is downloaded', async () => {
    await mountWith()

    await openMenu()
    await choose('Download saved settings')

    await flushPromises()
    expect(passwordDialog(), 'the password dialog is open').not.toBeNull()
    expect(download).not.toHaveBeenCalled()
  })

  it('downloads the encrypted file of the saved state with the password it was given', async () => {
    const store = await mountWith()
    editFirstRepository(store)
    await flushPromises()

    await openMenu()
    await choose('Download saved settings')
    await fillPassword('twelve-chars!')

    expect(exportRequests).toEqual(['twelve-chars!'])
    expect(downloadedJson()).toEqual(ENCRYPTED)
    expect(JSON.stringify(vi.mocked(download).mock.calls)).not.toContain('PRIVATE')
  })

  it('names the saved download after the day of the injected clock', async () => {
    await mountWith()

    await openMenu()
    await choose('Download saved settings')
    await fillPassword('twelve-chars!')

    expect(vi.mocked(download).mock.calls.at(-1)![1]).toBe('ingot-settings-2026-10-15.json')
  })

  it('does not offer the draft, because the draft is not part of the encrypted file', async () => {
    const store = await mountWith()
    editFirstRepository(store)
    await flushPromises()

    await openMenu()
    const draftItem = menuItem('Download including unsaved changes')

    expect(draftItem?.getAttribute('aria-disabled'), 'the draft option is marked disabled').toBe('true')
  })

  it('keeps the saved download available while the draft has invalid entries', async () => {
    const store = await mountWith()
    store.setEntry('maven', 'repositories', 0, { id: '', visibility: 'PRIVATE' })
    await flushPromises()

    await openMenu()
    const savedItem = menuItem('Download saved settings')

    expect(savedItem, 'the saved download is offered').toBeDefined()
    expect(savedItem!.getAttribute('aria-disabled'), 'the saved download can be chosen').toBeNull()
  })

  it('says secrets are not included when the server hides them', async () => {
    await mountWith({ secretsHidden: true })

    await openMenu()

    expect(document.querySelector('[role="menu"]')?.textContent).toContain('Secrets are not included')
  })
})
