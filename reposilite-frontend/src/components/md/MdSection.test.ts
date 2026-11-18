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
import MdSection from './MdSection.vue'

describe('MdSection', () => {
  it('renders the title as a level 2 heading by default', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members' } })

    expect(wrapper.get('h2').text()).toBe('Members')
  })

  it('renders the title as a level 3 heading when level is 3', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members', level: 3 } })

    expect(wrapper.find('h2').exists()).toBe(false)
    expect(wrapper.get('h3').text()).toBe('Members')
  })

  it('renders the description under the title', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members', description: 'People with access' } })

    expect(wrapper.get('[data-test="section-description"]').text()).toBe('People with access')
  })

  it('renders the actions slot inside the header, beside the title', () => {
    const wrapper = mount(MdSection, {
      props: { title: 'Members' },
      slots: { actions: () => h('button', { 'data-test': 'add-member' }, 'Add') }
    })

    expect(wrapper.get('[data-test="section-header"]').find('[data-test="add-member"]').exists()).toBe(true)
  })

  it('renders the default slot as the content below the header', () => {
    const wrapper = mount(MdSection, {
      props: { title: 'Members' },
      slots: { default: () => h('p', { 'data-test': 'section-body' }, 'Body') }
    })

    expect(wrapper.get('[data-test="section-body"]').text()).toBe('Body')
  })

  it('puts the headingId on the title so an aria-labelledby of the section can point at it', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members', headingId: 'members-title' } })

    expect(wrapper.get('h2').attributes('id')).toBe('members-title')
  })

  it('leaves the title without an id when no headingId is given', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members' } })

    expect(wrapper.get('h2').attributes('id'), 'no headingId means no id on the title').toBeUndefined()
  })

  it('carries the md-section anchor on its root', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members' } })

    expect(wrapper.attributes('data-ingot-component')).toBe('md-section')
  })

  it('sets the level 2 title in the title-medium type role', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members' } })

    expect(wrapper.get('h2').classes(), 'a level 2 title must use md-title-medium').toContain('md-title-medium')
  })

  it('sets the level 3 title in the title-small type role', () => {
    const wrapper = mount(MdSection, { props: { title: 'Members', level: 3 } })

    expect(wrapper.get('h3').classes(), 'a level 3 title must use md-title-small').toContain('md-title-small')
  })
})
