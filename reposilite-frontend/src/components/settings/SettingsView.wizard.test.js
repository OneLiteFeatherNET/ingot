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

vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const MAVEN_SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      type: 'array',
      items: { type: 'object', title: 'Maven repository', properties: { id: { type: 'string', title: 'Id' } } }
    }
  }
}
const SERVER = { maven: { repositories: [{ id: 'releases' }] } }

const fakeClient = () => ({
  settings: {
    domains: async () => ({ data: ['maven'] }),
    schema: async () => ({ data: MAVEN_SCHEMA }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(SERVER[domain])) }),
    update: async () => ({ data: {} })
  }
})

let wrapper

const mountSettings = async () => {
  const store = createConfigurationStore(ref(fakeClient()))
  await store.fetchConfiguration()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
      { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } },
      { name: 'home', path: '/', component: { render: () => null } }
    ]
  })
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', domain: 'maven' },
    global: { plugins: [router], provide: { [configurationStoreKey]: store } },
    attachTo: document.body
  })
  await flushPromises()
}

const wizard = () => document.getElementById('repository-wizard')
const plainDialog = () => document.getElementById('new-entry-form')

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('SettingsView: new maven repository', () => {
  it('opens the wizard and not the plain id dialog', async () => {
    await mountSettings()
    await wrapper.get('[data-test="new-entry"]').trigger('click')
    await flushPromises()

    expect(wizard(), 'the wizard is open').not.toBeNull()
    expect(plainDialog(), 'the plain dialog stays closed').toBeNull()
  })

  it('leaves no dialog open when the wizard is cancelled', async () => {
    await mountSettings()
    await wrapper.get('[data-test="new-entry"]').trigger('click')
    await flushPromises()

    const cancel = [...wizard().querySelectorAll('button')].find(button => button.textContent.trim() === 'Cancel')
    cancel.click()
    await flushPromises()

    expect(wizard(), 'the wizard is closed').toBeNull()
    expect(plainDialog(), 'the plain dialog is closed').toBeNull()
    expect(document.querySelectorAll('form'), 'no dialog content is left on the page').toHaveLength(0)
  })
})
