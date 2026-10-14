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
import TokenRow from './TokenRow.vue'

const mountRow = (props = {}) => mount(TokenRow, {
  props,
  slots: {
    default: '<span>route</span>',
    actions: '<button aria-label="Remove route /x">x</button>'
  }
})

const actionContainer = (wrapper) => wrapper.find('[aria-label="Remove route /x"]').element.parentElement

describe('TokenRow', () => {
  it('keeps the revealable actions in the DOM as focusable buttons', () => {
    const wrapper = mountRow({ revealActionsOnHover: true })

    const action = wrapper.find('[aria-label="Remove route /x"]').element
    expect(action.tagName).toBe('BUTTON')
    expect(action.disabled).toBe(false)
  })

  it('reveals the actions when something inside the row takes focus', () => {
    const wrapper = mountRow({ revealActionsOnHover: true })

    expect(actionContainer(wrapper).className).toContain('focus-within:opacity-100')
  })

  it('reveals the actions without hover on touch devices', () => {
    const wrapper = mountRow({ revealActionsOnHover: true })

    expect(actionContainer(wrapper).className).toContain('[@media(pointer:coarse)]:opacity-100')
  })

  it('does not hide the actions when they are not revealable', () => {
    const wrapper = mountRow({ revealActionsOnHover: false })

    expect(actionContainer(wrapper).className).not.toContain('opacity-0')
  })
})
