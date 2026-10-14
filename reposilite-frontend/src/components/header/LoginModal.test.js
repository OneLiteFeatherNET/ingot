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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { VueFinalModal } from 'vue-final-modal'
import LoginModal from './LoginModal.vue'
import { openLogin } from '../../store/login'

// Only the login call is replaced. The real loginFailureMessage decides the wording, so the
// dialog is tested with the mapping the app actually uses.
const session = vi.hoisted(() => ({ login: null }))

vi.mock('../../store/session', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useSession: () => ({ ...actual.useSession(), login: (...args) => session.login(...args) })
  }
})

const rejection = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} }
  })

// The dialog is teleported to the end of the body, so it is queried from the document.
const dialog = () => document.querySelector('[role="dialog"]')
const field = (id) => document.getElementById(id)

const openDialog = async (wrapper) => {
  await wrapper.find('button').trigger('click')
  await flushPromises()
}

describe('LoginModal', () => {
  let wrapper

  beforeEach(() => {
    session.login = vi.fn()
    wrapper = mount(LoginModal, { attachTo: document.body })
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('opens the sign in dialog from a real button', async () => {
    const trigger = wrapper.find('button')

    expect(trigger.text(), 'the sign in trigger must be a button with a visible name').toBe('Sign in')

    await openDialog(wrapper)

    expect(dialog(), 'the dialog must open when the trigger is pressed').not.toBeNull()
  })

  it('opens when another part of the app asks for the sign in dialog', async () => {
    // The console and other views call openLogin() when a session has expired. The dialog
    // is opened from here, so the request does not depend on the header button.
    expect(dialog(), 'the dialog starts closed').toBeNull()

    openLogin()
    await flushPromises()

    expect(dialog(), 'a request from outside the header must open the dialog').not.toBeNull()
  })

  it('names the dialog after its heading', async () => {
    await openDialog(wrapper)

    const heading = dialog().querySelector('h2')
    expect(heading.textContent.trim()).toBe('Sign in with access token')
    expect(dialog().getAttribute('aria-labelledby'), 'screen readers announce the dialog by its heading')
      .toBe(heading.id)
  })

  it('focuses the name field once the dialog reports that it has opened', async () => {
    // The real "opened" event waits for the CSS transition to end, which jsdom never does.
    // The event is therefore emitted directly, and the handler is what is under test.
    await openDialog(wrapper)

    wrapper.findComponent(VueFinalModal).vm.$emit('opened')

    expect(document.activeElement, 'keyboard users must start typing in the name field')
      .toBe(field('login-name'))
  })

  it('requires both the name and the secret', async () => {
    await openDialog(wrapper)

    expect(field('login-name').required, 'an empty name must be caught before the request').toBe(true)
    expect(field('login-secret').required, 'an empty secret must be caught before the request').toBe(true)
  })

  const submitRejectedPair = async () => {
    session.login.mockRejectedValue(rejection(401))
    await openDialog(wrapper)

    field('login-name').value = 'alice'
    field('login-name').dispatchEvent(new Event('input'))
    field('login-secret').value = 'wrong'
    field('login-secret').dispatchEvent(new Event('input'))
    dialog().querySelector('form').dispatchEvent(new Event('submit'))
    await flushPromises()
  }

  it('shows that the name or the secret is incorrect when the server rejects them', async () => {
    await submitRejectedPair()

    const helper = document.getElementById(field('login-secret').getAttribute('aria-describedby'))
    expect(helper?.textContent.trim(),
      'a rejected pair must be explained in words, not as a status code, and linked to the field')
      .toBe('Name or secret is incorrect')
  })

  it('moves focus to the secret field after a rejected sign in so the message is read out', async () => {
    await submitRejectedPair()

    expect(document.activeElement, 'focus on the field that carries the message makes it announced')
      .toBe(field('login-secret'))
  })

  it('labels the way out of the dialog as Cancel', async () => {
    await openDialog(wrapper)

    const labels = [...dialog().querySelectorAll('button')].map(button => button.textContent.trim())
    expect(labels, 'closing without signing in is a cancel, not a return to an index')
      .toContain('Cancel')
  })
})
