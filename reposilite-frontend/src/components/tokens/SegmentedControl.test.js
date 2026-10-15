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
import { h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import SegmentedControl from './SegmentedControl.vue'
import SegmentedOption from './SegmentedOption.vue'

const OPTIONS = [
  { key: 'a', label: 'Alpha' },
  { key: 'b', label: 'Bravo' },
  { key: 'c', label: 'Charlie' }
]

// A harness that owns the selection, so a click on a segment really changes what is checked.
const mountGroup = (initial = 'b') => {
  const selected = ref(initial)
  const wrapper = mount({
    setup: () => () => h(SegmentedControl, { label: 'Phonetics' }, () =>
      OPTIONS.map(option => h(SegmentedOption, {
        key: option.key,
        on: selected.value === option.key,
        onClick: () => { selected.value = option.key }
      }, () => option.label)))
  }, { attachTo: document.body })
  return { wrapper, selected }
}

const radios = () => [...document.querySelectorAll('[role="radio"]')]
const checkedLabel = () => radios().find(radio => radio.getAttribute('aria-checked') === 'true').textContent.trim()

// Sends a key to a segment and waits for Vue to paint the new selection.
const press = async (node, key) => {
  node.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }))
  await nextTick()
}

describe('SegmentedControl', () => {
  let mounted

  afterEach(() => {
    mounted?.wrapper.unmount()
    mounted = undefined
    document.body.innerHTML = ''
  })

  it('is a radiogroup named by its label', () => {
    mounted = mountGroup()

    const group = document.querySelector('[role="radiogroup"]')

    expect(group, 'the track announces a single choice').not.toBeNull()
    expect(group.getAttribute('aria-label')).toBe('Phonetics')
  })

  it('moves the selection to the next segment on ArrowRight and focuses it', async () => {
    mounted = mountGroup('a')

    await press(radios()[0], 'ArrowRight')

    expect(checkedLabel(), 'the selection follows the arrow key').toBe('Bravo')
    expect(document.activeElement, 'focus follows the selection').toBe(radios()[1])
  })

  it('moves to the previous segment on ArrowLeft', async () => {
    mounted = mountGroup('c')

    await press(radios()[2], 'ArrowLeft')

    expect(checkedLabel()).toBe('Bravo')
  })

  it('wraps from the last segment to the first on ArrowRight', async () => {
    mounted = mountGroup('c')

    await press(radios()[2], 'ArrowRight')

    expect(checkedLabel(), 'the group is a loop, not a dead end').toBe('Alpha')
  })

  it('wraps from the first segment to the last on ArrowLeft', async () => {
    mounted = mountGroup('a')

    await press(radios()[0], 'ArrowLeft')

    expect(checkedLabel()).toBe('Charlie')
  })

  it('selects the first segment on Home', async () => {
    mounted = mountGroup('c')

    await press(radios()[2], 'Home')

    expect(checkedLabel()).toBe('Alpha')
  })

  it('selects the last segment on End', async () => {
    mounted = mountGroup('b')

    await press(radios()[1], 'End')

    expect(checkedLabel()).toBe('Charlie')
  })

  it('ignores keys that are not navigation', async () => {
    mounted = mountGroup('b')

    await press(radios()[1], 'Enter')
    await press(radios()[1], 'x')

    expect(checkedLabel(), 'other keys leave the selection alone').toBe('Bravo')
  })

  it('leaves exactly one segment checked and in the tab order', async () => {
    mounted = mountGroup('b')

    await press(radios()[1], 'ArrowRight')

    expect(radios().filter(radio => radio.getAttribute('aria-checked') === 'true'), 'one checked radio').toHaveLength(1)
    expect(radios().map(radio => radio.getAttribute('tabindex')), 'only the selected segment is in the tab order')
      .toEqual(['-1', '-1', '0'])
  })
})
