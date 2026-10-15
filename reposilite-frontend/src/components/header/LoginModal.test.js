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
import { useSession } from '../../store/session'

// The actions that reach the server are replaced; the state refs and the failure wording are the
// real ones, so the dialog is tested with the mapping the app actually uses.
const actions = vi.hoisted(() => ({
  signInWithPassword: null,
  signInWithToken: null,
  completeSetup: null,
  loadProviders: null
}))

vi.mock('../../store/session', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useSession: () => ({
      ...actual.useSession(),
      signInWithPassword: (...args) => actions.signInWithPassword(...args),
      signInWithToken: (...args) => actions.signInWithToken(...args),
      completeSetup: (...args) => actions.completeSetup(...args),
      loadProviders: (...args) => actions.loadProviders(...args)
    })
  }
})

const LOCAL = { id: 'local', type: 'LOCAL', label: 'Password' }

const rejection = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {} }
  })

// The dialog is teleported to the end of the body, so it is queried from the document.
const dialog = () => document.querySelector('[role="dialog"]')
const field = (id) => document.getElementById(id)
const buttonLabels = () => [...dialog().querySelectorAll('button, a')].map(node => node.textContent.trim())
const buttonByLabel = (label) =>
  [...dialog().querySelectorAll('button, a')].find(node => node.textContent.trim() === label)

const type = (id, value) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
}

const submit = async () => {
  dialog().querySelector('form').dispatchEvent(new Event('submit'))
  await flushPromises()
}

const helperOf = (id) => document.getElementById(field(id).getAttribute('aria-describedby'))

