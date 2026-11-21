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

// jsdom has no layout, so the tests give the tab list and its tabs a fixed geometry: the list is `listWidth` wide and
// each tab is 150 pixels, placed one after the other. Every stub is restored after the test that installed it.
const TAB_WIDTH = 150
const restorers: (() => void)[] = []

const stubLayout = (listWidth: number) => {
  const rect = (left: number, width: number) => ({ left, right: left + width, top: 0, bottom: 0, width, height: 0, x: left, y: 0, toJSON: () => ({}) })
  const spy = vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
    if (this.getAttribute('role') === 'tablist') return rect(0, listWidth)
    const index = Array.from(this.parentElement?.children ?? []).indexOf(this)
    return rect(index * TAB_WIDTH, TAB_WIDTH)
  })
  restorers.push(() => { spy.mockRestore() })
}

// Records every call of scrollIntoView, which must never be used: it would also scroll the page.
const stubScrollIntoView = () => {
  const scrollIntoView = vi.fn()
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { value: scrollIntoView, configurable: true, writable: true })
  restorers.push(() => { delete (HTMLElement.prototype as Partial<Pick<HTMLElement, 'scrollIntoView'>>).scrollIntoView })
  return scrollIntoView
}

const stubReducedMotion = (reduce: boolean) => {
  Object.defineProperty(window, 'matchMedia', {
    value: (query: string) => ({ matches: reduce && query === '(prefers-reduced-motion: reduce)', media: query }),
    configurable: true,
    writable: true,
  })
  restorers.push(() => { delete (window as Partial<Pick<Window, 'matchMedia'>>).matchMedia })
}

// The tab list is the element that scrolls, so its scrollTo is the one that is recorded.
const recordScrollTo = (tabs: VueWrapper) => {
  const list = tabs.find('[role="tablist"]').element as HTMLElement
  const scrollTo = vi.fn()
  list.scrollTo = scrollTo
  return scrollTo
}

