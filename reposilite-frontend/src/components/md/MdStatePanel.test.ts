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
import MdStatePanel from './MdStatePanel.vue'
import MdSkeleton from './MdSkeleton.vue'

describe('MdStatePanel', () => {
  it('renders the empty state with its title and no retry action', () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'empty', title: 'No artifacts yet' } })

    expect(wrapper.attributes('data-ingot-state'), 'the empty state must be marked').toBe('empty')
    expect(wrapper.text(), 'the title must be rendered').toContain('No artifacts yet')
    expect(wrapper.findAll('button'), 'the empty state must not offer a retry').toHaveLength(0)
  })

  it('offers one Retry action in the error state', () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'error', title: 'Could not load' } })

    const buttons = wrapper.findAll('button')
    expect(buttons, 'the error state must offer exactly one action').toHaveLength(1)
    expect(buttons[0].text(), 'the action must be labelled Retry').toBe('Retry')
  })

  it('emits retry once for one click on the Retry action', async () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'error', title: 'Could not load' } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('retry'), 'one click must emit retry exactly once').toHaveLength(1)
  })

  it('uses role alert in the error state', () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'error', title: 'Could not load' } })

    expect(wrapper.attributes('role'), 'the error state must be announced at once').toBe('alert')
  })

  it('sets aria-busy while loading and shows skeleton lines instead of a spinner', () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'loading', title: 'Loading members' } })

    expect(wrapper.attributes('aria-busy'), 'the loading state must set aria-busy').toBe('true')
    expect(
      wrapper.findAllComponents(MdSkeleton).length,
      'the loading state must render skeleton lines'
    ).toBeGreaterThan(0)
  })

  it('leaves aria-busy out of the empty state', () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'empty', title: 'No artifacts yet' } })

    expect(wrapper.attributes('aria-busy'), 'only the loading state is busy').toBeUndefined()
  })

  it('carries the anchor on the root', () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'empty', title: 'No artifacts yet' } })

    expect(wrapper.attributes('data-ingot-component'), 'the root must carry the md-state-panel anchor').toBe('md-state-panel')
  })
})
