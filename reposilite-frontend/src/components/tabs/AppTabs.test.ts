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

import { h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AppTabs from './AppTabs.vue'

// Every test mounts its own tabs with a memory router, so no test reads the address of another.
const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'plugins', label: 'Plugins' },
  { key: 'environment', label: 'Environment' },
  { key: 'configuration', label: 'Configuration' },
]

const BLANK = { render: () => null }

let wrapper: VueWrapper | undefined
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

// Mounts the tabs at an address. The panels are the tab keys written as text, so a test reads what is shown.
const mountAt = async (address: string, props: Record<string, unknown> = {}) => {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/settings/system-info', name: 'system-info', component: BLANK }],
  })
  await router.push(address)
  await router.isReady()
  wrapper = mount(AppTabs, {
    props: { tabs: TABS, label: 'System sections', ...props },
    slots: { default: '<template #default="{ tab }"><p>panel {{ tab }}</p></template>' },
    global: { plugins: [router] },
    attachTo: document.body,
  }) as VueWrapper
  return { router, wrapper }
}

const tabsOf = (tabs: VueWrapper) => tabs.findAll('[role="tab"]')

describe('AppTabs roles', () => {
  it('draws the tab list with the accessible name it was given', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    const list = wrapper.find('[role="tablist"]')

    expect(list.exists(), 'the tab list is a tablist').toBe(true)
    expect(list.attributes('aria-label')).toBe('System sections')
  })

  it('draws one tab per entry in the order given, each with aria-selected and aria-controls', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    const tabs = tabsOf(wrapper)

    expect(tabs.map((tab) => tab.text()), 'the tabs keep their order').toEqual(['Overview', 'Plugins', 'Environment', 'Configuration'])
    expect(tabs[0].attributes('aria-selected'), 'the first tab is selected by default').toBe('true')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    for (const tab of tabs) {
      const panel = wrapper.find(`#${tab.attributes('aria-controls')}`)
      expect(panel.attributes('role'), 'each tab controls a tabpanel').toBe('tabpanel')
    }
  })

  it('labels each panel by the tab that controls it', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    const panels = wrapper.findAll('[role="tabpanel"]')

    expect(panels).toHaveLength(TABS.length)
    panels.forEach((panel, index) => {
      expect(panel.attributes('aria-labelledby'), 'the panel is labelled by its tab').toBe(tabsOf(wrapper)[index].attributes('id'))
    })
  })

  it('renders the slot content of each tab into its panel', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    const panels = wrapper.findAll('[role="tabpanel"]')

    expect(panels.map((panel) => panel.text())).toEqual(['panel overview', 'panel plugins', 'panel environment', 'panel configuration'])
  })
})

describe('AppTabs focus and visibility', () => {
  it('gives the tab index 0 to the selected tab only, and -1 to the others', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=environment')

    const indexes = tabsOf(wrapper).map((tab) => tab.attributes('tabindex'))

    expect(indexes, 'only the selected tab is in the tab order').toEqual(['-1', '-1', '0', '-1'])
  })

  it('hides every panel but the selected one', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=plugins')

    const hidden = wrapper.findAll('[role="tabpanel"]').map((panel) => panel.attributes('hidden') !== undefined)

    expect(hidden, 'only the Plugins panel is shown').toEqual([true, false, true, true])
  })
})

