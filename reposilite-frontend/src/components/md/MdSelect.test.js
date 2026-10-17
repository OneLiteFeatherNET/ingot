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

import { describe, it, expect, afterEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MdSelect from './MdSelect.vue'

const options = [
  { value: 'PUBLIC', label: 'Public' },
  { value: 'HIDDEN', label: 'Hidden' },
  { value: 'PRIVATE', label: 'Private' }
]

// Every wrapper is mounted in the document and unmounted after its test, so the listbox,
// which is teleported to the body, never outlives the test that opened it.
const mounted = []
const mountSelect = (props = {}) => {
  const wrapper = mount(MdSelect, {
    props: { label: 'Visibility', options, ...props },
    attachTo: document.body
  })
  mounted.push(wrapper)
  return wrapper
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  while (mounted.length > 0) mounted.pop().unmount()
})

const combobox = (wrapper) => wrapper.get('[role="combobox"]')
const isOpen = (wrapper) => combobox(wrapper).attributes('aria-expanded') === 'true'
const listbox = (wrapper) => {
  const id = combobox(wrapper).attributes('aria-controls')
  return id ? document.getElementById(id) : null
}
const optionElements = (wrapper) => [...listbox(wrapper).querySelectorAll('[role="option"]')]
const activeLabel = (wrapper) => {
  const id = combobox(wrapper).attributes('aria-activedescendant')
  return id ? document.getElementById(id).textContent.trim() : undefined
}
const press = (wrapper, key) => combobox(wrapper).trigger('keydown', { key })
const open = async (wrapper) => {
  await combobox(wrapper).trigger('click')
}
const stubRect = (element, rect) => {
  vi.spyOn(element, 'getBoundingClientRect').mockReturnValue({
    top: rect.top,
    bottom: rect.bottom,
    left: rect.left ?? 40,
    right: (rect.left ?? 40) + (rect.width ?? 300),
    width: rect.width ?? 300,
    height: rect.bottom - rect.top,
    x: rect.left ?? 40,
    y: rect.top
  })
}

