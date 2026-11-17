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

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MdListbox from './MdListbox.vue'
import { optionId } from '../../helpers/useCombobox'

const options = [{ value: 'alpha' }, { value: 'beta', description: 'the second' }, { value: 'gamma', disabled: true }]

const mountListbox = (props: Record<string, unknown> = {}) =>
  mount(MdListbox, { props: { id: 'nodes', label: 'Nodes', options, active: -1, open: true, ...props } })

const optionsOf = (wrapper: ReturnType<typeof mountListbox>) => wrapper.findAll('[role="option"]')

describe('MdListbox', () => {
  it('renders a listbox that carries its registered anchor', () => {
    const wrapper = mountListbox()

    expect(wrapper.attributes('role'), 'the root is a listbox').toBe('listbox')
    expect(wrapper.attributes('data-ingot-component'), 'the root carries the registered name').toBe('md-listbox')
  })

  it('takes its accessible name from the label', () => {
    const wrapper = mountListbox({ label: 'Node templates' })

    expect(wrapper.attributes('aria-label'), 'the listbox is named by its label').toBe('Node templates')
  })

  it('renders one option for each entry, each with the option role', () => {
    const wrapper = mountListbox()

    expect(optionsOf(wrapper).length, 'one option per entry').toBe(3)
    expect(optionsOf(wrapper)[0].text(), 'the option shows its value').toContain('alpha')
  })

  it('shows the description of an option under its value', () => {
    const wrapper = mountListbox()

    expect(optionsOf(wrapper)[1].text(), 'the description is rendered').toContain('the second')
  })

  it('marks only the active option as selected', () => {
    const wrapper = mountListbox({ active: 1 })
    const selected = optionsOf(wrapper).map((option) => option.attributes('aria-selected'))

    expect(selected, 'only the second option is aria-selected true').toEqual(['false', 'true', 'false'])
  })

  it('gives each option the id that aria-activedescendant points at', () => {
    const wrapper = mountListbox({ active: 2 })

    expect(optionsOf(wrapper)[2].attributes('id'), 'the id follows the combobox id scheme').toBe(optionId('nodes', 2))
  })

  it('marks a disabled option with aria-disabled', () => {
    const wrapper = mountListbox()

    expect(optionsOf(wrapper)[2].attributes('aria-disabled'), 'the disabled option is announced as disabled').toBe('true')
  })

  it('emits the value of the option that was clicked', async () => {
    const wrapper = mountListbox()

    await optionsOf(wrapper)[1].trigger('click')

    expect(wrapper.emitted('choose'), 'a click chooses the option').toEqual([['beta']])
  })

  it('does not emit a choice for a disabled option', async () => {
    const wrapper = mountListbox()

    await optionsOf(wrapper)[2].trigger('click')

    expect(wrapper.emitted('choose'), 'a disabled option cannot be chosen').toBeUndefined()
  })

  it('is hidden while the combobox is closed, and keeps its id for aria-controls', () => {
    const wrapper = mountListbox({ open: false })

    expect(wrapper.isVisible(), 'a closed list is not shown').toBe(false)
    expect(wrapper.attributes('id'), 'the id stays on the hidden list').toBe('nodes')
  })

  it('is hidden when there is nothing to show', () => {
    const wrapper = mountListbox({ options: [] })

    expect(wrapper.isVisible(), 'an empty list is not shown').toBe(false)
  })
})