describe('LoginModal', () => {
  let wrapper
  let store

  const openDialog = async () => {
    await wrapper.find('button').trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    actions.signInWithPassword = vi.fn().mockResolvedValue()
    actions.signInWithToken = vi.fn().mockResolvedValue()
    actions.completeSetup = vi.fn().mockResolvedValue()
    actions.loadProviders = vi.fn().mockResolvedValue()
    store = useSession()
    store.providers.value = [LOCAL]
    store.setupRequired.value = false
    wrapper = mount(LoginModal, { attachTo: document.body })
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  describe('the dialog', () => {
    it('opens the sign in dialog from a real button', async () => {
      const trigger = wrapper.find('button')

      expect(trigger.text(), 'the sign in trigger must be a button with a visible name').toBe('Sign in')

      await openDialog()

      expect(dialog(), 'the dialog must open when the trigger is pressed').not.toBeNull()
    })

    it('opens when another part of the app asks for the sign in dialog', async () => {
      expect(dialog(), 'the dialog starts closed').toBeNull()

      openLogin()
      await flushPromises()

      expect(dialog(), 'a request from outside the header must open the dialog').not.toBeNull()
    })

    it('renders the dialog directly under the body so page content cannot paint over it', async () => {
      await openDialog()

      expect(dialog().parentElement, 'a dialog left in place is stacked by the z-order of the page content')
        .toBe(document.body)
    })

    it('names the dialog after its heading', async () => {
      await openDialog()

      const heading = dialog().querySelector('h2')
      expect(heading.textContent.trim()).toBe('Sign in')
      expect(dialog().getAttribute('aria-labelledby'), 'screen readers announce the dialog by its heading')
        .toBe(heading.id)
    })

    it('asks the server for the providers each time it opens', async () => {
      await openDialog()

      expect(actions.loadProviders).toHaveBeenCalledTimes(1)
    })

    it('focuses the first field once the dialog reports that it has opened', async () => {
      // The real "opened" event waits for the CSS transition to end, which jsdom never does.
      await openDialog()

      wrapper.findComponent(VueFinalModal).vm.$emit('opened')

      expect(document.activeElement, 'keyboard users must start typing in the username field')
        .toBe(field('login-username'))
    })

    it('labels the way out of the dialog as Cancel', async () => {
      await openDialog()

      expect(buttonLabels(), 'closing without signing in is a cancel, not a return to an index')
        .toContain('Cancel')
    })
  })

  describe('password sign in', () => {
    it('shows a username and a password field for the local provider', async () => {
      await openDialog()

      expect(field('login-username').type).toBe('text')
      expect(field('login-username').autocomplete).toBe('username')
      expect(field('login-password').type, 'a password must be masked').toBe('password')
      expect(field('login-password').autocomplete).toBe('current-password')
      expect(field('login-username').required).toBe(true)
      expect(field('login-password').required).toBe(true)
    })

    it('sends exactly the username and the password', async () => {
      await openDialog()
      type('login-username', 'alice')
      type('login-password', 'correct horse battery')

      await submit()

      expect(actions.signInWithPassword).toHaveBeenCalledTimes(1)
      expect(actions.signInWithPassword).toHaveBeenCalledWith({ username: 'alice', password: 'correct horse battery' })
    })

    it('closes after a successful sign in', async () => {
      await openDialog()
      type('login-username', 'alice')
      type('login-password', 'pw')

      await submit()

      expect(dialog()?.querySelector('h2'), 'a signed in user does not need the dialog').toBeFalsy()
    })

    const submitRejected = async (status) => {
      actions.signInWithPassword.mockRejectedValue(rejection(status))
      await openDialog()
      type('login-username', 'alice')
      type('login-password', 'wrong')
      await submit()
    }

    it('shows one inline message when the server rejects the password', async () => {
      await submitRejected(401)

      expect(helperOf('login-password')?.textContent.trim(),
        'the failure is explained in words and linked to the field')
        .toBe('Username or password is incorrect')
    })

    it('shows the same message for a disabled account, so it does not reveal that the user exists', async () => {
      await submitRejected(403)

      expect(helperOf('login-password')?.textContent.trim()).toBe('Username or password is incorrect')
    })

    it('moves focus to the password field after a rejection so the message is read out', async () => {
      await submitRejected(401)

      expect(document.activeElement).toBe(field('login-password'))
    })

    it('keeps the dialog open after a rejection', async () => {
      await submitRejected(401)

      expect(dialog()).not.toBeNull()
    })

    it('clears the password when the dialog is closed', async () => {
      await openDialog()
      type('login-password', 'secret-to-forget')

      buttonByLabel('Cancel').click()
      await flushPromises()
      await openDialog()

      expect(field('login-password').value, 'a password must not outlive the dialog').toBe('')
    })
  })

  describe('providers', () => {
    it('shows a provider with a login url as a link that goes there', async () => {
      store.providers.value = [LOCAL, { id: 'oidc', type: 'OIDC', label: 'Continue with Example', loginUrl: '/api/auth/oidc/start' }]

      await openDialog()

      const link = buttonByLabel('Continue with Example')
      expect(link.tagName).toBe('A')
      expect(link.getAttribute('href'), 'the button navigates to the provider').toBe('/api/auth/oidc/start')
    })

    it('shows a provider without a login url as a disabled button with its label', async () => {
      store.providers.value = [LOCAL, { id: 'saml', type: 'SAML', label: 'Corporate SSO' }]

      await openDialog()

      const button = buttonByLabel('Corporate SSO')
      expect(button, 'the provider is listed even when it cannot be used').not.toBeUndefined()
      expect(button.hasAttribute('disabled') || button.getAttribute('aria-disabled') === 'true').toBe(true)
    })

    it('shows no password form when there is no local provider', async () => {
      store.providers.value = [{ id: 'oidc', type: 'OIDC', label: 'Continue with Example', loginUrl: '/x' }]

      await openDialog()

      expect(field('login-password')).toBeNull()
    })
  })

  describe('access token sign in', () => {
    const switchToToken = async () => {
      await openDialog()
      buttonByLabel('Use an access token instead').click()
      await flushPromises()
    }

    it('offers the access token form through a link', async () => {
      await openDialog()

      expect(buttonLabels()).toContain('Use an access token instead')
    })

    it('switches to a name and secret form', async () => {
      await switchToToken()

      expect(dialog().querySelector('h2').textContent.trim()).toBe('Sign in with access token')
      expect(field('login-name').required).toBe(true)
      expect(field('login-secret').type).toBe('password')
      expect(field('login-secret').autocomplete).toBe('current-password')
      expect(field('login-password'), 'the password form is replaced').toBeNull()
    })

    it('sends the name and the secret', async () => {
      await switchToToken()
      type('login-name', 'ci')
      type('login-secret', 's3cret')

      await submit()

      expect(actions.signInWithToken).toHaveBeenCalledWith('ci', 's3cret')
    })

    it('shows that the name or the secret is incorrect when the server rejects them', async () => {
      actions.signInWithToken.mockRejectedValue(rejection(401))
      await switchToToken()
      type('login-name', 'ci')
      type('login-secret', 'wrong')

      await submit()

      expect(helperOf('login-secret')?.textContent.trim()).toBe('Name or secret is incorrect')
      expect(document.activeElement).toBe(field('login-secret'))
    })

    it('goes back to the providers', async () => {
      await switchToToken()

      buttonByLabel('Use a password instead').click()
      await flushPromises()

      expect(field('login-password')).not.toBeNull()
    })
  })

  describe('first run setup', () => {
    const PASSWORD = 'a-long-enough-password'

    beforeEach(() => {
      store.setupRequired.value = true
    })

    const fillSetup = ({ code = 'abc123', username = 'root', display = '', password = PASSWORD, confirm = PASSWORD } = {}) => {
      type('setup-code', code)
      type('setup-username', username)
      type('setup-display-name', display)
      type('setup-password', password)
      type('setup-confirm', confirm)
    }

    it('shows Set up Ingot instead of the sign in form', async () => {
      await openDialog()

      expect(dialog().querySelector('h2').textContent.trim()).toBe('Set up Ingot')
      expect(field('login-username'), 'the sign in form is not offered during setup').toBeNull()
    })

    it('points to the server log for the setup code', async () => {
      await openDialog()

      expect(helperOf('setup-code').textContent).toMatch(/server log/)
    })

    it('shows the password length hint as helper text', async () => {
      await openDialog()

      expect(helperOf('setup-password').textContent).toMatch(/at least 12 characters/i)
    })

    it('masks both password fields', async () => {
      await openDialog()

      expect(field('setup-password').type).toBe('password')
      expect(field('setup-password').autocomplete).toBe('new-password')
      expect(field('setup-confirm').type).toBe('password')
      expect(field('setup-confirm').autocomplete).toBe('new-password')
    })

    it('does not submit when the confirmation does not match', async () => {
      await openDialog()
      fillSetup({ confirm: 'something else entirely' })

      await submit()

      expect(actions.completeSetup, 'a mistyped password must not become the admin password').not.toHaveBeenCalled()
      expect(helperOf('setup-confirm').textContent.trim()).toBe('Passwords do not match')
    })

    it('does not submit a password under 12 characters', async () => {
      await openDialog()
      fillSetup({ password: 'short', confirm: 'short' })

      await submit()

      expect(actions.completeSetup, 'the server would answer 400, so the dialog stops it first').not.toHaveBeenCalled()
      expect(helperOf('setup-password').textContent).toMatch(/at least 12 characters/i)
      expect(field('setup-password').getAttribute('aria-invalid')).toBe('true')
    })

    it('sends exactly the setup code, username, password and display name', async () => {
      await openDialog()
      fillSetup({ display: 'Root Admin' })

      await submit()

      expect(actions.completeSetup).toHaveBeenCalledWith({
        setupCode: 'abc123',
        username: 'root',
        password: PASSWORD,
        displayName: 'Root Admin'
      })
    })

    it('leaves out the display name when it is empty', async () => {
      await openDialog()
      fillSetup()

      await submit()

      expect(actions.completeSetup.mock.calls[0][0]).toEqual({
        setupCode: 'abc123',
        username: 'root',
        password: PASSWORD
      })
      expect(actions.completeSetup.mock.calls[0][0]).not.toHaveProperty('displayName')
    })

    it('closes after a successful setup', async () => {
      await openDialog()
      fillSetup()

      await submit()

      expect(dialog()?.querySelector('h2'), 'the dialog content is gone').toBeFalsy()
    })

    it('says so when the setup code is wrong', async () => {
      actions.completeSetup.mockRejectedValue(rejection(401))
      await openDialog()
      fillSetup()

      await submit()

      expect(dialog().querySelector('[role="alert"]').textContent.trim()).toBe('Setup code is incorrect')
    })
  })
})
