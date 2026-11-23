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
import { ref } from 'vue'
import type { Ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import ReadOnlyToggle from './ReadOnlyToggle.vue'

// The shape of the read-only state the probe answers with. The component keeps its own copy, which is not exported.
type ReadOnlyState = { enabled: boolean; source: string }
type ReadOnlyReply = { status: number; data: ReadOnlyState }
type ReadOnlyApi = {
  get: () => Promise<ReadOnlyReply>
  enable: () => Promise<ReadOnlyReply>
  disable: () => Promise<ReadOnlyReply>
}

// The toggle reads the session for its client. The client is replaced, so the test never
// reaches the network or the real token.
const session: { client: Ref<{ readOnly: ReadOnlyApi }> | null } = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))

const forbidden = () => Promise.reject(Object.assign(new Error('Request failed'), {
  response: { status: 403, data: {} }
}))

const clientWith = (overrides: Partial<ReadOnlyApi> = {}): { readOnly: ReadOnlyApi } => ({
  readOnly: {
    get: vi.fn(forbidden),
    enable: vi.fn(() => Promise.resolve({ status: 200, data: { enabled: true, source: 'stored' } })),
    disable: vi.fn(() => Promise.resolve({ status: 200, data: { enabled: false, source: 'stored' } })),
    ...overrides
  }
})

const answeringWith = (data: ReadOnlyState) => vi.fn(() => Promise.resolve({ status: 200, data }))

const CONSEQUENCE = 'Every upload, deploy and delete is refused with 503 on every instance until read-only mode is turned off. Reads continue, and scheduled cleanup and index runs wait.'

let wrapper: VueWrapper

const mountToggle = () => {
  wrapper = mount(ReadOnlyToggle, { attachTo: document.body }) as VueWrapper
}

const dialog = () => document.querySelector('[role="dialog"]')
const switchOf = () => wrapper.find('[role="switch"]')

