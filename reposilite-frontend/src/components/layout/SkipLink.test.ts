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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SkipLink from './SkipLink.vue'
import App from '../../App.vue'

// The app shell is mounted for the placement test only. Its stores and the head manager are replaced, so the test
// reads nothing from the network, the browser storage or the system clock.
vi.mock('@unhead/vue', () => ({ useHead: () => {} }))
vi.mock('../../store/session', () => ({ useSession: () => ({ initializeSession: () => Promise.resolve() }) }))
vi.mock('../../store/theme', async () => {
  const { reactive: reactiveState } = await import('vue')
  return { default: () => ({ theme: reactiveState({ isDark: false }), fetchColorMode: () => {} }) }
})
vi.mock('../../store/qualifier', async () => {
  const { ref: reactiveRef } = await import('vue')
  return { default: () => ({ qualifier: reactiveRef(null) }) }
})
vi.mock('../../store/placeholders', async () => {
  const { ref: reactiveRef } = await import('vue')
  return { default: () => ({ description: reactiveRef('') }) }
})
vi.mock('../../helpers/toast', () => ({ createErrorToast: () => {}, errorMessage: (error) => String(error) }))

// A page with the main element that IndexPage renders. The document is cleared after each test.
const addMain = () => {
  const main = document.createElement('main')
  main.id = 'main'
  main.tabIndex = -1
  document.body.appendChild(main)
  return main
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('skip link', () => {
  it('reads Skip to main content', () => {
    const wrapper = mount(SkipLink)

    expect(wrapper.text(), 'the visible words of the link').toBe('Skip to main content')
  })

  it('points at the main element by its fragment', () => {
    const wrapper = mount(SkipLink)

    expect(wrapper.attributes('href'), 'the target of the link').toBe('#main')
  })

  it('stays out of the way until it receives focus', () => {
    const wrapper = mount(SkipLink)

    expect(wrapper.classes(), 'hidden visually, and shown only when focused').toEqual(
      expect.arrayContaining(['sr-only', 'focus:not-sr-only'])
    )
  })

  it('moves focus to main when it is activated', async () => {
    const main = addMain()
    const wrapper = mount(SkipLink, { attachTo: document.body })

    await wrapper.trigger('click')

    expect(document.activeElement, 'the focus after activation').toBe(main)
    wrapper.unmount()
  })

  it('does not change the address when it is activated', async () => {
    addMain()
    const before = window.location.href
    const wrapper = mount(SkipLink, { attachTo: document.body })

    await wrapper.trigger('click')

    expect(window.location.href, 'the address stays as it was, so no history entry is added').toBe(before)
    wrapper.unmount()
  })
})

describe('skip link placement in the application', () => {
  it('is the first element of the application, before the page', () => {
    const wrapper = mount(App, {
      global: { stubs: { 'router-view': true, MdSnackbar: true } }
    })

    const shell = wrapper.find('.min-h-screen').element
    expect(shell.firstElementChild, 'the first child of the shell').toBe(wrapper.find('a[href="#main"]').element)
    wrapper.unmount()
  })
})
