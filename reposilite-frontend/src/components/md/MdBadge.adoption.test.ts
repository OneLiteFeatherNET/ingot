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
import MdBadge from './MdBadge.vue'
import TypeBadge from '../repositories/TypeBadge.vue'
import TokenTag from '../tokens/TokenTag.vue'
import RepositoryTypeBadge from '../tokens/RepositoryTypeBadge.vue'

describe('components that wrap the shared badge', () => {
  it('draws TypeBadge with MdBadge and keeps its anchor and hooks', () => {
    const wrapper = mount(TypeBadge, { props: { type: { key: 'PROXY', label: 'Proxy', derived: true } } })

    expect(wrapper.attributes('data-ingot-component')).toBe('type-badge')
    expect(wrapper.getComponent(MdBadge).props('tone')).toBe('proxy')
    expect(wrapper.get('[data-test="type-badge"]').attributes('data-type')).toBe('PROXY')
    expect(wrapper.get('[data-test="derived"]').text()).toBe('derived')
  })

  it('draws TokenTag with MdBadge, accent when asked, and keeps its anchor', () => {
    const plain = mount(TokenTag, { slots: { default: 'read' } })
    const accent = mount(TokenTag, { props: { accent: true }, slots: { default: 'manager' } })

    expect(plain.attributes('data-ingot-component')).toBe('token-tag')
    expect(plain.getComponent(MdBadge).props('tone')).toBe('accent')
    expect(accent.getComponent(MdBadge).props('tone')).toBe('hosted')
    expect(accent.text()).toBe('manager')
  })

  it('draws RepositoryTypeBadge with MdBadge and keeps its anchor and hooks', () => {
    const wrapper = mount(RepositoryTypeBadge, { props: { repository: { type: 'group', typeExplicit: true } } })

    expect(wrapper.attributes('data-ingot-component')).toBe('repository-type-badge')
    expect(wrapper.getComponent(MdBadge).props('tone')).toBe('group')
    expect(wrapper.get('[data-test="type-badge"]').attributes('data-type')).toBe('group')
  })
})
