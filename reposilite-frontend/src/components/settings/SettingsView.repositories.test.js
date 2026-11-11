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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'
import { loadedStore, repositorySchema } from '../repositories/fixtures/repositories'

// Session and toasts are replaced, so the page touches neither the network nor the DOM outside the view.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null }, isManager: { value: true } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))
vi.mock('../../store/placeholders', () => ({
  default: () => ({ basePath: '/', productionUrl: 'https://repo.example.net/' }),
  frontendSecretsHidden: () => false
}))

HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// The page is mounted at the address the router holds, with the route params the app passes in.
const mountSettings = async ({ address, props = {}, server }) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: stubRoutes()
  })
  await router.push(address)
  const store = await loadedStore({ server, schema: repositorySchema({ versionPolicy: true, readOnly: true }) })
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings', ...props },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store }
}

describe('the Repositories page inside Settings', () => {
  it('shows the Repositories page in place of the generic domain form', async () => {
    const { router } = await mountSettings({ address: '/settings/repositories' })

    expect(wrapper.find('h1').text(), 'the page heading is Repositories').toBe('Repositories')
    expect(wrapper.find('table').exists(), 'the table of the repositories is shown').toBe(true)
    expect(wrapper.find('#repositories-title').exists(), 'the generic collection editor is not drawn').toBe(false)
    expect(router.currentRoute.value.name, 'the address stays on the Repositories page').toBe('settings-repositories')
  })

  it('does not move the address to a domain while it shows the Repositories page', async () => {
    const { router } = await mountSettings({ address: '/settings/repositories' })

    expect(router.currentRoute.value.path, 'no domain is written to the address').toBe('/settings/repositories')
  })

  it('shows the detail of one repository at its own address', async () => {
    await mountSettings({ address: '/settings/repositories/releases', props: { id: 'releases' } })

    expect(wrapper.find('h1').text(), 'the detail heading is the id').toBe('releases')
  })

  it('lists a repository without an id as Unnamed repository, and its Edit action opens its detail', async () => {
    const server = {
      maven: {
        repositories: [
          { visibility: 'PUBLIC', storageProvider: { type: 'fs' } },
          { id: 'releases', visibility: 'PUBLIC', storageProvider: { type: 'fs' } }
        ]
      }
    }
    const { router } = await mountSettings({ address: '/settings/repositories', server })

    expect(wrapper.text(), 'the unnamed repository is listed').toContain('Unnamed repository')
    await wrapper.find('[aria-label="Edit Unnamed repository"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name, 'the Edit action opens the repository detail').toBe('settings-repository')
  })

  it('links the maven page to the Repositories page instead of listing the repositories there', async () => {
    await mountSettings({ address: '/settings/maven', props: { domain: 'maven' } })

    expect(wrapper.find('#repositories-title').exists(), 'the maven page does not list the repositories').toBe(false)
    const link = wrapper.find('a[href="/settings/repositories"]')
    expect(link.exists(), 'the card links to the Repositories page').toBe(true)
    expect(link.text(), 'the link is named after the repositories').toContain('Repositories')
  })
})