describe('MdSelect', () => {
  describe('labelling and the native value', () => {
    it('names the combobox after its label', () => {
      const wrapper = mountSelect()

      const labelId = wrapper.get('label').attributes('id')
      expect(labelId).toBeTruthy()
      expect(combobox(wrapper).attributes('aria-labelledby')).toBe(labelId)
    })

    it('lists every option under its label', () => {
      const wrapper = mountSelect()

      expect(wrapper.findAll('option').map(option => option.text())).toEqual(['Public', 'Hidden', 'Private'])
    })

    it('shows the label of the option whose value matches modelValue', () => {
      const wrapper = mountSelect({ modelValue: 'HIDDEN' })

      expect(wrapper.find('select').element.value).toBe('HIDDEN')
      expect(combobox(wrapper).text()).toBe('Hidden')
    })

    it('still takes a change of the native select, which forms and tests use', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })

      await wrapper.find('select').setValue('PRIVATE')

      expect(wrapper.emitted('update:modelValue')).toEqual([['PRIVATE']])
    })

    it('shows no option as chosen while modelValue is empty', () => {
      const wrapper = mountSelect({ modelValue: '' })

      expect(wrapper.find('select').element.value).toBe('')
      expect(combobox(wrapper).text()).toBe('')
    })

    it('marks the field invalid and shows the error text instead of the supporting text', () => {
      const wrapper = mountSelect({ error: true, errorText: 'Pick a visibility.', supportingText: 'Who can see it.' })

      expect(combobox(wrapper).attributes('aria-invalid')).toBe('true')
      expect(wrapper.text()).toContain('Pick a visibility.')
      expect(wrapper.text(), 'the supporting text is replaced while the error shows').not.toContain('Who can see it.')
    })

    it('links the supporting text to the combobox through aria-describedby', () => {
      const wrapper = mountSelect({ supportingText: 'Who can see it.' })

      const helper = wrapper.find('p')
      expect(helper.text()).toBe('Who can see it.')
      expect(combobox(wrapper).attributes('aria-describedby')).toBe(helper.attributes('id'))
    })

    it('cannot be opened or changed while disabled', async () => {
      const wrapper = mountSelect({ disabled: true, modelValue: 'PUBLIC' })

      await open(wrapper)
      await press(wrapper, 'ArrowDown')

      expect(combobox(wrapper).attributes('aria-disabled')).toBe('true')
      expect(isOpen(wrapper)).toBe(false)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })
  })

  describe('opening the menu', () => {
    it('opens a listbox of the options when the field is clicked', async () => {
      const wrapper = mountSelect()

      await open(wrapper)

      expect(isOpen(wrapper)).toBe(true)
      expect(listbox(wrapper).getAttribute('role')).toBe('listbox')
      expect(optionElements(wrapper).map(option => option.textContent.trim())).toEqual(['Public', 'Hidden', 'Private'])
    })

    it('labels the listbox with the field label and links it from the combobox', async () => {
      const wrapper = mountSelect()

      await open(wrapper)

      expect(listbox(wrapper).getAttribute('aria-labelledby')).toBe(combobox(wrapper).attributes('aria-labelledby'))
    })

    it('starts with the selected option active and marked selected', async () => {
      const wrapper = mountSelect({ modelValue: 'PRIVATE' })

      await open(wrapper)

      expect(activeLabel(wrapper)).toBe('Private')
      const selected = optionElements(wrapper).filter(option => option.getAttribute('aria-selected') === 'true')
      expect(selected.map(option => option.textContent.trim())).toEqual(['Private'])
    })

    it('starts with the first option active when nothing is selected', async () => {
      const wrapper = mountSelect({ modelValue: '' })

      await open(wrapper)

      expect(activeLabel(wrapper)).toBe('Public')
    })

    it('closes on a second click of the field', async () => {
      const wrapper = mountSelect()
      await open(wrapper)

      await open(wrapper)

      expect(isOpen(wrapper)).toBe(false)
      expect(listbox(wrapper)).toBeNull()
    })

    it('is drawn in the body, so that a dialog around the field cannot clip it', async () => {
      const wrapper = mountSelect()

      await open(wrapper)

      expect(listbox(wrapper).parentElement).toBe(document.body)
    })

    it('uses the theme surface for the menu and the primary role for the selected option', async () => {
      const wrapper = mountSelect({ modelValue: 'HIDDEN' })

      await open(wrapper)

      expect(listbox(wrapper).classList).toContain('bg-surface-container')
      const selected = optionElements(wrapper)[1]
      expect(selected.classList, 'the selected option takes the primary role').toContain('text-primary')
      expect(optionElements(wrapper)[0].classList).toContain('text-on-surface')
    })

    it('keeps the pointer down on the menu from taking focus off the field', async () => {
      const wrapper = mountSelect()
      await open(wrapper)
      const event = new MouseEvent('mousedown', { bubbles: true, cancelable: true })

      listbox(wrapper).dispatchEvent(event)

      expect(event.defaultPrevented).toBe(true)
    })
  })

  describe('keyboard', () => {
    it('opens the menu on ArrowDown when it is closed', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })

      await press(wrapper, 'ArrowDown')

      expect(isOpen(wrapper)).toBe(true)
    })

    it('opens the menu on Enter or Space when it is closed', async () => {
      const wrapper = mountSelect()

      await press(wrapper, 'Enter')
      expect(isOpen(wrapper)).toBe(true)

      await press(wrapper, 'Escape')
      await press(wrapper, ' ')
      expect(isOpen(wrapper)).toBe(true)
    })

    it('moves the active option with ArrowDown and ArrowUp, and stops at the ends', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)

      await press(wrapper, 'ArrowDown')
      expect(activeLabel(wrapper)).toBe('Hidden')

      await press(wrapper, 'ArrowDown')
      await press(wrapper, 'ArrowDown')
      expect(activeLabel(wrapper), 'the last option does not wrap to the first').toBe('Private')

      await press(wrapper, 'ArrowUp')
      expect(activeLabel(wrapper)).toBe('Hidden')
    })

    it('jumps to the first and last option with Home and End', async () => {
      const wrapper = mountSelect({ modelValue: 'HIDDEN' })
      await open(wrapper)

      await press(wrapper, 'End')
      expect(activeLabel(wrapper)).toBe('Private')

      await press(wrapper, 'Home')
      expect(activeLabel(wrapper)).toBe('Public')
    })

    it('chooses the active option on Enter, emits it and closes the menu', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)
      await press(wrapper, 'ArrowDown')

      await press(wrapper, 'Enter')

      expect(wrapper.emitted('update:modelValue')).toEqual([['HIDDEN']])
      expect(isOpen(wrapper)).toBe(false)
    })

    it('chooses the active option on Space as well', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)
      await press(wrapper, 'End')

      await press(wrapper, ' ')

      expect(wrapper.emitted('update:modelValue')).toEqual([['PRIVATE']])
    })

    it('closes on Escape without choosing, and keeps Escape from also closing a dialog', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)
      await press(wrapper, 'ArrowDown')
      const outerListener = vi.fn()
      document.addEventListener('keydown', outerListener)
      const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })

      try {
        combobox(wrapper).element.dispatchEvent(event)
        await wrapper.vm.$nextTick()
      } finally {
        document.removeEventListener('keydown', outerListener)
      }

      expect(isOpen(wrapper)).toBe(false)
      expect(wrapper.emitted('update:modelValue'), 'Escape must not choose the active option').toBeUndefined()
      expect(outerListener, 'the dialog must not see the Escape key').not.toHaveBeenCalled()
    })

    it('closes on Tab without choosing the active option', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)
      await press(wrapper, 'ArrowDown')

      await press(wrapper, 'Tab')

      expect(isOpen(wrapper)).toBe(false)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    it('stops arrow keys and Space from scrolling the page', async () => {
      const wrapper = mountSelect()
      for (const key of ['ArrowDown', ' ']) {
        const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
        combobox(wrapper).element.dispatchEvent(event)
        expect(event.defaultPrevented, `${JSON.stringify(key)} is handled by the field`).toBe(true)
      }
    })

    it('moves the active option to the first label that starts with the typed letter', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)

      await press(wrapper, 'h')

      expect(activeLabel(wrapper)).toBe('Hidden')
    })

    it('matches several typed letters as one search', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)

      await press(wrapper, 'p')
      await press(wrapper, 'r')

      expect(activeLabel(wrapper)).toBe('Private')
    })

    it('starts a new search after a pause, so an old prefix does not block the new letter', async () => {
      vi.useFakeTimers()
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)
      await press(wrapper, 'p')

      vi.advanceTimersByTime(1000)
      await press(wrapper, 'h')

      expect(activeLabel(wrapper), 'the search began again at h, not at ph').toBe('Hidden')
    })

    it('takes a typed letter as the value while the menu is closed', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })

      await press(wrapper, 'p')
      await press(wrapper, 'r')

      expect(isOpen(wrapper), 'a letter does not open the menu').toBe(false)
      expect(wrapper.emitted('update:modelValue')).toEqual([['PRIVATE']])
    })
  })

  describe('choosing with the pointer', () => {
    it('chooses the option that is clicked and closes the menu', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)

      await optionElements(wrapper)[1].click()

      expect(wrapper.emitted('update:modelValue')).toEqual([['HIDDEN']])
      expect(isOpen(wrapper)).toBe(false)
    })

    it('emits nothing when the option that is already chosen is clicked', async () => {
      const wrapper = mountSelect({ modelValue: 'PUBLIC' })
      await open(wrapper)

      await optionElements(wrapper)[0].click()

      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      expect(isOpen(wrapper)).toBe(false)
    })

    it('updates the field through v-model when the parent takes the emitted value', async () => {
      const wrapper = mount(MdSelect, {
        props: {
          label: 'Visibility',
          options,
          modelValue: 'PUBLIC',
          'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value })
        },
        attachTo: document.body
      })
      mounted.push(wrapper)
      await open(wrapper)

      await optionElements(wrapper)[2].click()

      expect(wrapper.props('modelValue')).toBe('PRIVATE')
      expect(combobox(wrapper).text()).toBe('Private')
      expect(wrapper.find('select').element.value).toBe('PRIVATE')
    })

    it('keeps the focus on the field after a choice', async () => {
      const wrapper = mountSelect()
      combobox(wrapper).element.focus()
      await open(wrapper)

      await optionElements(wrapper)[1].click()

      expect(document.activeElement).toBe(combobox(wrapper).element)
    })
  })

  describe('placement', () => {
    it('opens below the field when there is room for the options', async () => {
      vi.stubGlobal('innerHeight', 700)
      const wrapper = mountSelect()
      stubRect(combobox(wrapper).element, { top: 100, bottom: 156 })

      await open(wrapper)

      expect(listbox(wrapper).style.top).toBe('160px')
      expect(listbox(wrapper).style.bottom).toBe('')
    })

    it('flips above the field when there is no room below it', async () => {
      vi.stubGlobal('innerHeight', 700)
      const wrapper = mountSelect()
      stubRect(combobox(wrapper).element, { top: 600, bottom: 656 })

      await open(wrapper)

      expect(listbox(wrapper).style.top).toBe('')
      expect(listbox(wrapper).style.bottom).toBe('104px')
      expect(listbox(wrapper).style.maxHeight).toBe('160px')
    })

    it('scrolls the menu when neither side has room for every option', async () => {
      vi.stubGlobal('innerHeight', 400)
      const manyOptions = Array.from({ length: 20 }, (_, index) => ({ value: `v${index}`, label: `Option ${index}` }))
      const wrapper = mountSelect({ options: manyOptions })
      stubRect(combobox(wrapper).element, { top: 300, bottom: 356 })

      await open(wrapper)

      expect(listbox(wrapper).style.bottom).toBe('104px')
      expect(listbox(wrapper).style.maxHeight).toBe('292px')
    })
  })
})
