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

import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import MdTabs from './MdTabs.vue'

// Every test mounts its own tabs, so no test reads the state of another. The address is only read when a test names it.
const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'plugins', label: 'Plugins' },
  { key: 'environment', label: 'Environment' },
]

const BLANK = { render: () => null }

let wrapper: VueWrapper | undefined
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

const tabsOf = (tabs: VueWrapper) => tabs.findAll('[role="tab"]')

const mountTabs = (props: Record<string, unknown> = {}, router?: Router) => {
  wrapper = mount(MdTabs, {
    props: { tabs: TABS, label: 'Sections', ...props },
    slots: { default: ({ tab }: { tab: string }) => `panel ${tab}` },
    global: { plugins: router ? [router] : [] },
    attachTo: document.body,
  }) as VueWrapper
  return wrapper
}

const routerAt = async (address: string) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/settings/system-info', name: 'system-info', component: BLANK }],
  })
  await router.push(address)
  await router.isReady()
  return router
}

describe('MdTabs touch targets', () => {
  it('gives every tab button a minimum height and width so the tap target stays large enough', () => {
    const tabs = mountTabs()

    for (const tab of tabsOf(tabs)) {
      expect(tab.classes(), `tab "${tab.text()}" lacks min-h-6 min-w-6`).toEqual(expect.arrayContaining(['min-h-6', 'min-w-6']))
    }
  })
})

describe('MdTabs roles', () => {
  it('draws the tab list with the accessible name it was given', () => {
    const tabs = mountTabs()

    expect(tabs.find('[role="tablist"]').attributes('aria-label')).toBe('Sections')
  })

  it('draws one tab per entry, in order, with the first tab selected when no value is given', () => {
    const tabs = mountTabs()

    expect(tabsOf(tabs).map((tab) => tab.text())).toEqual(['Overview', 'Plugins', 'Environment'])
    expect(tabsOf(tabs).map((tab) => tab.attributes('aria-selected'))).toEqual(['true', 'false', 'false'])
  })

  it('labels each panel by the tab that controls it', () => {
    const tabs = mountTabs()

    const panel = tabs.find('[role="tabpanel"]')

    expect(panel.attributes('aria-labelledby')).toBe(tabsOf(tabs)[0].attributes('id'))
  })
})

describe('MdTabs value', () => {
  it('selects the tab that the model value names', () => {
    const tabs = mountTabs({ modelValue: 'environment' })

    expect(tabsOf(tabs).map((tab) => tab.attributes('aria-selected'))).toEqual(['false', 'false', 'true'])
  })

  it('emits update:modelValue with the key of the tab that was chosen', async () => {
    const tabs = mountTabs({ modelValue: 'overview' })

    await tabsOf(tabs)[1].trigger('click')

    expect(tabs.emitted('update:modelValue')).toEqual([['plugins']])
  })

  it('moves the selection to the next tab on ArrowRight and emits the key', async () => {
    const tabs = mountTabs({ modelValue: 'overview' })

    await tabsOf(tabs)[0].trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()

    expect(tabs.emitted('update:modelValue')).toEqual([['plugins']])
    expect(document.activeElement).toBe(tabsOf(tabs)[1].element)
  })
})

describe('MdTabs address', () => {
  it('ignores the address when no query parameter is named, so the first tab stays selected', async () => {
    const router = await routerAt('/settings/system-info?tab=environment')

    const tabs = mountTabs({}, router)

    expect(tabsOf(tabs)[0].attributes('aria-selected'), 'the address is opt-in').toBe('true')
  })

  it('selects the tab named in the query parameter when one is named', async () => {
    const router = await routerAt('/settings/system-info?tab=environment')

    const tabs = mountTabs({ queryParam: 'tab' }, router)

    expect(tabsOf(tabs)[2].attributes('aria-selected')).toBe('true')
  })

  it('writes the chosen key into the named query parameter with a replace, not a push', async () => {
    const router = await routerAt('/settings/system-info')
    const push = vi.spyOn(router, 'push')
    const replace = vi.spyOn(router, 'replace')
    const tabs = mountTabs({ queryParam: 'tab' }, router)

    await tabsOf(tabs)[1].trigger('click')
    await flushPromises()

    expect(push, 'choosing a tab adds no history entry').not.toHaveBeenCalled()
    expect(replace).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.query.tab).toBe('plugins')
  })
})

describe('MdTabs anchors', () => {
  it('names its root md-tabs', () => {
    const tabs = mountTabs()

    expect(tabs.attributes('data-ingot-component')).toBe('md-tabs')
  })
})
