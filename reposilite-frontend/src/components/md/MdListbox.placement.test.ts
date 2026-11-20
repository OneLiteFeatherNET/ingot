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
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import MdListbox from './MdListbox.vue'

/*
 * DESIGN.md: "The list is not teleported: the consumer places it, and it takes the menu layer z-md-menu (50)." Unlike the
 * overflow menu and the dialog, the listbox stays where its combobox renders it, so the field around the input can place it.
 */
const Field = defineComponent({
  setup: () => () =>
    h('div', { 'data-test': 'field' }, [
      h('input', { role: 'combobox', 'aria-controls': 'nodes' }),
      h(MdListbox, { id: 'nodes', label: 'Nodes', options: [{ value: 'alpha' }], active: -1, open: true })
    ])
})

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const mountField = () => {
  wrapper = mount(Field, { attachTo: document.body })
  return wrapper
}

const listbox = () => document.querySelector<HTMLElement>('[role="listbox"]')!

describe('MdListbox placement', () => {
  it('stays inside the field of its combobox instead of moving to the body', () => {
    const field = mountField().find('[data-test="field"]').element

    expect(field.contains(listbox()), 'the consumer places the list, so it renders where the field renders it').toBe(true)
    expect(listbox().parentElement, 'the list is not a direct child of the body').not.toBe(document.body)
  })

  it('takes the menu layer, so it is drawn above the content that follows the field', () => {
    mountField()

    expect(listbox().classList.contains('z-md-menu'), 'the list sits on the menu layer z-md-menu').toBe(true)
  })
})