describe('MdTabs narrow screens', () => {
  afterEach(() => {
    restorers.splice(0).reverse().forEach((restore) => restore())
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

  it('scrolls only the tab list sideways so a chosen tab cut off on the right becomes fully visible', async () => {
    stubLayout(300)
    const scrollIntoView = stubScrollIntoView()
    const router = await routerAt('/settings/system-info')
    const tabs = mountTabs({ queryParam: 'tab' }, router)
    const scrollTo = recordScrollTo(tabs)

    await tabsOf(tabs)[2].trigger('click')
    await flushPromises()

    expect(scrollTo, 'the tab list is scrolled by the offset that shows the tab').toHaveBeenCalledWith({ left: 150, behavior: 'smooth' })
    expect(scrollIntoView, 'the page is never scrolled by scrollIntoView').not.toHaveBeenCalled()
  })

  it('brings the deep-linked tab into view on mount without animation, and never scrolls the page', async () => {
    stubLayout(300)
    const scrollIntoView = stubScrollIntoView()
    const router = await routerAt('/settings/system-info?tab=environment')
    const scrollTo = vi.fn()
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', { value: scrollTo, configurable: true, writable: true })
    restorers.push(() => { delete (HTMLElement.prototype as Partial<Pick<HTMLElement, 'scrollTo'>>).scrollTo })

    mountTabs({ queryParam: 'tab' }, router)
    await flushPromises()

    expect(scrollTo, 'the deep-linked tab is shown with the auto behaviour').toHaveBeenCalledWith({ left: 150, behavior: 'auto' })
    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it('leaves the tab list where it is when the chosen tab is already fully visible', async () => {
    stubLayout(1000)
    stubScrollIntoView()
    const router = await routerAt('/settings/system-info')
    const tabs = mountTabs({ queryParam: 'tab' }, router)
    const scrollTo = recordScrollTo(tabs)

    await tabsOf(tabs)[2].trigger('click')
    await flushPromises()

    expect(scrollTo).not.toHaveBeenCalled()
  })

  it('changes the tab without animation when the user prefers reduced motion', async () => {
    stubLayout(300)
    stubReducedMotion(true)
    stubScrollIntoView()
    const router = await routerAt('/settings/system-info')
    const tabs = mountTabs({ queryParam: 'tab' }, router)
    const scrollTo = recordScrollTo(tabs)

    await tabsOf(tabs)[2].trigger('click')
    await flushPromises()

    expect(scrollTo).toHaveBeenCalledWith({ left: 150, behavior: 'auto' })
  })
})

// jsdom lays nothing out, so every tab reports the offset and width of the TAB_WIDTH geometry, placed one after the
// other in the order of the tabs. The indicator is not a tab and reports nothing.
const stubOffsets = () => {
  const indexOf = (element: HTMLElement) =>
    Array.from(element.parentElement?.querySelectorAll('[role="tab"]') ?? []).indexOf(element)
  const offsetLeft = vi.spyOn(HTMLElement.prototype, 'offsetLeft', 'get').mockImplementation(function (this: HTMLElement) {
    return Math.max(indexOf(this), 0) * TAB_WIDTH
  })
  const offsetWidth = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockImplementation(function (this: HTMLElement) {
    return indexOf(this) < 0 ? 0 : TAB_WIDTH
  })
  restorers.push(() => { offsetLeft.mockRestore(); offsetWidth.mockRestore() })
}

const indicatorOf = (tabs: VueWrapper) => tabs.find('[data-test="tab-indicator"]')

describe('MdTabs indicator', () => {
  afterEach(() => {
    restorers.splice(0).reverse().forEach((restore) => restore())
  })

  it('draws one indicator inside the tab list, hidden from assistive technology', () => {
    const tabs = mountTabs()

    const indicators = tabs.findAll('[data-test="tab-indicator"]')
    expect(indicators, 'the strip has a single indicator that moves, not one per tab').toHaveLength(1)
    expect(indicators[0].element.parentElement?.getAttribute('role'), 'the indicator scrolls with the tabs').toBe('tablist')
    expect(indicators[0].attributes('aria-hidden'), 'the selection is announced by aria-selected').toBe('true')
  })

  it('places the indicator under the selected tab from its offset and width, with no transition on the first paint', async () => {
    stubOffsets()

    const tabs = mountTabs({ modelValue: 'plugins' })
    await flushPromises()

    const indicator = indicatorOf(tabs)
    expect(indicator.attributes('style'), 'the indicator sits under the second tab').toContain('transform: translateX(150px) scaleX(150)')
    expect(indicator.classes(), 'the first paint does not slide the indicator in').not.toContain('transition-transform')
  })

  it('slides the indicator to the chosen tab on the motion tokens', async () => {
    stubOffsets()
    const router = await routerAt('/settings/system-info')
    const tabs = mountTabs({ queryParam: 'tab' }, router)
    await flushPromises()

    await tabsOf(tabs)[2].trigger('click')
    await flushPromises()

    const indicator = indicatorOf(tabs)
    expect(indicator.attributes('style'), 'the indicator moved under the third tab').toContain('transform: translateX(300px) scaleX(150)')
    expect(indicator.classes(), 'the move is a transform on the short4 and standard decelerate tokens').toEqual(
      expect.arrayContaining(['transition-transform', 'duration-md-short4', 'ease-md-standard-decelerate']),
    )
  })

  it('moves the indicator at once, with no transition, when the visitor asks for reduced motion', async () => {
    stubOffsets()
    stubReducedMotion(true)
    const router = await routerAt('/settings/system-info')
    const tabs = mountTabs({ queryParam: 'tab' }, router)
    await flushPromises()

    await tabsOf(tabs)[2].trigger('click')
    await flushPromises()

    const indicator = indicatorOf(tabs)
    expect(indicator.attributes('style'), 'the indicator still lands under the third tab').toContain('transform: translateX(300px) scaleX(150)')
    expect(indicator.classes(), 'reduced motion swaps the indicator instead of sliding it').not.toContain('transition-transform')
  })

  it('leaves the bottom border of every tab transparent, so the indicator is the only mark of the selection', () => {
    const tabs = mountTabs({ modelValue: 'plugins' })

    for (const tab of tabsOf(tabs)) {
      expect(tab.classes(), `tab "${tab.text()}" keeps its border box and draws no colour in it`).toEqual(
        expect.arrayContaining(['border-b-2', 'border-transparent']),
      )
      expect(tab.classes(), `tab "${tab.text()}" draws no indicator of its own`).not.toContain('border-primary')
    }
  })
})

describe('MdTabs anchors', () => {
  it('names its root md-tabs', () => {
    const tabs = mountTabs()

    expect(tabs.attributes('data-ingot-component')).toBe('md-tabs')
  })
})
