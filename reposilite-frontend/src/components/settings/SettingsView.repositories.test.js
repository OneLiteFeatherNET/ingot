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
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter, useRoute } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import { createAppRouter } from '../../router/create'
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

    expect(wrapper.find('h2').text(), 'the page heading is Repositories').toBe('Repositories')
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

    expect(wrapper.find('h2').text(), 'the detail heading is the id').toBe('releases')
  })

  it('lists a repository without an id as Unnamed repository, and its Edit item opens its detail', async () => {
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
    await wrapper.find('button[aria-label="Actions for Unnamed repository"]').trigger('click')
    await flushPromises()
    document.querySelector('[role="menu"] [aria-label="Edit Unnamed repository"]').click()
    await flushPromises()
    expect(router.currentRoute.value.name, 'the Edit item opens the repository detail').toBe('settings-repository')
  })

})

describe('the Maven page address', () => {
  // The application router, so the redirect of the old address runs as it does in the dashboard.
  const AppHost = defineComponent({
    setup() {
      const route = useRoute()
      return () => (route.name?.startsWith('settings')
        ? h(SettingsView, { selectedTab: 'Settings', domain: route.params.domain, collection: route.params.collection, entry: route.params.entry })
        : null)
    }
  })

  const mountAppAt = async (address) => {
    const router = createAppRouter({
      history: createMemoryHistory(),
      component: AppHost,
      legacy: () => null,
      siteTitle: 'Ingot Repository',
      can: () => true,
      isManager: () => true
    })
    await router.push(address)
    await router.isReady()
    // The served maven schema declares the one domain-level field, so the Maven options section has a field to show.
    const schema = repositorySchema({ versionPolicy: true, readOnly: true })
    schema.properties.resolutionCacheClusterMaxAgeSeconds = { type: 'integer', title: 'Resolution cache maximum age in cluster mode' }
    const store = await loadedStore({ schema })
    wrapper = mount(AppHost, {
      global: { plugins: [router], provide: { [configurationStoreKey]: store } },
      attachTo: document.body
    })
    await flushPromises()
    return { router }
  }

  it('opens the Repositories page at the address of the Maven page, with no Maven link card', async () => {
    const { router } = await mountAppAt('/settings/maven')

    expect(router.currentRoute.value.name).toBe('settings-repositories')
    expect(wrapper.find('h2').text()).toBe('Repositories')
    expect(wrapper.find('#maven-repositories-link-title').exists(), 'the card that linked to the page is gone').toBe(false)
  })

  it('shows the Maven options once, on the Repositories page', async () => {
    await mountAppAt('/settings/maven')

    const headings = wrapper.findAll('h2').map(heading => heading.text())
    expect(headings.filter(text => text === 'Maven options'), 'one section of Maven options').toHaveLength(1)
  })
})
