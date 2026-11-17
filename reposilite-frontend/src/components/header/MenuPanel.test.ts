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
import { ref, reactive, computed } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { computeAccessibleName } from 'dom-accessibility-api'
import MenuPanel from './MenuPanel.vue'

// A spy that records its arguments and returns nothing in particular.
type Spy = (...args: unknown[]) => unknown

// The session and the theme are replaced, so each test states who is signed in and which theme is on.
const state = vi.hoisted(() => ({
  isLogged: true,
  logout: vi.fn<Spy>(),
  changeTheme: vi.fn<Spy>(),
  mode: 'dark',
  principal: { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' },
  // The own access summary the menu reads for its sign-in sources; it is the answer of GET /api/identity/me/access.
  access: { principal: { name: 'alice', displayName: 'Alice Example', kind: 'USER', providers: ['local'] } }
}))

vi.mock('../../store/session', () => ({
  useSession: () => ({
    token: ref({ name: 'alice', secret: 'secret' }),
    principal: ref(state.principal),
    client: computed(() => ({
      identity: { me: { access: () => Promise.resolve({ data: state.access }) } }
    })),
    isLogged: ref(state.isLogged),
    logout: (...args: unknown[]) => state.logout(...args),
    login: vi.fn(),
    loginFailureMessage: (error: unknown) => String(error)
  })
}))

vi.mock('../../store/theme', () => ({
  default: () => ({
    theme: reactive({ mode: state.mode, isDark: state.mode === 'dark' }),
    changeTheme: (...args: unknown[]) => state.changeTheme(...args)
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

type MountedPanel = ReturnType<typeof mountPanel>

// The account menu is teleported to the body, so its items are read from the document.
const menuItem = (name: string): HTMLElement => [...document.querySelectorAll<HTMLElement>('[role="menu"] [role="menuitem"], [role="menu"] [role="menuitemradio"]')]
  .find(item => item.textContent.trim() === name) as HTMLElement
const accountTrigger = (wrapper: MountedPanel['wrapper']) => wrapper.find('button[aria-haspopup="menu"]')

describe('MenuPanel, signed in', () => {
  let mounted: MountedPanel | undefined

  beforeEach(() => {
    state.isLogged = true
    state.mode = 'dark'
    state.logout = vi.fn<Spy>()
    state.changeTheme = vi.fn<Spy>()
    state.principal = { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' }
    state.access = { principal: { name: 'alice', displayName: 'Alice Example', kind: 'USER', providers: ['local'] } }
    mounted?.wrapper.unmount()
    mounted = undefined
  })

  const open = async () => {
    mounted = mountPanel()
    await accountTrigger(mounted.wrapper).trigger('click')
    await flushPromises()
    return mounted
  }

  it('shows the account as one button, with the name of the user in it', () => {
    mounted = mountPanel()

    const triggers = mounted.wrapper.findAll('button[aria-haspopup="menu"]')
    expect(triggers, 'one account button').toHaveLength(1)
    expect(triggers[0].text(), 'the name is part of the button').toContain('Alice Example')
  })

  it('names the account button Account menu, followed by the name of the user', () => {
    mounted = mountPanel()

    const trigger = accountTrigger(mounted.wrapper).element
    expect(computeAccessibleName(trigger), 'a screen reader hears that the button opens a menu, and whose').toBe('Account menu, Alice Example')
  })

  it('keeps the visible name inside the accessible name of the account button (WCAG 2.5.3, Label in Name)', () => {
    mounted = mountPanel()

    const trigger = accountTrigger(mounted.wrapper).element
    expect(computeAccessibleName(trigger), 'the visible name is contained in the accessible name').toContain('Alice Example')
  })

  it('gives the name in the bar its full value as title, so a truncated name is still readable', () => {
    mounted = mountPanel()

    const name = accountTrigger(mounted.wrapper).find('[data-test="account-name"]')
    expect(name.attributes('title'), 'the full display name is the title').toBe('Alice Example')
  })

  it('shows the display name once, in the bar, and never again inside the open menu', async () => {
    const { wrapper } = await open()

    expect((accountTrigger(wrapper).text().match(/Alice Example/g) ?? []).length, 'the bar carries the name').toBe(1)
    const menuText = document.querySelector('[role="menu"]')!.textContent
    expect(menuText, 'the menu does not repeat the display name').not.toContain('Alice Example')
  })

  it('shows the username in the menu header only when it differs from the display name', async () => {
    state.principal = { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' }
    await open()
    expect(document.querySelector('[data-test="account-identity"]')!.textContent, 'the username differs, so it is shown').toContain('alice')
    mounted!.wrapper.unmount()

    state.principal = { id: 7, kind: 'USER', name: 'alice', displayName: 'alice' }
    await open()
    const header = document.querySelector('[data-test="account-identity"]')!.textContent
    expect(header.match(/alice/g) ?? [], 'the same value is not shown twice').toHaveLength(0)
  })

  it('shows the kind and the sign-in source in the menu header, such as User, LDAP', async () => {
    state.access = { principal: { name: 'alice', displayName: 'Alice Example', kind: 'USER', providers: ['ldap'] } }

    await open()

    const identity = document.querySelector('[data-test="account-identity"]')!
    expect(identity.textContent.replace(/\s+/g, ' ').trim()).toContain('User, LDAP')
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

  it('opens the menu with an identity block as its header, not a second copy of the name', async () => {
    const { wrapper } = await open()

    expect(accountTrigger(wrapper).attributes('aria-expanded')).toBe('true')
    const identity = document.querySelector<HTMLElement>('[role="menu"] [data-test="account-identity"]')
    expect(identity, 'the menu header is the identity block').not.toBeNull()
    expect(identity!.textContent, 'the header carries the kind line').toContain('User')
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

    const radios = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')]
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
    state.logout = vi.fn<Spy>()
    state.changeTheme = vi.fn<Spy>()
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
