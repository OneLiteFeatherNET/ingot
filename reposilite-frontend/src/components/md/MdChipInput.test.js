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

import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MdChipInput from './MdChipInput.vue'
import { validateTag } from '../../store/maven/repositories'

let wrapper

const mountInput = (props = {}) => {
  wrapper = mount(MdChipInput, {
    props: { label: 'Tags', modelValue: ['team-core'], validate: validateTag, maxItems: 10, ...props },
    attachTo: document.body
  })
  return wrapper
}

const field = () => wrapper.find('input')
const chips = () => wrapper.findAll('[data-test="chip"]')
const chipTexts = () => chips().map(chip => chip.text())
const suggestionTexts = () => wrapper.findAll('[data-test="suggestion"]').map(button => button.text())
const emitted = () => wrapper.emitted('update:modelValue') ?? []
const lastEmitted = () => emitted().at(-1)?.[0]
const press = (key) => field().trigger('keydown', { key })
const typeText = (text) => field().setValue(text)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('MdChipInput chips', () => {
  it('shows each value as a chip, in the order of the value', () => {
    mountInput({ modelValue: ['team-core', 'ci'] })

    expect(chipTexts()).toEqual(['team-core', 'ci'])
  })

  it('adds the typed text as a chip when Enter is pressed', async () => {
    mountInput()

    await typeText('ci')
    await press('Enter')

    expect(lastEmitted()).toEqual(['team-core', 'ci'])
  })

  it('clears the field once the text has become a chip', async () => {
    mountInput()

    await typeText('ci')
    await press('Enter')

    expect(field().element.value).toBe('')
  })

  it('adds the text before a comma and keeps the text that follows it in the field', async () => {
    mountInput()

    await typeText('ci,deploy')

    expect(lastEmitted()).toEqual(['team-core', 'ci'])
    expect(field().element.value).toBe('deploy')
  })

  it('splits a list of values typed or pasted at once into chips', async () => {
    mountInput()

    await typeText('ci, ops ,')

    expect(lastEmitted()).toEqual(['team-core', 'ci', 'ops'])
    expect(field().element.value).toBe('')
  })

  it('ignores the blank piece of a comma list', async () => {
    mountInput()

    await typeText(' , ')

    expect(emitted()).toEqual([])
  })

  it('removes the chip whose close button is pressed', async () => {
    mountInput({ modelValue: ['team-core', 'ci'] })

    await wrapper.find('[aria-label="Remove ci"]').trigger('click')

    expect(lastEmitted()).toEqual(['team-core'])
  })

  it('removes the last chip when Backspace is pressed in an empty field', async () => {
    mountInput({ modelValue: ['team-core', 'ci'] })

    await press('Backspace')

    expect(lastEmitted()).toEqual(['team-core'])
  })

  it('keeps every chip when Backspace is pressed while the field holds text', async () => {
    mountInput({ modelValue: ['team-core', 'ci'] })

    await typeText('x')
    await press('Backspace')

    expect(emitted()).toEqual([])
  })

  it('shows the count of chips against the limit', () => {
    mountInput({ modelValue: ['team-core', 'ci'] })

    expect(wrapper.text()).toContain('2 / 10')
  })
})

describe('MdChipInput validation', () => {
  it('keeps the typed text and names the problem when the text is not a valid tag', async () => {
    mountInput()

    await typeText('Team Core')
    await press('Enter')

    expect(emitted(), 'nothing is added').toEqual([])
    expect(field().element.value, 'the text stays so it can be fixed').toBe('Team Core')
    expect(wrapper.text()).toContain('Use lower case letters, digits and hyphens')
    expect(field().attributes('aria-invalid')).toBe('true')
  })

  it('refuses a duplicate of a chip that is already there', async () => {
    mountInput()

    await typeText('team-core')
    await press('Enter')

    expect(emitted(), 'a duplicate is not stored twice').toEqual([])
    expect(wrapper.text()).toContain('This tag is already added.')
  })

  it('refuses a tag beyond the limit', async () => {
    const full = Array.from({ length: 10 }, (_, index) => `tag-${index}`)
    mountInput({ modelValue: full })

    await typeText('one-more')
    await press('Enter')

    expect(emitted()).toEqual([])
    expect(wrapper.text()).toContain('A repository takes at most 10 tags.')
  })

  it('adds the valid entries of a comma list and keeps the first invalid one in the field', async () => {
    mountInput()

    await typeText('ci,Bad Tag,ops,')

    expect(lastEmitted()).toEqual(['team-core', 'ci'])
    expect(field().element.value).toBe('Bad Tag,ops,')
    expect(wrapper.text()).toContain('Use lower case letters, digits and hyphens')
  })

  it('shows the error the form reports, when the text itself is fine', () => {
    mountInput({ error: true, errorText: 'Use at most 10 tags.' })

    expect(wrapper.text()).toContain('Use at most 10 tags.')
    expect(field().attributes('aria-invalid')).toBe('true')
  })
})

describe('MdChipInput suggestions', () => {
  const suggestions = ['team-core', 'ci', 'internal']

  it('offers the tags of other repositories that are not chips yet, while the field has focus', async () => {
    mountInput({ modelValue: ['ci'], suggestions })

    await field().trigger('focus')

    expect(suggestionTexts()).toEqual(['team-core', 'internal'])
  })

  it('offers no suggestions until the field has focus', () => {
    mountInput({ modelValue: [], suggestions })

    expect(suggestionTexts()).toEqual([])
  })

  it('narrows the suggestions to the text typed after the last comma', async () => {
    mountInput({ modelValue: [], suggestions })

    await field().trigger('focus')
    await typeText('ci,int')

    expect(suggestionTexts()).toEqual(['internal'])
  })

  it('adds a suggestion when it is pressed', async () => {
    mountInput({ modelValue: [], suggestions })

    await field().trigger('focus')
    await wrapper.findAll('[data-test="suggestion"]')[2].trigger('click')

    expect(lastEmitted()).toEqual(['internal'])
  })

  it('hides the suggestions when the field loses focus', async () => {
    mountInput({ modelValue: [], suggestions })

    await field().trigger('focus')
    await field().trigger('blur')

    expect(suggestionTexts()).toEqual([])
  })
})

describe('MdChipInput label', () => {
  it('ties the label to the input', () => {
    mountInput()

    expect(wrapper.find('label').text()).toBe('Tags')
    expect(wrapper.find('label').attributes('for')).toBe(field().attributes('id'))
  })
})

describe('MdChipInput outlined field', () => {
  it('floats the label into the notch once the field holds a value', () => {
    mountInput({ modelValue: ['team-core'] })

    expect(wrapper.find('label').classes()).toContain('top-0')
    expect(wrapper.find('label').classes()).toContain('bg-[var(--md-field-container,var(--md-sys-color-surface))]')
  })

  it('rests the label in the middle of an empty field that is not focused', () => {
    mountInput({ modelValue: [] })

    expect(wrapper.find('label').classes()).toContain('top-1/2')
  })

  it('draws the text input with the shared embedded-input reset, inside the outlined box', () => {
    mountInput()

    expect(field().classes()).toContain('md-embedded-input')
  })

  it('takes the full width of its form, not a fixed maximum', () => {
    mountInput()

    expect(wrapper.classes()).not.toContain('max-w-xl')
    expect(wrapper.classes()).toContain('w-full')
  })
})
