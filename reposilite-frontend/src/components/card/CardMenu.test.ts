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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CardMenu from './CardMenu.vue'

const configurations = [
  { name: 'Maven', lang: 'xml' },
  { name: 'Gradle Kotlin', lang: 'kotlin' }
]

// CardMenu remembers the chosen tab in localStorage, so every test starts from an empty store.
let wrapper: VueWrapper | undefined

const mountMenu = () => {
  wrapper = mount(CardMenu, { props: { configurations } }) as VueWrapper
  return wrapper
}

const tabNamed = (name: string) =>
  wrapper!.findAll('button').find(button => button.text() === name)!

const indicator = () => wrapper!.find('[data-test="tab-indicator"]')

// jsdom lays nothing out, so every tab reports a width of TAB_WIDTH and sits right after the one before it.
const TAB_WIDTH = 100
const restorers: (() => void)[] = []
const stubOffsets = () => {
  const indexOf = (element: HTMLElement) =>
    element instanceof HTMLButtonElement ? Array.from(element.parentElement?.querySelectorAll('button') ?? []).indexOf(element) : -1
  const offsetLeft = vi.spyOn(HTMLElement.prototype, 'offsetLeft', 'get').mockImplementation(function (this: HTMLElement) {
    return Math.max(indexOf(this), 0) * TAB_WIDTH
  })
  const offsetWidth = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockImplementation(function (this: HTMLElement) {
    return indexOf(this) < 0 ? 0 : TAB_WIDTH
  })
  restorers.push(() => { offsetLeft.mockRestore(); offsetWidth.mockRestore() })
}

describe('CardMenu tabs', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
    restorers.splice(0).reverse().forEach((restore) => restore())
  })

  it('gives every tab a 48px minimum height', () => {
    mountMenu()

    expect(tabNamed('Maven').classes(), 'a tab is 48px tall').toContain('min-h-12')
  })

  it('sets the tab label in the title-small style', () => {
    mountMenu()

    expect(tabNamed('Maven').classes(), 'the label uses title-small').toContain('md-title-small')
  })

  it('colours the active tab label primary', () => {
    mountMenu()

    expect(tabNamed('Maven').classes(), 'the selected tab is primary').toContain('text-primary')
  })

  it('colours inactive tab labels on-surface-variant', () => {
    mountMenu()

    expect(tabNamed('Gradle Kotlin').classes(), 'an unselected tab is on-surface-variant').toContain('text-on-surface-variant')
    expect(tabNamed('Gradle Kotlin').classes(), 'an unselected tab is not primary').not.toContain('text-primary')
  })

  it('draws one indicator for the whole row, 2px tall, hidden from assistive technology', () => {
    mountMenu()

    const indicators = wrapper!.findAll('[data-test="tab-indicator"]')

    expect(indicators, 'the row has one indicator, not one per tab').toHaveLength(1)
    expect(indicators[0].classes(), 'the indicator is the 2px md3 secondary tab indicator').toEqual(expect.arrayContaining(['h-0.5', 'bg-primary']))
    expect(indicators[0].attributes('aria-hidden'), 'the selection is announced by aria-current').toBe('true')
    expect(tabNamed('Maven').find('[aria-hidden="true"]').exists(), 'no tab draws an indicator of its own').toBe(false)
  })

  it('places the indicator under the selected tab, 8px in from both edges, with no slide on the first paint', async () => {
    stubOffsets()
    mountMenu()
    await flushPromises()

    expect(indicator().attributes('style'), 'the indicator sits under Maven').toContain('transform: translateX(8px) scaleX(84)')
    expect(indicator().classes(), 'the first paint places the indicator at once').not.toContain('transition-transform')
  })

  it('slides the indicator to the chosen tab with a transform on the motion tokens', async () => {
    stubOffsets()
    mountMenu()

    await tabNamed('Gradle Kotlin').trigger('click')
    await flushPromises()

    expect(indicator().attributes('style'), 'the indicator moved under Gradle Kotlin').toContain('transform: translateX(108px) scaleX(84)')
    expect(indicator().classes(), 'the move is a transform on the short4 and standard decelerate tokens').toEqual(
      expect.arrayContaining(['transition-transform', 'duration-md-short4', 'ease-md-standard-decelerate']),
    )
  })
})

describe('CardMenu tab choice', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
  })

  it('emits select-tab with the name of the chosen tab', async () => {
    mountMenu()

    await tabNamed('Gradle Kotlin').trigger('click')

    expect(wrapper!.emitted('select-tab')?.at(-1), 'the parent hears the new tab under the kebab-case name').toEqual(['Gradle Kotlin'])
  })

  it('emits selectTab with the same tab name as its deprecated alias', async () => {
    mountMenu()

    await tabNamed('Gradle Kotlin').trigger('click')

    expect(wrapper!.emitted('selectTab')?.at(-1), 'the alias carries the same tab name for one release').toEqual(['Gradle Kotlin'])
  })

  it('emits the first tab on mount so the parent starts in sync', () => {
    mountMenu()

    expect(wrapper!.emitted('select-tab')?.[0], 'the default tab is announced before any click').toEqual(['Maven'])
  })
})