describe('AppTabs keyboard model', () => {
  it('moves the focus and the selection to the next tab on Right and to the previous one on Left', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    await tabsOf(wrapper)[0].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(tabsOf(wrapper)[1].attributes('aria-selected'), 'Right selects the next tab').toBe('true')
    expect(document.activeElement, 'Right moves the focus to the next tab').toBe(tabsOf(wrapper)[1].element)

    await tabsOf(wrapper)[1].trigger('keydown', { key: 'ArrowLeft' })
    await flushPromises()
    expect(tabsOf(wrapper)[0].attributes('aria-selected'), 'Left selects the previous tab').toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[0].element)
  })

  it('wraps from the last tab to the first on Right and from the first to the last on Left', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=configuration')

    await tabsOf(wrapper)[3].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(tabsOf(wrapper)[0].attributes('aria-selected'), 'Right on the last tab wraps to the first').toBe('true')

    await tabsOf(wrapper)[0].trigger('keydown', { key: 'ArrowLeft' })
    await flushPromises()
    expect(tabsOf(wrapper)[3].attributes('aria-selected'), 'Left on the first tab wraps to the last').toBe('true')
  })

  it('moves to the first tab on Home and to the last tab on End, and selects it', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=plugins')

    await tabsOf(wrapper)[1].trigger('keydown', { key: 'End' })
    await flushPromises()
    expect(tabsOf(wrapper)[3].attributes('aria-selected'), 'End selects the last tab').toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[3].element)

    await tabsOf(wrapper)[3].trigger('keydown', { key: 'Home' })
    await flushPromises()
    expect(tabsOf(wrapper)[0].attributes('aria-selected'), 'Home selects the first tab').toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[0].element)
  })
})

describe('AppTabs address', () => {
  it('selects the first tab when the tab query is missing', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    expect(tabsOf(wrapper)[0].attributes('aria-selected')).toBe('true')
  })

  it('selects the first tab when the tab query is unknown', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=unknown')

    expect(tabsOf(wrapper)[0].attributes('aria-selected')).toBe('true')
  })

  it('selects the tab named in a known tab query', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=environment')

    expect(tabsOf(wrapper)[2].attributes('aria-selected')).toBe('true')
  })

  it('replaces the history entry when a tab is chosen, and writes the key into the query', async () => {
    const { router, wrapper } = await mountAt('/settings/system-info')
    const push = vi.spyOn(router, 'push')
    const replace = vi.spyOn(router, 'replace')

    await tabsOf(wrapper)[2].trigger('click')
    await flushPromises()

    expect(push, 'choosing a tab adds no history entry').not.toHaveBeenCalled()
    expect(replace, 'choosing a tab replaces the history entry').toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.query.tab).toBe('environment')
  })

  it('keeps the selected tab when another query parameter changes', async () => {
    const { router, wrapper } = await mountAt('/settings/system-info?tab=plugins&filter=a')

    await router.replace({ path: '/settings/system-info', query: { tab: 'plugins', filter: 'b' } })
    await wrapper.vm.$nextTick()

    expect(tabsOf(wrapper)[1].attributes('aria-selected'), 'a change of another parameter keeps the tab').toBe('true')
  })
})

describe('AppTabs without the address', () => {
  it('follows the model value and reports the tab that is chosen, when no query parameter is named', async () => {
    const { wrapper } = await mountAt('/settings/system-info?tab=plugins', { queryParam: null, modelValue: 'configuration' })

    expect(tabsOf(wrapper)[3].attributes('aria-selected'), 'the model value selects the tab').toBe('true')

    await tabsOf(wrapper)[1].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['plugins']])
  })
})

describe('AppTabs anchors', () => {
  it('mounts an element named app-tabs that contains the tablist and the tabs, as on main', async () => {
    const { wrapper } = await mountAt('/settings/system-info')

    expect(wrapper.attributes('data-ingot-component'), 'plugins select on the app-tabs name').toBe('app-tabs')
    expect(wrapper.find('[role="tablist"]').exists(), 'the tablist is inside the named element').toBe(true)
    expect(wrapper.findAll('[role="tab"]'), 'every tab is inside the named element').toHaveLength(TABS.length)
  })
})

describe('AppTabs ids', () => {
  it('gives two instances on one page different ids', () => {
    // One app holds both instances, as one page does, so Vue scopes their ids to the same app.
    const page = mount({
      render: () => [
        h(AppTabs, { tabs: TABS, label: 'First sections', queryParam: null }),
        h(AppTabs, { tabs: TABS, label: 'Second sections', queryParam: null }),
      ],
    }, { attachTo: document.body })
    const ids = page.findAll('[role="tab"]').map((tab) => tab.attributes('id'))
    page.unmount()

    expect(new Set(ids).size, 'every tab has its own id').toBe(ids.length)
  })
})
