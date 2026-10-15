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
import { createMemoryHistory, createRouter } from 'vue-router'
import { VueFinalModal } from 'vue-final-modal'
import { siGithub } from 'simple-icons'
import LoginModal from './LoginModal.vue'
import { openLogin } from '../../store/login'
import { useSession } from '../../store/session'
import { resetClock, setClock } from '../../helpers/clock'

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

const rejection = (status, headers = {}) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: {}, headers }
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

// A link's address, read against the test origin whether it is relative or absolute.
const urlOf = (link) => new URL(link.getAttribute('href'), 'http://ingot.test')

const LDAP = { id: 'ldap', type: 'LDAP', label: 'Corporate LDAP', icon: 'GENERIC' }
const KEYCLOAK = { id: 'keycloak', type: 'OIDC', label: 'Keycloak', icon: 'KEYCLOAK', loginUrl: '/api/auth/oidc/keycloak/login' }
const GITHUB = { id: 'github', type: 'OIDC', label: 'GitHub', icon: 'GITHUB', loginUrl: '/api/auth/oidc/github/login' }
const dividerOf = () => dialog().querySelector('[data-test="divider"]')
const segment = (label) => buttonByLabel(label)

describe('LoginModal', () => {
  let wrapper
  let store
  let router

  const openDialog = async () => {
    await wrapper.find('button').trigger('click')
    await flushPromises()
  }

  // A memory router: the dialog reads the current path to send a provider back to it.
  const makeRouter = () => createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }]
  })

  beforeEach(async () => {
    actions.signInWithPassword = vi.fn().mockResolvedValue()
    actions.signInWithToken = vi.fn().mockResolvedValue()
    actions.completeSetup = vi.fn().mockResolvedValue()
    actions.loadProviders = vi.fn().mockResolvedValue()
    store = useSession()
    store.providers.value = [LOCAL]
    store.setupRequired.value = false
    router = makeRouter()
    await router.push('/')
    wrapper = mount(LoginModal, { attachTo: document.body, global: { plugins: [router] } })
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
    resetClock()
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
      expect(actions.signInWithPassword).toHaveBeenCalledWith({ provider: 'local', username: 'alice', password: 'correct horse battery' })
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

  describe('rate-limited password sign in', () => {
    const START = Date.parse('2026-10-15T12:00:00Z')
    let current

    const tickTo = (offsetSeconds) => {
      current = START + offsetSeconds * 1000
    }

    const submitLimited = async (headers) => {
      current = START
      setClock(() => new Date(current))
      actions.signInWithPassword.mockRejectedValue(rejection(429, headers))
      await openDialog()
      type('login-username', 'alice')
      type('login-password', 'wrong')
      await submit()
    }

    const submitButton = () => buttonByLabel('Sign in')

    it('says how many minutes to wait, rounded up', async () => {
      await submitLimited({ 'retry-after': '121' })

      expect(helperOf('login-password')?.textContent.trim()).toBe('Too many attempts, try again in 3 minutes')
    })

    it('uses the singular for one minute', async () => {
      await submitLimited({ 'retry-after': '30' })

      expect(helperOf('login-password')?.textContent.trim()).toBe('Too many attempts, try again in 1 minute')
    })

    it('disables the submit button while the wait lasts', async () => {
      await submitLimited({ 'retry-after': '120' })

      expect(submitButton().disabled, 'a locked sign in must not send another attempt').toBe(true)
    })

    it('keeps the password field and what was typed in it', async () => {
      await submitLimited({ 'retry-after': '120' })

      expect(field('login-password'), 'the field stays in the dialog').not.toBeNull()
      expect(field('login-password').disabled).toBe(false)
      expect(field('login-password').value).toBe('wrong')
    })

    it('does not send an attempt when the form is submitted during the wait', async () => {
      await submitLimited({ 'retry-after': '120' })
      actions.signInWithPassword.mockClear()

      await submit()

      expect(actions.signInWithPassword).not.toHaveBeenCalled()
    })

    it('counts the remaining wait down from the injected clock', async () => {
      await submitLimited({ 'retry-after': '180' })

      tickTo(70)
      type('login-username', 'alice2')
      await flushPromises()

      expect(helperOf('login-password')?.textContent.trim()).toBe('Too many attempts, try again in 2 minutes')
      expect(submitButton().disabled).toBe(true)
    })

    it('enables the submit button and drops the message once the time has passed', async () => {
      await submitLimited({ 'retry-after': '120' })

      tickTo(120)
      type('login-username', 'alice2')
      await flushPromises()

      expect(submitButton().disabled, 'the wait is over').toBe(false)
      expect(field('login-password').getAttribute('aria-invalid'), 'the old limit is not an error any more').toBeNull()
    })

    it('sends the next attempt after the wait', async () => {
      await submitLimited({ 'retry-after': '60' })
      actions.signInWithPassword.mockReset().mockResolvedValue()

      tickTo(60)
      type('login-username', 'alice')
      await submit()

      expect(actions.signInWithPassword).toHaveBeenCalledWith({ provider: 'local', username: 'alice', password: 'wrong' })
    })

    it('keeps the wait when the dialog is closed and opened again', async () => {
      await submitLimited({ 'retry-after': '120' })

      buttonByLabel('Cancel').click()
      await flushPromises()
      await openDialog()

      expect(submitButton().disabled, 'closing the dialog is no way around the limit').toBe(true)
    })

    it('asks to try again later when the server gives no Retry-After', async () => {
      await submitLimited({})

      expect(helperOf('login-password')?.textContent.trim()).toBe('Too many attempts, try again later')
      expect(submitButton().disabled, 'without a deadline the button cannot be locked').toBe(false)
    })
  })

  describe('providers', () => {
    it('shows a provider with a login url as a link that goes there', async () => {
      store.providers.value = [LOCAL, { id: 'oidc', type: 'OIDC', label: 'Example', loginUrl: '/api/auth/oidc/start' }]

      await openDialog()

      const link = buttonByLabel('Continue with Example')
      expect(link.tagName).toBe('A')
      expect(urlOf(link).pathname, 'the button navigates to the provider').toBe('/api/auth/oidc/start')
    })

    it('shows a provider without a login url as a disabled button with its label', async () => {
      store.providers.value = [LOCAL, { id: 'corp', type: 'OIDC', label: 'Corporate SSO' }]

      await openDialog()

      const button = buttonByLabel('Continue with Corporate SSO')
      expect(button, 'the provider is listed even when it cannot be used').not.toBeUndefined()
      expect(button.hasAttribute('disabled') || button.getAttribute('aria-disabled') === 'true').toBe(true)
    })

    it('shows no password form when there is no local provider', async () => {
      store.providers.value = [{ id: 'oidc', type: 'OIDC', label: 'Continue with Example', loginUrl: '/x' }]

      await openDialog()

      expect(field('login-password')).toBeNull()
    })
  })

  describe('sign in with several providers', () => {
    const submitNode = () => buttonByLabel('Sign in')

    it('shows a switch between the Ingot account and the LDAP provider when both are enabled', async () => {
      store.providers.value = [LOCAL, LDAP]

      await openDialog()

      expect(dialog().querySelector('[role="group"]').getAttribute('aria-label')).toBe('Sign in method')
      expect(segment('Ingot account'), 'the Ingot account is named by its own label').toBeDefined()
      expect(segment('Corporate LDAP'), 'the LDAP provider is named by its label').toBeDefined()
    })

    it('starts on the Ingot account', async () => {
      store.providers.value = [LOCAL, LDAP]

      await openDialog()

      expect(segment('Ingot account').getAttribute('aria-pressed')).toBe('true')
      expect(segment('Corporate LDAP').getAttribute('aria-pressed')).toBe('false')
    })

    it('sends the provider id of the selected switch with the credentials', async () => {
      store.providers.value = [LOCAL, LDAP]
      await openDialog()
      segment('Corporate LDAP').click()
      await flushPromises()
      type('login-username', 'ldapuser')
      type('login-password', 'ldap-password-12')

      await submit()

      expect(actions.signInWithPassword).toHaveBeenCalledWith({
        provider: 'ldap',
        username: 'ldapuser',
        password: 'ldap-password-12'
      })
    })

    it('keeps the typed username when the provider changes', async () => {
      store.providers.value = [LOCAL, LDAP]
      await openDialog()
      type('login-username', 'alice')

      segment('Corporate LDAP').click()
      await flushPromises()

      expect(field('login-username').value).toBe('alice')
    })

    it('shows no switch and a single form when only the LDAP provider is enabled', async () => {
      store.providers.value = [LDAP]

      await openDialog()
      type('login-username', 'ldapuser')
      type('login-password', 'ldap-password-12')
      await submit()

      expect(dialog().querySelector('[role="group"]')).toBeNull()
      expect(actions.signInWithPassword).toHaveBeenCalledWith({
        provider: 'ldap',
        username: 'ldapuser',
        password: 'ldap-password-12'
      })
    })

    it('puts the Sign in button full width under the fields, inside the form', async () => {
      store.providers.value = [LOCAL]

      await openDialog()

      const button = submitNode()
      expect(button.type, 'the button submits the form, so password managers see the pair').toBe('submit')
      expect(button.closest('form'), 'the button belongs to the form it submits').toBe(dialog().querySelector('form'))
      expect(button.classList.contains('w-full')).toBe(true)
    })

    it('separates the SSO buttons from the form with an or divider that is hidden from assistive technology', async () => {
      store.providers.value = [LOCAL, KEYCLOAK]

      await openDialog()

      expect(dividerOf().getAttribute('aria-hidden')).toBe('true')
      expect(dividerOf().textContent.trim()).toBe('or')
      expect(
        dividerOf().compareDocumentPosition(buttonByLabel('Continue with Keycloak')) & Node.DOCUMENT_POSITION_FOLLOWING,
        'the divider sits between the form and the SSO buttons'
      ).toBeTruthy()
    })

    it('shows no divider when there is no password form', async () => {
      store.providers.value = [KEYCLOAK]

      await openDialog()

      expect(dividerOf()).toBeNull()
    })

    it('shows the SSO buttons alone, without a form, when only SSO providers exist', async () => {
      store.providers.value = [KEYCLOAK, GITHUB]

      await openDialog()

      expect(dialog().querySelector('form')).toBeNull()
      expect(dialog().querySelector('button[type="submit"]')).toBeNull()
      expect(buttonByLabel('Continue with Keycloak')).toBeDefined()
      expect(buttonByLabel('Continue with GitHub')).toBeDefined()
    })

    it('shows one outlined button per SSO provider, named Continue with its label', async () => {
      store.providers.value = [LOCAL, KEYCLOAK, GITHUB]

      await openDialog()

      const keycloak = buttonByLabel('Continue with Keycloak')
      expect(keycloak.classList.contains('border'), 'an outlined button has a border').toBe(true)
      expect(keycloak.classList.contains('w-full')).toBe(true)
      expect(buttonByLabel('Continue with GitHub')).toBeDefined()
    })

    it('draws the provider mark in the button, in the text colour', async () => {
      store.providers.value = [LOCAL, GITHUB]

      await openDialog()

      const svg = buttonByLabel('Continue with GitHub').querySelector('svg')
      expect(svg.querySelector('path').getAttribute('d')).toBe(siGithub.path)
      expect(svg.getAttribute('fill')).toBe('currentColor')
      expect(svg.getAttribute('aria-hidden')).toBe('true')
    })

    it('sends the browser to the provider with the current path as the redirect', async () => {
      await router.push('/access/sso')
      store.providers.value = [LOCAL, KEYCLOAK]

      await openDialog()

      const url = urlOf(buttonByLabel('Continue with Keycloak'))
      expect(url.pathname, 'the provider starts its login at its own address').toBe('/api/auth/oidc/keycloak/login')
      expect(url.searchParams.get('redirect'), 'the provider returns the visitor to this page').toBe('/access/sso')
    })

    it('keeps the query of the current page in the redirect', async () => {
      await router.push('/access/users?tab=2')
      store.providers.value = [KEYCLOAK]

      await openDialog()

      expect(urlOf(buttonByLabel('Continue with Keycloak')).searchParams.get('redirect')).toBe('/access/users?tab=2')
    })

    it('keeps the access token link and Cancel after the SSO buttons', async () => {
      store.providers.value = [LOCAL, KEYCLOAK]

      await openDialog()

      const sso = buttonByLabel('Continue with Keycloak')
      const tokenLink = buttonByLabel('Use an access token instead')
      expect(sso.compareDocumentPosition(tokenLink) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
      expect(buttonLabels()).toContain('Cancel')
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
