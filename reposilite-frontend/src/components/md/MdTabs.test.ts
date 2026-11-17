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

describe('MdTabs focus ring', () => {
  it('draws the focus ring of a tab with the standard md-focus-ring utility, which shows only for keyboard focus', () => {
    const tabs = mountTabs()

    for (const tab of tabsOf(tabs)) {
      expect(tab.classes(), `tab "${tab.text()}" takes the standard focus ring`).toContain('md-focus-ring')
    }
  })

  it('draws no focus outline on a tab that is not the standard ring, so only focus-visible shows a ring', () => {
    const tabs = mountTabs()

    for (const tab of tabsOf(tabs)) {
      const outlines = tab.classes().filter((name) => /outline|ring/.test(name))
      expect(outlines.every((name) => name === 'md-focus-ring' || name.startsWith('focus-visible:')), `tab "${tab.text()}" outline classes: ${outlines.join(' ')}`).toBe(true)
    }
  })

  it('draws the focus ring of the panel with the standard utility, so the browser default box does not show', () => {
    const tabs = mountTabs()

    const panel = tabs.find('[role="tabpanel"]')

    expect(panel.classes(), 'the panel is a focus stop, so it takes the ring the other controls take').toContain('md-focus-ring')
  })

  it('keeps focus where it is when the address selects another tab, so no tab takes a ring after navigation', async () => {
    const router = await routerAt('/settings/system-info?tab=overview')
    const tabs = mountTabs({ queryParam: 'tab' }, router)
    const outside = document.createElement('button')
    document.body.appendChild(outside)
    outside.focus()

    await router.replace({ path: '/settings/system-info', query: { tab: 'environment' } })
    await flushPromises()

    expect(tabsOf(tabs)[2].attributes('aria-selected'), 'the address selected the tab').toBe('true')
    expect(document.activeElement, 'focus did not move to the selected tab').toBe(outside)
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

// jsdom has no scrollIntoView, so the tests install a recording stub on the element prototype and remove it afterwards.
const stubScrollIntoView = () => {
  const scrolled: { element: HTMLElement; options: unknown }[] = []
  const scrollIntoView = function (this: HTMLElement, options: unknown) {
    scrolled.push({ element: this, options })
  }
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { value: scrollIntoView, configurable: true, writable: true })
  return scrolled
}

describe('MdTabs narrow screens', () => {
  afterEach(() => {
    delete (HTMLElement.prototype as Partial<Pick<HTMLElement, 'scrollIntoView'>>).scrollIntoView
  })

  it('keeps the tab list on one line and lets it scroll sideways instead of wrapping', () => {
    const tabs = mountTabs()

    expect(tabs.find('[role="tablist"]').classes(), 'the tab list does not wrap and scrolls when it overflows').toEqual(
      expect.arrayContaining(['flex-nowrap', 'overflow-x-auto']),
    )
  })

  it('keeps every tab at its own width, so a tab is not squeezed when the row overflows', () => {
    const tabs = mountTabs()

    for (const tab of tabsOf(tabs)) {
      expect(tab.classes(), `tab "${tab.text()}" lacks shrink-0`).toContain('shrink-0')
    }
  })

  it('scrolls a tab into view when it is selected by a click', async () => {
    const scrolled = stubScrollIntoView()
    const router = await routerAt('/settings/system-info')
    const tabs = mountTabs({ queryParam: 'tab' }, router)

    await tabsOf(tabs)[2].trigger('click')
    await flushPromises()

    expect(scrolled.at(-1), 'the chosen tab is scrolled to').toEqual({
      element: tabsOf(tabs)[2].element,
      options: { block: 'nearest', inline: 'nearest' },
    })
  })

  it('scrolls the tab named in the address into view on the first render', async () => {
    const scrolled = stubScrollIntoView()
    const router = await routerAt('/settings/system-info?tab=environment')

    const tabs = mountTabs({ queryParam: 'tab' }, router)
    await flushPromises()

    expect(scrolled.map((call) => call.element), 'the deep-linked tab is scrolled to').toContain(tabsOf(tabs)[2].element)
  })
})

describe('MdTabs anchors', () => {
  it('names its root md-tabs', () => {
    const tabs = mountTabs()

    expect(tabs.attributes('data-ingot-component')).toBe('md-tabs')
  })
})
