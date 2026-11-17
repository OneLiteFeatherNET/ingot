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
import { h } from 'vue'
import MdDialogActions from './MdDialogActions.vue'

const mountActions = () => mount(MdDialogActions, {
  slots: {
    default: () => [h('button', { type: 'button' }, 'Cancel'), h('button', { type: 'button' }, 'Save')]
  }
})

describe('MdDialogActions', () => {
  it('aligns the actions to the end of the row', () => {
    const actions = mountActions()

    expect(actions.classes(), 'the row is end-aligned').toContain('justify-end')
  })

  it('separates the actions with one gap token', () => {
    const actions = mountActions()

    expect(actions.classes(), 'the row uses the gap-2 gap').toContain('gap-2')
    expect(actions.classes().filter(name => name.startsWith('gap-')), 'there is one gap class and no axis override').toEqual(['gap-2'])
  })

  it('wraps the actions on narrow widths', () => {
    const actions = mountActions()

    expect(actions.classes(), 'the row wraps instead of overflowing').toContain('flex-wrap')
  })

  it('renders the actions in the order given', () => {
    const actions = mountActions()

    expect(actions.findAll('button').map(button => button.text()), 'the buttons keep their order').toEqual(['Cancel', 'Save'])
  })
})
