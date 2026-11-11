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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../router/create'
import { canUse } from '../store/nodeAccess'
import { setContributions } from '../navigation/registry'
import IndexPage from './IndexPage.vue'
import NotFoundView from '../components/util/NotFoundView.vue'

// The session is replaced by plain flags, so each test decides who is signed in and what they may do.
const session = vi.hoisted(() => ({ isManager: null, isLogged: null, mustChange: null, can: null }))
const login = vi.hoisted(() => ({ openLogin: vi.fn() }))
const migrationMounts = vi.hoisted(() => vi.fn())

vi.mock('../store/session', () => ({
  useSession: () => ({ isManager: session.isManager, isLogged: session.isLogged, mustChangePassword: session.mustChange, can: session.can })
}))

vi.mock('../store/login', () => ({ openLogin: login.openLogin }))

// The plugin manifest is set by each test through the registry, so loading it from the server is replaced.
vi.mock('../store/pluginUi', async () => {
  const { ref: makeRef } = await import('vue')
  return { pluginUiReady: makeRef(true), loadPluginUi: vi.fn(), clearPluginUi: vi.fn() }
})

vi.mock('../components/header/DefaultHeader.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'HeaderStub', render: () => h('header') } }
})

vi.mock('../components/header/HeaderHero.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'HeroStub', render: () => h('p', 'hero') } }
})

// The Migration page is replaced by a stub that reports its mount and keeps the step it was given.
vi.mock('../components/migration/MigrationView.vue', async () => {
  const { h, defineComponent } = await import('vue')
  return {
    __esModule: true,
    default: defineComponent({
      name: 'MigrationStub',
      props: { step: { type: String, default: undefined } },
      setup(props) {
        migrationMounts(props.step)
        return () => h('p', { 'data-test': 'section', 'data-section': 'migration', 'data-step': props.step ?? '' }, 'migration')
      },
    }),
  }
})

// IndexPage imports these two at once, and they read the session, which this file replaces with flags.
vi.mock('../components/browser/FileBrowserView.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'FileBrowserStub', render: () => h('p', 'browser') } }
})

vi.mock('../components/overview/RepositoryOverview.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'RepositoryOverviewStub', render: () => h('p', 'overview') } }
})

vi.mock('../components/settings/SettingsView.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'SettingsStub', render: () => h('p', 'settings') } }
})

const QUALIFIER = { path: '', watchable: 0 }

// The page as the application builds it: the router on memory history, the session answered at once.
const mountAt = async (url, { nodes, logged = true }) => {
  session.isManager = ref(canUse(nodes ?? [], 'ingot.admin.*'))
  session.can = (node) => canUse(nodes ?? [], node)
  session.isLogged = ref(logged)
  session.mustChange = ref(false)
  const router = createAppRouter({
    history: createMemoryHistory(),
    component: IndexPage,
    whenSessionKnown: () => Promise.resolve(),
    isManager: () => session.isManager.value,
    can: (node) => session.can(node),
    openLogin: login.openLogin,
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount(IndexPage, { props: { qualifier: QUALIFIER }, global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

const manifest = (enabledPlugins) => ({ pages: [], sections: [], enabledPlugins })

beforeEach(() => {
  migrationMounts.mockClear()
})

afterEach(() => {
  setContributions(null)
})

describe('the Migration page in the dashboard', () => {
  it('shows the Migration page to an instance admin when nexus-import is enabled', async () => {
    setContributions(manifest(['nexus-import']))

    const { wrapper } = await mountAt('/settings/migration/connect', { nodes: ['ingot.admin.*'] })

    expect(wrapper.find('[data-section="migration"]').exists(), 'the page is on screen').toBe(true)
  })

  it('hands the step of the address to the page', async () => {
    setContributions(manifest(['nexus-import']))

    const { wrapper } = await mountAt('/settings/migration/sync', { nodes: ['ingot.admin.*'] })

    expect(wrapper.find('[data-section="migration"]').attributes('data-step')).toBe('sync')
  })

  it('shows the not-found page at the address when nexus-import is not enabled', async () => {
    setContributions(manifest([]))

    const { wrapper } = await mountAt('/settings/migration/connect', { nodes: ['ingot.admin.*'] })

    expect(wrapper.findComponent(NotFoundView).exists(), 'the dashboard answers as for an unknown address').toBe(true)
    expect(wrapper.find('[data-section="migration"]').exists()).toBe(false)
  })

  it('shows no migration page and makes no call to a visitor without the instance admin permission', async () => {
    setContributions(manifest(['nexus-import']))

    const { wrapper } = await mountAt('/settings/migration/connect', { nodes: ['ingot.admin.dashboard'] })

    expect(wrapper.find('[data-test="no-access"]').exists(), 'a signed in visitor sees the no access state').toBe(true)
    expect(wrapper.find('[data-section="migration"]').exists()).toBe(false)
    expect(migrationMounts, 'the page, and with it every call, is never created').not.toHaveBeenCalled()
  })

  it('keeps the Migration page reachable while the plugin UI switch is off', async () => {
    setContributions(manifest(['nexus-import']))

    const { wrapper } = await mountAt('/settings/migration/connect', { nodes: ['ingot.admin.*'] })

    expect(wrapper.find('[data-section="migration"]').exists()).toBe(true)
  })
})