describe('ReadOnlyToggle', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('hides the read-only toggle when the probe answers 403', async () => {
    session.client = ref(clientWith({ get: vi.fn(forbidden) }))
    mountToggle()
    await flushPromises()

    expect(switchOf().exists(), 'a principal without the maintenance area must not see the toggle').toBe(false)
    expect(wrapper.text(), 'no label of the toggle is shown either').not.toContain('Read-only mode')
  })

  it('shows the read-only toggle with the current state when the probe answers 200', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: true, source: 'stored' }) }))
    mountToggle()
    await flushPromises()

    expect(switchOf().exists(), 'a principal with the maintenance area sees the toggle').toBe(true)
    expect(switchOf().attributes('aria-checked'), 'the switch shows the state the server reported').toBe('true')
    expect(wrapper.text()).toContain('Read-only mode')
  })

  it('does not show the toggle while the probe is still pending', async () => {
    session.client = ref(clientWith({ get: vi.fn(() => new Promise<ReadOnlyReply>(() => {})) }))
    mountToggle()
    await flushPromises()

    expect(switchOf().exists(), 'nothing is shown before the probe has answered').toBe(false)
  })

  it('opens the confirmation and sends no request when the switch is turned on', async () => {
    const client = clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) })
    session.client = ref(client)
    mountToggle()
    await flushPromises()

    await switchOf().trigger('click')
    await flushPromises()

    expect(dialog(), 'turning on asks first').not.toBeNull()
    expect(client.readOnly.enable, 'no request before the confirm button is activated').not.toHaveBeenCalled()
  })

  it('sends exactly one enable request when the confirm button is activated', async () => {
    const client = clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) })
    session.client = ref(client)
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    document.getElementById('read-only-confirm')?.click()
    await flushPromises()

    expect(client.readOnly.enable, 'the confirm button sends the one request').toHaveBeenCalledTimes(1)
    expect(client.readOnly.disable, 'turning on never sends a disable').not.toHaveBeenCalled()
  })

  it('sends no request and leaves the switch off when the dialog is cancelled', async () => {
    const client = clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) })
    session.client = ref(client)
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    document.getElementById('read-only-cancel')?.click()
    await flushPromises()

    expect(client.readOnly.enable, 'cancel sends nothing').not.toHaveBeenCalled()
    expect(switchOf().attributes('aria-checked'), 'the switch stays off after cancel').toBe('false')
  })

  it('sends no request and leaves the switch off when the dialog is closed with Escape', async () => {
    const client = clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) })
    session.client = ref(client)
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(client.readOnly.enable, 'Escape sends nothing').not.toHaveBeenCalled()
    expect(switchOf().attributes('aria-checked'), 'the switch stays off after Escape').toBe('false')
  })

  it('turns read-only mode off with one request and no dialog', async () => {
    const client = clientWith({ get: answeringWith({ enabled: true, source: 'stored' }) })
    session.client = ref(client)
    mountToggle()
    await flushPromises()

    await switchOf().trigger('click')
    await flushPromises()

    expect(client.readOnly.disable, 'turning off sends one disable request').toHaveBeenCalledTimes(1)
    expect(dialog(), 'turning off needs no dialog').toBeNull()
  })

  it('describes the switch by the consequence, so a screen reader announces it on focus', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) }))
    mountToggle()
    await flushPromises()

    const describedBy = switchOf().attributes('aria-describedby') ?? ''
    expect(describedBy, 'the switch names the element that describes it').toBeTruthy()
    expect(document.getElementById(describedBy)?.textContent?.trim(), 'the description states the refusal and the continuing reads').toBe(CONSEQUENCE)
  })

  it('states in the dialog the same consequence the switch describes', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) }))
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    expect(dialog()?.textContent, 'the dialog opens with the sentence the switch describes').toContain(CONSEQUENCE)
  })

  it('disables the switch and names INGOT_READ_ONLY when the environment forces the state', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: true, source: 'environment' }) }))
    mountToggle()
    await flushPromises()

    expect(switchOf().attributes('disabled'), 'a forced state cannot be changed here').toBeDefined()
    expect(wrapper.text(), 'the reason is shown before the click').toContain('INGOT_READ_ONLY')
  })

  it('names the confirmation by its heading', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) }))
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    const labelledBy = dialog()?.getAttribute('aria-labelledby') ?? ''
    expect(document.getElementById(labelledBy)?.textContent?.trim(), 'the dialog is named by its question').toBe('Turn on read-only mode?')
  })

  it('moves focus to Cancel when the confirmation opens, so an accidental Enter does not confirm', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) }))
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    expect(document.activeElement?.id, 'focus starts on the cancel button').toBe('read-only-cancel')
  })

  it('returns focus to the switch when the confirmation is cancelled', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) }))
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    document.getElementById('read-only-cancel')?.click()
    await flushPromises()

    expect(document.activeElement?.id, 'focus returns to the switch').toBe('read-only-switch')
  })

  it('offers exactly one filled button in the confirmation, the confirm action', async () => {
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }) }))
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()

    const filled = [...(dialog()?.querySelectorAll('[data-variant="filled"]') ?? [])].map(button => button.textContent?.trim())
    expect(filled, 'the only filled button turns the mode on').toEqual(['Turn on read-only mode'])
  })

  it('shows a failed change as an alert in the error tone', async () => {
    const refused = vi.fn(() => Promise.reject(Object.assign(new Error('Request failed'), {
      response: { status: 409, data: { message: 'INGOT_READ_ONLY forces the state.' } }
    })))
    session.client = ref(clientWith({ get: answeringWith({ enabled: false, source: 'stored' }), enable: refused }))
    mountToggle()
    await flushPromises()
    await switchOf().trigger('click')
    await flushPromises()
    document.getElementById('read-only-confirm')?.click()
    await flushPromises()

    const alert = wrapper.find('[role="alert"][data-tone="error"]')
    expect(alert.exists(), 'the refusal is announced as an error alert').toBe(true)
    expect(alert.text(), 'the alert carries the text the server sent').toContain('INGOT_READ_ONLY forces the state.')
  })
})
