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

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ArrayListElement from './ArrayListElement.vue'

const styles = {
  arrayList: {
    item: '',
    itemToolbar: '',
    itemLabel: '',
    itemMoveUp: '',
    itemMoveDown: '',
    itemContent: '',
    itemExpanded: ''
  }
}

const mountElement = (props = {}) => mount(ArrayListElement, {
  props: { styles, label: 'maven.example.org', ...props },
  slots: { default: '<p>entry body</p>' }
})

const removeButton = (wrapper) => wrapper.find('button[aria-label^="Remove"]')

describe('ArrayListElement remove button', () => {
  it('has an accessible name that names the removed entry', () => {
    const wrapper = mountElement()

    expect(removeButton(wrapper).attributes('aria-label')).toBe('Remove maven.example.org')
  })

  it('falls back to a generic accessible name when the entry has no label', () => {
    const wrapper = mountElement({ label: '' })

    expect(removeButton(wrapper).attributes('aria-label')).toBe('Remove entry')
  })

  it('emits the removal once when clicked', () => {
    const removal = vi.fn()
    const wrapper = mountElement({ delete: removal })

    removeButton(wrapper).trigger('click')

    expect(removal).toHaveBeenCalledTimes(1)
  })
})
