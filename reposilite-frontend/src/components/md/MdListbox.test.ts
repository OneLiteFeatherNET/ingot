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
import type { Component } from 'vue'
import MdTransition from './MdTransition.vue'
import { optionId } from '../../helpers/useCombobox'

const options = [{ value: 'alpha' }, { value: 'beta', description: 'the second' }, { value: 'gamma', disabled: true }]

// The test utilities stub Vue <Transition> with an element of its own, so the list is mounted with the real Transition,
// which adds no element, as in the browser.
const mountListbox = (props: Record<string, unknown> = {}) =>
  mount(MdListbox, {
    props: { id: 'nodes', label: 'Nodes', options, active: -1, open: true, ...props },
    global: { stubs: { transition: false } }
  })

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

  it('sets the option values in the monospace face when mono is given', () => {
    const wrapper = mountListbox({ mono: true })
    expect(optionsOf(wrapper)[0].find('span').classes(), 'the value uses the monospace face').toContain('font-mono')
  })

  it('keeps the option values in the body face by default', () => {
    const wrapper = mountListbox()
    expect(optionsOf(wrapper)[0].find('span').classes(), 'the value is not monospace by default').not.toContain('font-mono')
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

  describe('groups', () => {
    const grouped = [
      { value: 'ingot.read', group: 'Static' },
      { value: 'ingot.write', group: 'Static' },
      { value: 'ingot.repository.releases', group: 'Repository' }
    ]

    it('renders no group when no option names one', () => {
      const wrapper = mountListbox()

      expect(wrapper.findAll('[role="group"]').length, 'a flat list has no groups').toBe(0)
    })

    it('puts consecutive options of one group into one group under a heading', () => {
      const wrapper = mountListbox({ options: grouped })
      const groups = wrapper.findAll('[role="group"]')

      expect(groups.length, 'one group per run of the same name').toBe(2)
      expect(groups[0].findAll('[role="option"]').length, 'the first group holds both Static options').toBe(2)
      expect(wrapper.findAll('[data-test="option-group"]').map((heading) => heading.text()), 'the headings name the groups')
        .toEqual(['Static', 'Repository'])
    })

    it('names each group by its heading', () => {
      const wrapper = mountListbox({ options: grouped })
      const group = wrapper.findAll('[role="group"]')[1]
      const heading = wrapper.find(`#${group.attributes('aria-labelledby')}`)

      expect(heading.text(), 'aria-labelledby resolves to the heading text').toBe('Repository')
    })

    it('keeps the option ids in list order across groups', () => {
      const wrapper = mountListbox({ options: grouped, active: 2 })
      const third = optionsOf(wrapper)[2]

      expect(third.attributes('id'), 'the id counts across the groups').toBe(optionId('nodes', 2))
      expect(third.attributes('aria-selected'), 'the active index counts across the groups').toBe('true')
    })

    it('emits the value of a grouped option that was clicked', async () => {
      const wrapper = mountListbox({ options: grouped })

      await optionsOf(wrapper)[2].trigger('click')

      expect(wrapper.emitted('choose'), 'a click in a group chooses the option').toEqual([['ingot.repository.releases']])
    })
  })
})

describe('MdListbox motion', () => {
  it('opens and closes the list through the menu pattern of the motion primitives', () => {
    const wrapper = mountListbox()

    const transition = wrapper.findComponent(MdTransition as Component) as unknown as { exists: () => boolean, props: (key: 'pattern') => unknown }
    expect(transition.exists(), 'the list is wrapped by MdTransition').toBe(true)
    expect(transition.props('pattern'), 'the list uses the menu pattern, not a transition name of its own').toBe('menu')
  })

  it('keeps the list in the DOM while it is closed, so aria-controls still names it', () => {
    const wrapper = mountListbox({ open: false })

    const list = wrapper.find('[role="listbox"]')
    expect(list.exists(), 'the closed list stays in the DOM under v-show').toBe(true)
    expect((list.element as HTMLElement).style.display, 'the closed list is hidden by v-show').toBe('none')
  })

  it('scales from the top edge, where the input sits above the list', () => {
    const wrapper = mountListbox()

    expect(wrapper.find('[role="listbox"]').classes(), 'the list grows from the edge next to the input').toContain('origin-top')
  })

  it('keeps its registered anchor on the list, with no element of the transition around it', () => {
    const wrapper = mountListbox()

    expect(wrapper.attributes('role'), 'the root is still the listbox').toBe('listbox')
    expect(wrapper.attributes('data-ingot-component'), 'the root carries the registered name').toBe('md-listbox')
  })
})
