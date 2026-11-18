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
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdFieldShell from './MdFieldShell.vue'

// The shell's default slot receives the aria-describedby value and the invalid flag, so the test renders a
// plain input that binds them the way a field does.
const boundInput = () =>
  ({ describedBy, invalid }: { describedBy?: string, invalid: boolean }) =>
    h('input', {
      'data-test': 'field',
      'aria-describedby': describedBy,
      'aria-invalid': invalid ? 'true' : undefined
    })

describe('MdFieldShell', () => {
  it('gives the helper line the id of the input with the -helper suffix', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name', helperText: 'Shown in the token list' },
      slots: { default: boundInput() }
    })

    expect(wrapper.find('p').attributes('id'), 'the helper id follows the input id pattern').toBe('token-name-helper')
  })

  it('points aria-describedby of the field at the helper line when helper text is shown', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name', helperText: 'Shown in the token list' },
      slots: { default: boundInput() }
    })

    expect(wrapper.find('[data-test="field"]').attributes('aria-describedby'), 'the field is described by its helper line').toBe(
      wrapper.find('p').attributes('id')
    )
  })

  it('leaves aria-describedby off the field when there is no helper text', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name' },
      slots: { default: boundInput() }
    })

    expect(wrapper.find('[data-test="field"]').attributes('aria-describedby'), 'no helper line means nothing to describe the field').toBeUndefined()
  })

  it('reports the error state as invalid so that the field sets aria-invalid to true', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name', helperText: 'Name is taken', invalid: true },
      slots: { default: boundInput() }
    })

    expect(wrapper.find('[data-test="field"]').attributes('aria-invalid'), 'the error state must set aria-invalid to true').toBe('true')
  })

  it('does not set aria-invalid while the field is valid', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name', helperText: 'Shown in the token list' },
      slots: { default: boundInput() }
    })

    expect(wrapper.find('[data-test="field"]').attributes('aria-invalid'), 'a valid field has no aria-invalid').toBeUndefined()
  })

  it('insets the leading slot 12px from the edge', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'search' },
      slots: {
        leading: () => h('svg', { 'data-test': 'lead' }),
        default: boundInput()
      }
    })

    const inset = wrapper.find('[data-test="lead"]').element.parentElement!.className
    expect(inset, 'the leading slot sits at left-3, the shell inset').toContain('left-3')
    expect(inset, 'the leading slot must not use the 16px inset').not.toContain('left-4')
  })

  it('insets the trailing slot 12px from the edge', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'search' },
      slots: {
        trailing: () => h('svg', { 'data-test': 'trail' }),
        default: boundInput()
      }
    })

    const inset = wrapper.find('[data-test="trail"]').element.parentElement!.className
    expect(inset, 'the trailing slot sits at right-3, the shell inset').toContain('right-3')
    expect(inset, 'the trailing slot must not use the 16px inset').not.toContain('right-4')
  })

  it('sizes only an svg in the icon slots, so a trailing button keeps its own size', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'secret' },
      slots: {
        leading: () => h('svg', { 'data-test': 'lead' }),
        trailing: () => h('button', { 'data-test': 'toggle', class: 'size-10' }),
        default: boundInput()
      }
    })

    for (const test of ['lead', 'toggle']) {
      const slot = wrapper.find(`[data-test="${test}"]`).element.parentElement!.classList
      expect(slot.contains('md-field-icon'), `the ${test} slot sizes its svg with the md-field-icon utility`).toBe(true)
      expect([...slot].some(name => name.startsWith('*:')), `the ${test} slot must not resize every child`).toBe(false)
    }
  })

  it('renders the counter slot in the helper row beside the helper text', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'description', helperText: 'Optional' },
      slots: {
        default: boundInput(),
        counter: () => h('span', { 'data-test': 'counter' }, '3 / 10')
      }
    })

    expect(wrapper.find('[data-test="counter"]').exists(), 'the counter must be rendered by the shell').toBe(true)
  })

  it('has no style element in its own markup', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name' },
      slots: { default: boundInput() }
    })

    expect(wrapper.find('style').exists(), 'the shell styles itself with classes only').toBe(false)
  })

  it('anchors its root with md-field-shell', () => {
    const wrapper = mount(MdFieldShell, {
      props: { inputId: 'token-name' },
      slots: { default: boundInput() }
    })

    expect(wrapper.attributes('data-ingot-component'), 'the root carries the registered anchor').toBe('md-field-shell')
  })
})
