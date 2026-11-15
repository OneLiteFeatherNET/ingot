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

const ALICE = { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' }

const render = (props = {}) =>
  mount(ProfileCard, { props: { principal: ALICE, teams: [], instanceAdmin: false, ...props } })

describe('ProfileCard', () => {
  it('shows the display name, the username and the kind', () => {
    const text = render().text()

    expect(text).toContain('Alice Example')
    expect(text).toContain('alice')
    expect(text).toContain('User')
  })

  it('shows every team as a chip', () => {
    const wrapper = render({ teams: ['developers', 'release'] })

    expect(wrapper.findAll('[data-test="team"]').map(chip => chip.text())).toEqual(['developers', 'release'])
  })

  it('says so when the principal is in no team', () => {
    expect(render().text()).toContain('No teams')
  })

  it('shows the instance admin badge for an instance admin', () => {
    expect(render({ instanceAdmin: true }).find('[data-test="instance-admin"]').exists()).toBe(true)
  })

  it('shows no badge for an ordinary account', () => {
    expect(render().find('[data-test="instance-admin"]').exists(), 'only an instance admin gets the badge').toBe(false)
  })

  it('falls back to the username when there is no display name', () => {
    const wrapper = render({ principal: { ...ALICE, displayName: '' } })

    expect(wrapper.find('[data-test="display-name"]').text()).toBe('alice')
  })

  it('names a machine account as such', () => {
    expect(render({ principal: { ...ALICE, kind: 'MACHINE' } }).text()).toContain('Machine')
  })
})
