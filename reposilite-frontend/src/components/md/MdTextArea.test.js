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
import MdTextArea from './MdTextArea.vue'

let wrapper

const mountArea = (props = {}) => {
  wrapper = mount(MdTextArea, {
    props: { label: 'Description', modelValue: '', maxLength: 280, ...props }
  })
  return wrapper
}

const area = () => wrapper.find('textarea')
const counter = () => wrapper.find('[data-test="counter"]')

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('MdTextArea', () => {
  it('shows the value in a multi-line field labelled by the label', () => {
    mountArea({ modelValue: 'Nightly builds' })

    expect(area().element.value).toBe('Nightly builds')
    expect(wrapper.find('label').attributes('for')).toBe(area().attributes('id'))
  })

  it('emits the text as it is typed', async () => {
    mountArea()

    await area().setValue('Released artifacts')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['Released artifacts'])
  })

  it('counts the characters against the limit', () => {
    mountArea({ modelValue: 'Nightly builds' })

    expect(counter().text()).toBe('14 / 280')
  })

  it('lets the field take no more characters than the limit', () => {
    mountArea()

    expect(area().attributes('maxlength')).toBe('280')
  })

  it('shows the counter in the error colour when the text is over the limit', () => {
    mountArea({ modelValue: 'x'.repeat(281) })

    expect(counter().classes()).toContain('text-error')
  })

  it('shows the error text and marks the field invalid when the form reports a problem', () => {
    mountArea({ error: true, errorText: 'Use at most 280 characters.' })

    expect(wrapper.text()).toContain('Use at most 280 characters.')
    expect(area().attributes('aria-invalid')).toBe('true')
  })

  it('shows the supporting text when there is no error', () => {
    mountArea({ supportingText: 'Shown in the overview.' })

    expect(wrapper.text()).toContain('Shown in the overview.')
    expect(area().attributes('aria-invalid')).toBeUndefined()
  })

  it('cannot be typed into while it is disabled', () => {
    mountArea({ disabled: true })

    expect(area().attributes('disabled')).toBeDefined()
  })

  it('shows no counter when there is no limit', () => {
    mountArea({ maxLength: 0 })

    expect(counter().exists()).toBe(false)
  })
})

describe('MdTextArea outlined field', () => {
  it('draws the label after the textarea, so the peer pattern floats it into the notch', () => {
    mountArea()

    const children = [...area().element.parentElement.children].map(element => element.tagName)
    expect(children).toEqual(['TEXTAREA', 'LABEL'])
    expect(area().classes()).toContain('peer')
    expect(wrapper.find('label').classes()).toContain('peer-focus:top-0')
  })

  it('has no resize handle, because the field grows with its text', () => {
    mountArea()

    expect(area().classes()).toContain('resize-none')
    expect(area().classes()).not.toContain('resize-y')
  })

  it('takes the full width of its form, not a fixed maximum', () => {
    mountArea()

    expect(wrapper.classes()).not.toContain('max-w-xl')
    expect(wrapper.classes()).toContain('w-full')
  })
})
