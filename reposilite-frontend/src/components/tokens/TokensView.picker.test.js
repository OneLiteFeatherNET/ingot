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
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import TokensView from './TokensView.vue'

// The token list and the settings are both served by fakes, so the view is tested against the server's answers.
vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return fakeClient } } })
}))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      type: 'array',
      items: {
        type: 'object',
        properties: { id: { type: 'string' } },
        additionalProperties: false
      }
    }
  }
}

let fakeClient
let wrapper

const settingsServer = () => ({
  domains: vi.fn(async () => ({ data: ['maven'] })),
  schema: vi.fn(async () => ({ data: SCHEMA })),
  fetch: vi.fn(async () => ({ data: { repositories: [{ id: 'releases' }, { id: 'snapshots' }] } })),
  update: vi.fn()
})

const tokensServer = () => ({
  list: vi.fn(async () => ({
    data: [{
      name: 'ci',
      identifier: { type: 'PERSISTENT', value: 1 },
      createdAt: [2026, 1, 1],
      expiresAt: null,
      description: '',
      permissions: [],
      routes: [{ path: '/releases', permission: { identifier: 'route:read' } }]
    }]
  }))
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('TokensView route picker', () => {
  it('offers the saved repositories, and not one that is only drafted in the settings', async () => {
    fakeClient = { settings: settingsServer(), tokens: tokensServer() }
    const configuration = createConfigurationStore(ref(fakeClient))
    await configuration.fetchConfiguration()
    configuration.addEntry('maven', 'repositories', { id: 'draft-repo' })

    wrapper = mount(TokensView, {
      props: { selectedTab: 'Tokens' },
      attachTo: document.body,
      global: { provide: { [configurationStoreKey]: configuration } }
    })
    await flushPromises()
    document.querySelector('[aria-label="Edit route /releases"]').click()
    await flushPromises()

    const labels = [...document.querySelectorAll('select[name="route-repository"] option')].map(option => option.textContent.trim())
    expect(labels).toEqual(['releases', 'snapshots', 'Custom path'])
  })
})
