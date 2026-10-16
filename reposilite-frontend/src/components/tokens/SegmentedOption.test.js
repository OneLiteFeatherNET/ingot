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
import SegmentedOption from './SegmentedOption.vue'

const mountOption = (props = {}) => mount(SegmentedOption, {
  props,
  slots: { default: 'Open' }
})

describe('SegmentedOption', () => {
  it('is a radio that reports the selected state through aria-checked', () => {
    const selected = mountOption({ on: true })
    const unselected = mountOption({ on: false })

    expect(selected.find('button').attributes('role')).toBe('radio')
    expect(selected.find('button').attributes('aria-checked'), 'the selected segment is checked').toBe('true')
    expect(unselected.find('button').attributes('aria-checked'), 'an unselected segment is not').toBe('false')
  })

  it('takes the tab stop only when selected, so arrow keys move within the group', () => {
    const selected = mountOption({ on: true })
    const unselected = mountOption({ on: false })

    expect(selected.find('button').attributes('tabindex')).toBe('0')
    expect(unselected.find('button').attributes('tabindex')).toBe('-1')
  })

  it('shows the check mark only on the selected segment', () => {
    const selected = mountOption({ on: true })
    const unselected = mountOption({ on: false })

    expect(selected.find('svg').exists(), 'the selected segment has a check mark').toBe(true)
    expect(unselected.find('svg').exists(), 'an unselected segment has no check mark').toBe(false)
  })

  it('is 40px tall', () => {
    const wrapper = mountOption()

    expect(wrapper.find('button').classes(), 'the segment height is h-10').toContain('h-10')
  })

  it('shares the track equally from sm up and never shrinks below its label on a narrow screen', () => {
    const classes = mountOption().find('button').classes()

    expect(classes, 'equal share of the track').toEqual(expect.arrayContaining(['flex-1', 'basis-0']))
    expect(classes, 'a narrow row wraps rather than squeezing the label').toContain('min-w-max')
    expect(classes, 'one row from sm up shares the track equally').toContain('sm:min-w-0')
  })

  it('fills the selected segment inside the track, with no ring of its own', () => {
    const selected = mountOption({ on: true }).find('button').classes()

    expect(selected).toContain('bg-secondary-container')
    expect(selected, 'no rounding of its own, the track clips it').not.toContain('rounded-l-corner-full')
    expect(selected, 'no outline ring on the selected segment').not.toContain('ring-2')
  })

  it('draws the focus ring inset and for keyboard focus only', () => {
    expect(mountOption().find('button').classes()).toContain('md-focus-ring-inset')
  })

  it('loses the focus on a pointer click, so the ring of a keyboard-focused segment does not stay', () => {
    const wrapper = mount(SegmentedOption, { props: { on: false }, slots: { default: 'Custom' }, attachTo: document.body })
    const button = wrapper.find('button').element
    button.focus()

    button.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))

    expect(document.activeElement, 'a pointer click leaves the segment without focus').not.toBe(button)
    wrapper.unmount()
  })

  it('keeps the focus on a keyboard activation, which has no pointer detail', () => {
    const wrapper = mount(SegmentedOption, { props: { on: false }, slots: { default: 'Custom' }, attachTo: document.body })
    const button = wrapper.find('button').element
    button.focus()

    button.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 0 }))

    expect(document.activeElement, 'a keyboard activation keeps the focus where the arrow key put it').toBe(button)
    wrapper.unmount()
  })

  it('still reports the click to its parent after blurring on a pointer click', () => {
    let clicks = 0
    const wrapper = mount(SegmentedOption, {
      props: { on: false },
      slots: { default: 'Custom' },
      attrs: { onClick: () => { clicks += 1 } }
    })

    wrapper.find('button').element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))

    expect(clicks, 'the parent handler still runs').toBe(1)
  })
})
