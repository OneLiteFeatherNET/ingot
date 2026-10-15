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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import CardMenu from './CardMenu.vue'

const configurations = [
  { name: 'Maven', lang: 'xml' },
  { name: 'Gradle Kotlin', lang: 'kotlin' }
]

// CardMenu remembers the chosen tab in localStorage, so every test starts from an empty store.
let wrapper

const mountMenu = () => {
  wrapper = mount(CardMenu, { props: { configurations } })
  return wrapper
}

const tabNamed = (name) =>
  wrapper.findAll('button').find(button => button.text() === name)

const indicatorOf = (tab) =>
  tab.find('[aria-hidden="true"]')

describe('CardMenu tabs', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
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

  it('draws the active indicator 2px tall', () => {
    mountMenu()

    expect(indicatorOf(tabNamed('Maven')).classes(), 'the indicator is the 2px md3 secondary tab indicator').toContain('h-0.5')
  })

  it('rounds the top corners of the indicator', () => {
    mountMenu()

    expect(indicatorOf(tabNamed('Maven')).classes(), 'the indicator has rounded top corners').toContain('rounded-t-full')
  })

  it('fills the indicator only under the active tab', () => {
    mountMenu()

    expect(indicatorOf(tabNamed('Maven')).classes(), 'the active tab has a primary indicator').toContain('bg-primary')
    expect(indicatorOf(tabNamed('Gradle Kotlin')).classes(), 'an inactive tab has no visible indicator').toContain('bg-transparent')
  })
})
