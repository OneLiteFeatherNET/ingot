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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import MenuPanel from './MenuPanel.vue'

// A signed in user, so that the account actions are the ones on screen.
const session = vi.hoisted(() => ({ logout: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({
    token: ref({ name: 'alice', secret: 'secret' }),
    isLogged: ref(true),
    logout: (...args) => session.logout(...args),
    login: vi.fn(),
    loginFailureMessage: (error) => String(error)
  })
}))

// The router is not part of this test, so the link component is replaced by one that hands its
// slot a fixed address and a recorder for the navigation.
const navigation = vi.hoisted(() => ({ navigate: null, target: null }))

const RouterLinkStub = {
  props: ['to'],
  render() {
    navigation.target = this.to
    return this.$slots.default({ href: '/account', navigate: (...args) => navigation.navigate(...args) })
  }
}

const mountPanel = () => mount(MenuPanel, { global: { stubs: { RouterLink: RouterLinkStub } } })

describe('MenuPanel', () => {
  beforeEach(() => {
    session.logout = vi.fn()
    navigation.navigate = vi.fn(event => event.preventDefault())
    navigation.target = null
  })

  it('links to the account page by its route name', () => {
    const wrapper = mountPanel()

    const link = wrapper.findAll('a').find(anchor => anchor.text() === 'My account')

    expect(link, 'the account page is one click from the header').toBeDefined()
    expect(link.attributes('href')).toBe('/account')
    expect(navigation.target).toEqual({ name: 'account' })
  })

  it('gives the compact account control an accessible name', () => {
    const wrapper = mountPanel()

    expect(wrapper.find('a[aria-label="My account"]').exists(), 'the icon-only link on small screens needs a name').toBe(true)
  })

  it('navigates through the router when the account link is pressed', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('a').find(anchor => anchor.text() === 'My account').trigger('click')

    expect(navigation.navigate).toHaveBeenCalledTimes(1)
  })

  it('offers logout as a real button with its name as text', () => {
    const wrapper = mountPanel()

    const logout = wrapper.findAll('button').find(button => button.text() === 'Logout')

    expect(logout, 'logout must be a button, not a clickable div').toBeDefined()
    expect(logout.element.tagName).toBe('BUTTON')
  })

  it('gives the compact logout control an accessible name', () => {
    const wrapper = mountPanel()

    const compact = wrapper.find('button[aria-label="Logout"]')

    expect(compact.exists(), 'the icon-only logout on small screens needs a name for screen readers')
      .toBe(true)
  })

  it('signs the user out when the logout button is pressed', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('button').find(button => button.text() === 'Logout').trigger('click')

    expect(session.logout).toHaveBeenCalledTimes(1)
  })
})
