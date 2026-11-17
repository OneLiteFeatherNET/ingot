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
import ProfileCard from './ProfileCard.vue'
import type { Principal } from '../../store/session'

const ALICE = { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' }

const render = (props: { principal?: Principal; sources?: string[] } = {}) =>
  mount(ProfileCard, { props: { principal: ALICE, sources: ['Local'], ...props } })

const labels = (wrapper: ReturnType<typeof render>) => wrapper.findAll('dt').map(term => term.text())

describe('ProfileCard', () => {
  it('shows the display name, the username and the kind', () => {
    const text = render().text()

    expect(text).toContain('Alice Example')
    expect(text).toContain('alice')
    expect(text).toContain('User')
  })

  it('shows the username only when it differs from the display name', () => {
    const wrapper = render({ principal: { ...ALICE, displayName: 'alice' } })

    expect(labels(wrapper), 'the same value is not drawn on a second row').not.toContain('Username')
    expect(wrapper.text().match(/alice/g) ?? [], 'the value appears once').toHaveLength(1)
  })

  it('keeps the username row when the display name differs', () => {
    expect(labels(render())).toContain('Username')
  })

  it('leaves the teams to the Your access section, so no team is drawn in the profile', () => {
    const wrapper = render()

    expect(labels(wrapper), 'the profile has no Teams row').not.toContain('Teams')
    expect(wrapper.findAll('[data-test="team"]'), 'no team chip').toHaveLength(0)
  })

  it('leaves the instance admin tag to the Your access section', () => {
    expect(render().find('[data-test="instance-admin"]').exists(), 'the profile carries no admin tag').toBe(false)
  })

  it('names the sign-in sources of the principal, such as Local and LDAP', () => {
    const wrapper = render({ sources: ['Local', 'LDAP'] })

    expect(labels(wrapper), 'the profile names how the principal signs in').toContain('Sign-in')
    expect(wrapper.find('[data-test="sign-in-sources"]').text()).toBe('Local, LDAP')
  })

  it('falls back to the username when there is no display name', () => {
    const wrapper = render({ principal: { ...ALICE, displayName: '' } })

    expect(wrapper.find('[data-test="display-name"]').text()).toBe('alice')
  })

  it('names a machine account as such', () => {
    expect(render({ principal: { ...ALICE, kind: 'MACHINE' } }).text()).toContain('Machine')
  })

  it('draws the card through MdCard in the low variant', () => {
    const card = render().find('[data-variant="low"]')

    expect(card.exists(), 'the profile card is drawn by MdCard in the low variant').toBe(true)
    expect(card.classes(), 'a flat tinted panel on the page').toContain('bg-surface-container-low')
    expect(card.text(), 'the profile values sit inside the card').toContain('Alice Example')
  })

  it('takes the default padding and the medium corner of MdCard', () => {
    const card = render().find('[data-variant="low"]')

    expect(card.classes(), 'the inset is the card padding').toContain('p-4')
    expect(card.classes(), 'the corner is the medium one').toContain('rounded-corner-medium')
    expect(card.classes()).not.toContain('rounded-corner-large')
  })
})
