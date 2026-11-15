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
import RepositoryTypeBadge from './RepositoryTypeBadge.vue'

const LEGACY = 'No type set, behaves as before. Set a type to enforce the rules.'

describe('RepositoryTypeBadge', () => {
  it('shows the label of a typed repository', () => {
    const wrapper = mount(RepositoryTypeBadge, { props: { repository: { type: 'proxy', typeExplicit: true } } })
    expect(wrapper.get('[data-test="type-badge"]').text()).toBe('Proxy')
    expect(wrapper.get('[data-test="type-badge"]').attributes('data-type')).toBe('proxy')
  })

  it('does not show the legacy marker or hint for a typed repository', () => {
    const wrapper = mount(RepositoryTypeBadge, { props: { repository: { type: 'hosted', typeExplicit: true } } })
    expect(wrapper.find('[data-test="legacy-hint"]').exists()).toBe(false)
    expect(wrapper.get('[data-test="type-badge"]').attributes('title')).toBeUndefined()
  })

  it('marks an untyped repository as legacy, with the hint as tooltip and as text for screen readers', () => {
    const wrapper = mount(RepositoryTypeBadge, { props: { repository: { type: 'mixed', typeExplicit: false } } })
    const badge = wrapper.get('[data-test="type-badge"]')
    expect(badge.text()).toContain('Mixed')
    expect(badge.text()).toContain('legacy')
    expect(badge.attributes('title')).toBe(LEGACY)
    expect(wrapper.get('.sr-only').text()).toBe(LEGACY)
  })

  it('gives every type its own name, so a list can be read without the colours', () => {
    const labels = ['hosted', 'proxy', 'group'].map(type => mount(RepositoryTypeBadge, {
      props: { repository: { type, typeExplicit: true } }
    }).get('[data-test="type-badge"]').text())
    expect(labels).toEqual(['Hosted', 'Proxy', 'Group'])
  })
})
