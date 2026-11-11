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
import { ref, reactive } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import MenuPanel from './MenuPanel.vue'

// The session and the theme are replaced, so each test states who is signed in and which theme is on.
const state = vi.hoisted(() => ({
  isLogged: true,
  logout: null,
  changeTheme: null,
  mode: 'dark'
}))

vi.mock('../../store/session', () => ({
  useSession: () => ({
    token: ref({ name: 'alice', secret: 'secret' }),
    principal: ref({ name: 'alice', displayName: 'Alice Example' }),
    isLogged: ref(state.isLogged),
    logout: (...args) => state.logout(...args),
    login: vi.fn(),
    loginFailureMessage: (error) => String(error)
  })
}))

vi.mock('../../store/theme', () => ({
  default: () => ({
    theme: reactive({ mode: state.mode, isDark: state.mode === 'dark' }),
    changeTheme: (...args) => state.changeTheme(...args)
  })
}))

const mountPanel = () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/account', name: 'account', component: { render: () => null } },
      { path: '/:qualifier(.*)', component: { render: () => null } }
    ]
  })
  const wrapper = mount(MenuPanel, { global: { plugins: [router] }, attachTo: document.body })
  return { wrapper, router }
}

// The account menu is teleported to the body, so its items are read from the document.
const menuItem = (name) => [...document.querySelectorAll('[role="menu"] [role="menuitem"], [role="menu"] [role="menuitemradio"]')]
  .find(item => item.textContent.trim() === name)
const accountTrigger = (wrapper) => wrapper.find('button[aria-haspopup="menu"]')

describe('MenuPanel, signed in', () => {
  let mounted

  beforeEach(() => {
    state.isLogged = true
    state.mode = 'dark'
    state.logout = vi.fn()
    state.changeTheme = vi.fn()
    mounted?.wrapper.unmount()
    mounted = undefined
  })

  const open = async () => {
    mounted = mountPanel()
    await accountTrigger(mounted.wrapper).trigger('click')
    await flushPromises()
    return mounted
  }

  it('shows the account as one button, named after the signed in user', () => {
    mounted = mountPanel()

    const triggers = mounted.wrapper.findAll('button[aria-haspopup="menu"]')
    expect(triggers, 'one account button').toHaveLength(1)
    expect(triggers[0].attributes('aria-label'), 'the visible name is the accessible name, so no label overrides it').toBeUndefined()
    expect(triggers[0].text(), 'the name is part of the button').toContain('Alice Example')
  })

  it('draws the first letter of the name as the avatar, hidden from assistive technology', () => {
    mounted = mountPanel()

    const avatar = accountTrigger(mounted.wrapper).find('[aria-hidden="true"]')
    expect(avatar.text(), 'the initial of the name, in capitals').toBe('A')
  })

  it('takes the name out of the layout below 900 pixels, but keeps it as the button name', () => {
    mounted = mountPanel()

    const name = accountTrigger(mounted.wrapper).find('[data-test="account-name"]')
    expect(name.text()).toBe('Alice Example')
    expect(name.classes(), 'screen reader only on narrow windows').toContain('sr-only')
    expect(name.classes(), 'shown again from 900 pixels').toContain('min-[900px]:not-sr-only')
  })

  it('keeps Welcome, My account, Logout and the theme icon out of the bar', () => {
    mounted = mountPanel()

    const bar = mounted.wrapper.text()
    expect(bar, 'no welcome text in the bar').not.toContain('Welcome')
    expect(mounted.wrapper.findAll('button').map(button => button.text()), 'no loose Logout button in the bar').not.toContain('Logout')
    expect(mounted.wrapper.findAll('a').map(link => link.text()), 'no loose My account link in the bar').not.toContain('My account')
  })

  it('opens the menu with the name of the user as its heading', async () => {
    const { wrapper } = await open()

    expect(accountTrigger(wrapper).attributes('aria-expanded')).toBe('true')
    const heading = document.querySelector('[role="menu"] [data-test="menu-heading"]')
    expect(heading, 'the heading names the user').not.toBeNull()
    expect(heading.textContent.trim()).toBe('Alice Example')
  })

  it('offers My account as a link to the account page', async () => {
    await open()

    const link = menuItem('My account')
    expect(link, 'My account is a menu item').toBeDefined()
    expect(link.getAttribute('href')).toBe('/account')
  })

  it('navigates to the account page when My account is chosen', async () => {
    const { router } = await open()

    menuItem('My account').click()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('account')
  })

  it('offers the three themes as radio items and checks the one in use', async () => {
    await open()

    const radios = [...document.querySelectorAll('[role="menuitemradio"]')]
    expect(radios.map(radio => radio.textContent.trim())).toEqual(['Light', 'Dark', 'Follow system'])
    expect(menuItem('Dark').getAttribute('aria-checked'), 'the theme in use is checked').toBe('true')
    expect(menuItem('Light').getAttribute('aria-checked')).toBe('false')
  })

  it('changes the theme when a theme is chosen', async () => {
    await open()

    menuItem('Light').click()
    await flushPromises()

    expect(state.changeTheme).toHaveBeenCalledWith('light')
  })

  it('offers Logout as a menu item, and signs the user out when it is chosen', async () => {
    await open()

    const logout = menuItem('Logout')
    expect(logout, 'logout is a menu item').toBeDefined()

    logout.click()
    await flushPromises()

    expect(state.logout).toHaveBeenCalledTimes(1)
  })
})

describe('MenuPanel, signed out', () => {
  beforeEach(() => {
    state.isLogged = false
    state.mode = 'auto'
    state.logout = vi.fn()
    state.changeTheme = vi.fn()
  })

  it('keeps a visible Sign in button', () => {
    const { wrapper } = mountPanel()

    expect(wrapper.findAll('button').map(button => button.text()), 'the Sign in button').toContain('Sign in')
  })

  it('offers no account menu, since there is no account to show', () => {
    const { wrapper } = mountPanel()

    expect(wrapper.find('button[aria-haspopup="menu"]').exists()).toBe(false)
  })

  it('keeps the theme control reachable for a visitor who is not signed in', () => {
    const { wrapper } = mountPanel()

    expect(wrapper.find('button[aria-label^="Theme: "]').exists()).toBe(true)
  })
})
