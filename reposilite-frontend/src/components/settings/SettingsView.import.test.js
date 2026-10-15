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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'

vi.mock('downloadjs', () => ({ default: vi.fn() }))
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
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
        title: 'Repository',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', enum: ['PUBLIC', 'PRIVATE'], title: 'Visibility' }
        },
        additionalProperties: false
      }
    }
  }
}

const initialServer = () => ({
  maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] }
})

let server
let writes

const fakeClient = () => ({
  settings: {
    domains: async () => ({ data: Object.keys(server) }),
    schema: async () => ({ data: SCHEMA }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(server[domain])) }),
    update: async (domain, content) => {
      writes.push({ domain, content })
      return { data: {} }
    }
  }
})

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
  ]
})

let wrapper

const mountView = async () => {
  server = initialServer()
  writes = []
  const store = createConfigurationStore(ref(fakeClient()), { secretsHidden: () => false })
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings' },
    global: {
      plugins: [namedRouter()],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return store
}

/*
 * Picks a file in the file input. The file's text is given directly, because the view reads it with
 * Blob.text() and the test does not depend on how the test DOM implements the File API.
 */
const pickFile = async (text) => {
  const input = wrapper.get('input[type="file"]')
  const file = new File([text], 'settings.json', { type: 'application/json' })
  file.text = async () => text
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

const sheet = () => document.querySelector('[role="dialog"][aria-labelledby]')

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('SettingsView import', () => {
  it('loads a valid file into the draft and opens the review, without saving', async () => {
    const store = await mountView()

    await pickFile(JSON.stringify({ maven: { repositories: [{ id: 'releases', visibility: 'PRIVATE' }] } }))

    expect(store.configurations.value.maven.repositories[0].visibility).toBe('PRIVATE')
    expect(store.hasUnsavedChanges.value).toBe(true)
    expect(writes, 'nothing is written before the user saves').toEqual([])
    expect(sheet(), 'the review shows the imported changes').not.toBeNull()
    expect(sheet().textContent).toContain('Visibility')
  })

  it('shows the errors per domain and field, and loads nothing when the file is invalid', async () => {
    const store = await mountView()

    await pickFile(JSON.stringify({ maven: { repositories: [{ id: 'releases', visibility: 'SECRET' }] } }))

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('Repositories > releases > Visibility')
    expect(store.configurations.value.maven.repositories[0].visibility).toBe('PUBLIC')
    expect(store.hasUnsavedChanges.value).toBe(false)
  })

  it('reports a file that is not JSON at all', async () => {
    await mountView()

    await pickFile('{ "maven": ')

    expect(wrapper.get('[role="alert"]').text()).toContain('not valid JSON')
  })
})
