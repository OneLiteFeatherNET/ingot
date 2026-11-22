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
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'empty', title: 'No artifacts yet' } })

    expect(wrapper.attributes('data-ingot-state'), 'the empty state must be marked').toBe('empty')
    expect(wrapper.text(), 'the title must be rendered').toContain('No artifacts yet')
    expect(wrapper.findAll('button'), 'the empty state must not offer a retry').toHaveLength(0)
  })

  it('offers one Retry action in the error state', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'error', title: 'Could not load' } })

    const buttons = wrapper.findAll('button')
    expect(buttons, 'the error state must offer exactly one action').toHaveLength(1)
    expect(buttons[0].text(), 'the action must be labelled Retry').toBe('Retry')
  })

  it('emits retry once for one click on the Retry action', async () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'error', title: 'Could not load' } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('retry'), 'one click must emit retry exactly once').toHaveLength(1)
  })

  it('uses role alert in the error state', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'error', title: 'Could not load' } })

    expect(wrapper.attributes('role'), 'the error state must be announced at once').toBe('alert')
  })

  it('draws the container tone on the error container with its on-container text colour', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'error', title: 'Not connected', tone: 'container' } })

    expect(wrapper.classes(), 'the container tone must fill the panel with the error container').toContain('bg-error-container')
    expect(wrapper.classes(), 'the text must pair with the error container, not the surface').toContain('text-on-error-container')
    expect(wrapper.classes(), 'the surface text colour must not stay on a container panel').not.toContain('text-on-surface')
  })

  it('keeps the surface tone when no tone is given', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'error', title: 'Could not load' } })

    expect(wrapper.classes(), 'the default panel must not take the error container').not.toContain('bg-error-container')
    expect(wrapper.classes(), 'the default panel keeps the surface text colour').toContain('text-on-surface')
  })

  it('leaves role alert off the error state when announce is false', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'error', title: 'Not connected', announce: false } })

    expect(wrapper.attributes('role'), 'a caller that announces the state itself must not get a second live region').toBeUndefined()
  })

  it('sets aria-busy while loading and shows skeleton lines instead of a spinner', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'loading', title: 'Loading members' } })

    expect(wrapper.attributes('aria-busy'), 'the loading state must set aria-busy').toBe('true')
    expect(
      wrapper.findAllComponents(MdSkeleton).length,
      'the loading state must render skeleton lines'
    ).toBeGreaterThan(0)
  })

  it('leaves aria-busy out of the empty state', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'empty', title: 'No artifacts yet' } })

    expect(wrapper.attributes('aria-busy'), 'only the loading state is busy').toBeUndefined()
  })

  it('renders the actions slot in place of the default Retry in the error state', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } },
      props: { state: 'error', title: 'Not connected' },
      slots: { actions: '<button type="button">Reconnect</button><button type="button">Sign in</button>' }
    })

    const labels = wrapper.findAll('button').map(button => button.text())
    expect(labels, 'the slot must supply every action of the error state').toEqual(['Reconnect', 'Sign in'])
    expect(labels, 'the default Retry must not be drawn next to the slot').not.toContain('Retry')
  })

  it('emits nothing for the default retry when the actions slot is used', async () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } },
      props: { state: 'error', title: 'Not connected' },
      slots: { actions: '<button type="button">Reconnect</button>' }
    })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('retry'), 'a slot action must not emit the default retry').toBeUndefined()
  })

  it('renders the actions slot in the empty state', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } },
      props: { state: 'empty', title: 'Nothing here' },
      slots: { actions: '<button type="button">Create</button>' }
    })

    expect(wrapper.findAll('button').map(button => button.text()), 'the empty state must show the slot action').toEqual(['Create'])
  })

  it('keeps the slot actions in document order', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } },
      props: { state: 'error', title: 'Not connected' },
      slots: { actions: '<button type="button">Sign in</button><button type="button">Reconnect</button>' }
    })

    expect(wrapper.findAll('button').map(button => button.text()), 'the order given by the slot must be kept').toEqual(['Sign in', 'Reconnect'])
  })

  it('carries the anchor on the root', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'empty', title: 'No artifacts yet' } })

    expect(wrapper.attributes('data-ingot-component'), 'the root must carry the md-state-panel anchor').toBe('md-state-panel')
  })

  it('draws loading, content and failure through the fade pattern, leaving before entering', () => {
    const wrapper = mount(MdStatePanel, { global: { stubs: { transition: false } }, props: { state: 'loading' } })

    const transition = wrapper.findComponent({ name: 'MdTransition' })
    expect(transition.exists(), 'the states of the panel must be wrapped in a transition').toBe(true)
    expect(transition.props('pattern'), 'the skeleton leaves at short2 and the content enters at short4 in the fade pattern').toBe('fade')
    expect(transition.props('mode'), 'the skeleton must be gone before the content is drawn, so both never stack').toBe('out-in')
    expect(transition.props('appear'), 'a panel that is loading on first paint must be drawn at once').toBe(false)
  })

  it('draws the loaded state in place of the skeleton', async () => {
    const wrapper = mount(MdStatePanel, { props: { state: 'loading' } })
    expect(wrapper.find('div').attributes('data-ingot-state'), 'the loading state is drawn first').toBe('loading')

    await wrapper.setProps({ state: 'empty', title: 'No artifacts yet' })

    expect(wrapper.find('div').attributes('data-ingot-state'), 'the content replaces the skeleton').toBe('empty')
    expect(wrapper.findAllComponents(MdSkeleton), 'no skeleton stays behind').toHaveLength(0)
  })
})
