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
import { h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import MdRadio from './MdRadio.vue'

// Mounts one radio with the v-model of a parent that owns the group value, as a form would.
const mountRadio = ({ value = 'read', initial = 'all', disabled = false, attrs = {} } = {}) => {
  const group = ref(initial)
  const wrapper = mount({
    setup: () => () => h(MdRadio, {
      modelValue: group.value,
      'onUpdate:modelValue': (next) => { group.value = next },
      value,
      name: 'access',
      disabled,
      ...attrs
    }, () => 'Read only')
  }, { attachTo: document.body })
  return { wrapper, group }
}

const input = () => document.querySelector('input[type="radio"]')
const label = () => document.querySelector('label')

describe('MdRadio', () => {
  let mounted

  afterEach(() => {
    mounted?.wrapper.unmount()
    mounted = undefined
    document.body.innerHTML = ''
  })

  it('renders a native radio that carries the name and value of its group', () => {
    mounted = mountRadio()

    expect(input().name).toBe('access')
    expect(input().value).toBe('read')
  })

  it('is checked when the group value equals its own value', () => {
    mounted = mountRadio({ initial: 'read' })

    expect(input().checked).toBe(true)
  })

  it('is unchecked while the group holds another value', () => {
    mounted = mountRadio({ initial: 'all' })

    expect(input().checked).toBe(false)
  })

  it('shows the selection dot only while checked', () => {
    mounted = mountRadio({ initial: 'read' })
    expect(mounted.wrapper.find('[data-part="dot"]').exists(), 'selected radio shows its dot').toBe(true)

    mounted.group.value = 'all'
    return mounted.wrapper.vm.$nextTick().then(() => {
      expect(mounted.wrapper.find('[data-part="dot"]').exists(), 'an unselected radio shows no dot').toBe(false)
    })
  })

  it('emits its own value through v-model when it is chosen', async () => {
    mounted = mountRadio({ initial: 'all' })

    input().click()
    await mounted.wrapper.vm.$nextTick()

    expect(mounted.group.value, 'the parent receives the value of the chosen radio').toBe('read')
  })

  it('selects its radio when the label text is clicked', async () => {
    mounted = mountRadio({ initial: 'all' })

    label().click()
    await mounted.wrapper.vm.$nextTick()

    expect(mounted.group.value, 'a click on the label is a click on the radio').toBe('read')
  })

  it('is labelled by its label text, so a screen reader announces the choice', () => {
    mounted = mountRadio()

    expect(input().labels[0].textContent.trim()).toBe('Read only')
  })

  it('forwards aria-describedby to the input', () => {
    mounted = mountRadio({ attrs: { 'aria-describedby': 'access-read-help' } })

    expect(input().getAttribute('aria-describedby')).toBe('access-read-help')
  })

  it('stays in the tab order as a native radio, so the keyboard reaches the group', () => {
    mounted = mountRadio()

    input().focus()

    expect(document.activeElement, 'the native input takes keyboard focus').toBe(input())
    expect(input().hasAttribute('tabindex'), 'no tabindex overrides the native tab stop').toBe(false)
  })

  it('keeps the focus after a keyboard activation, which has no pointer detail', () => {
    mounted = mountRadio({ initial: 'all' })
    input().focus()

    input().dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 0 }))

    expect(document.activeElement, 'a keyboard click leaves the focus where it is').toBe(input())
  })

  it('leaves the focus after a pointer click, so no focus ring stays behind', () => {
    mounted = mountRadio({ initial: 'all' })
    input().focus()

    input().dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))

    expect(document.activeElement, 'a mouse click removes the focus from the radio').not.toBe(input())
  })

  it('does not select a disabled radio when its label is clicked', async () => {
    mounted = mountRadio({ initial: 'all', disabled: true })

    label().click()
    await mounted.wrapper.vm.$nextTick()

    expect(mounted.group.value, 'a disabled radio cannot be chosen').toBe('all')
    expect(input().disabled).toBe(true)
  })

  it('marks the label as disabled with the same disabled state as the input', () => {
    mounted = mountRadio({ disabled: true })

    expect(label().className).toContain('text-on-surface/38')
  })

  describe('as a card', () => {
    const cardContent = () => document.querySelector('[data-part="card-content"]')

    // Mounts a card radio in a fieldset, the way the wizard groups its options.
    const mountCard = ({ initial = 'all', disabled = false } = {}) => {
      const group = ref(initial)
      const wrapper = mount({
        setup: () => () => h('fieldset', [
          h(MdRadio, {
            modelValue: group.value,
            'onUpdate:modelValue': (next) => { group.value = next },
            value: 'read',
            name: 'access',
            card: true,
            disabled
          }, () => 'Read only'),
          h(MdRadio, {
            modelValue: group.value,
            'onUpdate:modelValue': (next) => { group.value = next },
            value: 'all',
            name: 'access',
            card: true,
            disabled: false
          }, () => 'Everything')
        ])
      }, { attachTo: document.body })
      return { wrapper, group }
    }
    const inputs = () => Array.from(document.querySelectorAll('input[type="radio"]'))
    const press = (element, key) => element.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }))

    it('makes the card itself the label of its radio', () => {
      mounted = mountRadio({ attrs: { card: true, class: 'border p-3' } })

      expect(label().contains(input()), 'the input sits inside the card label').toBe(true)
      expect(label().classList.contains('border')).toBe(true)
    })

    it('selects the radio when the card text is clicked', async () => {
      mounted = mountCard()

      cardContent().click()
      await mounted.wrapper.vm.$nextTick()

      expect(mounted.group.value, 'a click on the card text chooses the card').toBe('read')
    })

    it('selects the radio when the empty card area is clicked', async () => {
      mounted = mountCard()

      document.querySelector('label').click()
      await mounted.wrapper.vm.$nextTick()

      expect(mounted.group.value, 'a click on the card root chooses the card').toBe('read')
    })

    it('does not select a disabled card when it is clicked', async () => {
      mounted = mountCard({ disabled: true })

      cardContent().click()
      await mounted.wrapper.vm.$nextTick()

      expect(mounted.group.value, 'a disabled card cannot be chosen').toBe('all')
    })

    it('moves the selection to the next card with the down arrow', async () => {
      mounted = mountCard({ initial: 'read' })
      inputs()[0].focus()

      press(inputs()[0], 'ArrowDown')
      await mounted.wrapper.vm.$nextTick()

      expect(mounted.group.value, 'the second card is chosen').toBe('all')
      expect(document.activeElement, 'the focus follows the selection').toBe(inputs()[1])
    })

    it('moves the selection back with the up arrow and wraps at the ends of the group', async () => {
      mounted = mountCard({ initial: 'read' })
      inputs()[0].focus()

      press(inputs()[0], 'ArrowUp')
      await mounted.wrapper.vm.$nextTick()

      expect(mounted.group.value, 'up from the first card wraps to the last').toBe('all')
    })

    it('moves with the right and left arrows as well', async () => {
      mounted = mountCard({ initial: 'all' })
      inputs()[1].focus()

      press(inputs()[1], 'ArrowRight')
      await mounted.wrapper.vm.$nextTick()
      expect(mounted.group.value, 'right from the last card wraps to the first').toBe('read')

      press(inputs()[0], 'ArrowLeft')
      await mounted.wrapper.vm.$nextTick()
      expect(mounted.group.value, 'left from the first card wraps to the last').toBe('all')
    })

    it('takes the arrow key itself, so the browser does not move the selection a second time', () => {
      mounted = mountCard({ initial: 'read' })

      const event = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true })
      inputs()[0].dispatchEvent(event)

      expect(event.defaultPrevented, 'the default of the arrow key is prevented').toBe(true)
    })

    it('does not move the selection for other keys', async () => {
      mounted = mountCard({ initial: 'read' })

      press(inputs()[0], 'Enter')
      await mounted.wrapper.vm.$nextTick()

      expect(mounted.group.value).toBe('read')
    })
  })
})
