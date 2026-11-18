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

// @vitest-environment jsdom

import { describe, it, expect, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ComboboxField from './ComboboxField.vue'

describe('ComboboxField', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  const OPTIONS = [{ value: 'alpha' }, { value: 'beta', description: 'the second one' }]

  const mountField = () => {
    wrapper = mount(ComboboxField, {
      props: { modelValue: '', label: 'Principal', listboxLabel: 'Principal suggestions', options: OPTIONS },
      attachTo: document.body
    }) as VueWrapper
    return wrapper
  }

  const input = () => wrapper!.find('input[role="combobox"]')
  const listbox = () => wrapper!.find('[role="listbox"]')

  it('names the listbox by the label of the field', () => {
    mountField()

    expect(listbox().attributes('aria-label'), 'the listbox is named').toBe('Principal suggestions')
  })

  it('stacks the list in the menu layer and not with a z-index literal', () => {
    mountField()

    const classes = listbox().classes()

    expect(classes, 'the menu layer token is used').toContain('z-md-menu')
    expect(classes, 'the z-20 literal is gone').not.toContain('z-20')
  })

  it('chooses the active option with ArrowDown and Enter', async () => {
    mountField()
    await input().trigger('focus')

    await input().trigger('keydown', { key: 'ArrowDown' })
    await input().trigger('keydown', { key: 'Enter' })

    expect(wrapper!.emitted('update:modelValue')?.at(-1), 'the first option is chosen').toEqual(['alpha'])
  })

  it('names the active option in aria-activedescendant', async () => {
    mountField()
    await input().trigger('focus')

    await input().trigger('keydown', { key: 'ArrowDown' })

    const active = wrapper!.findAll('[role="option"]')[0]
    expect(input().attributes('aria-activedescendant'), 'the active option is named').toBe(active.attributes('id'))
  })

  it('chooses an option with a click', async () => {
    mountField()
    await input().trigger('focus')

    await wrapper!.findAll('[role="option"]')[1].trigger('click')

    expect(wrapper!.emitted('update:modelValue')?.at(-1), 'the clicked option is chosen').toEqual(['beta'])
  })
})
