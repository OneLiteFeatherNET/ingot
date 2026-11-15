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
import { mount, flushPromises } from '@vue/test-utils'
import FailuresSheet from './FailuresSheet.vue'

// The sheet reads the session for its client and raises a toast when an action fails. Both are
// replaced, so the test never reaches the network, the real token or the toast container.
const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))
vi.mock('../../helpers/toast', () => ({ createErrorToast: vi.fn() }))

// The clock is frozen, so "2 minutes ago" is the same on every machine and at every hour.
const NOW = '2026-10-15T12:00:00Z'

const NEWER = {
  id: 2,
  timestamp: '2026-10-15T11:58:00Z',
  source: '/api/maven/details',
  type: 'IllegalStateException',
  message: 'repository is broken',
  stacktrace: '  by IllegalStateException: repository is broken\n  at com.reposilite.Maven.details(Maven.kt:42)',
  count: 1
}

const OLDER = {
  id: 1,
  timestamp: '2026-10-15T09:00:00Z',
  source: '/com/reposilite/private',
  type: 'NullPointerException',
  message: null,
  stacktrace: '  by NullPointerException: null\n  at com.reposilite.Storage.read(Storage.kt:7)',
  count: 4
}

const clientWith = ({ failures, clearFailures = vi.fn(() => Promise.resolve({})) }) => ({
  status: {
    failures: failures ?? vi.fn(() => Promise.resolve({ data: { failures: [NEWER, OLDER] } })),
    clearFailures
  }
})

let wrapper

const mountSheet = () => {
  wrapper = mount(FailuresSheet, {
    props: { modelValue: true, 'onUpdate:modelValue': () => {} },
    attachTo: document.body
  })
}

// The sheet is teleported to the body, so the tests look for it there.
const dialog = () => document.querySelector('[role="dialog"]')
const dialogText = () => dialog()?.textContent.replace(/\s+/g, ' ').trim() ?? ''
const buttonNamed = (text) =>
  [...(dialog()?.querySelectorAll('button') ?? [])].find(button => button.textContent.trim() === text)

describe('FailuresSheet', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(Date.parse(NOW))
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('lists the failures in the order the server sends them, newest first', async () => {
    session.client = ref(clientWith({}))
    mountSheet()
    await flushPromises()

    const sources = [...dialog().querySelectorAll('li')].map(item => item.textContent)
    expect(sources[0], 'the newest failure must be the first entry').toContain('/api/maven/details')
    expect(sources[1], 'the older failure must follow it').toContain('/com/reposilite/private')
  })

  it('shows when each failure happened in words, and the exact time as a tooltip', async () => {
    session.client = ref(clientWith({}))
    mountSheet()
    await flushPromises()

    const time = dialog().querySelector('time[datetime="2026-10-15T11:58:00Z"]')
    expect(time.textContent.trim(), 'the relative time reads the way people talk').toBe('2 minutes ago')
    expect(time.getAttribute('title'), 'the exact moment is the tooltip').toBe('2026-10-15 11:58:00 UTC')
  })

  it('shows how often an aggregated failure happened', async () => {
    session.client = ref(clientWith({}))
    mountSheet()
    await flushPromises()

    expect(dialogText(), 'a failure seen four times says so').toContain('Seen 4 times')
  })

  it('keeps the stack trace hidden until it is asked for', async () => {
    session.client = ref(clientWith({}))
    mountSheet()
    await flushPromises()

    expect(dialog().querySelector('pre'), 'no stack trace is rendered before the toggle').toBeNull()

    buttonNamed('Show stack trace').click()
    await flushPromises()

    expect(dialog().querySelector('pre').textContent).toContain('at com.reposilite.Maven.details(Maven.kt:42)')
  })

  it('copies the report of a failure to the clipboard', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    session.client = ref(clientWith({}))
    mountSheet()
    await flushPromises()
    buttonNamed('Show stack trace').click()
    await flushPromises()

    buttonNamed('Copy').click()
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(writeText.mock.calls[0][0], 'the report carries the source and the stack trace').toContain('/api/maven/details')
    expect(writeText.mock.calls[0][0]).toContain('at com.reposilite.Maven.details(Maven.kt:42)')
    expect(buttonNamed('Copied'), 'the button confirms the copy').toBeDefined()
    Reflect.deleteProperty(navigator, 'clipboard')
  })

  it('removes every failure when asked to clear them', async () => {
    const clearFailures = vi.fn(() => Promise.resolve({}))
    session.client = ref(clientWith({ clearFailures }))
    mountSheet()
    await flushPromises()

    buttonNamed('Clear all').click()
    await flushPromises()

    expect(clearFailures, 'the clear request is sent once').toHaveBeenCalledTimes(1)
    expect(dialogText(), 'an empty list says so').toContain('No failures recorded')
    expect(wrapper.emitted('cleared'), 'the owner learns that the count has changed').toHaveLength(1)
  })

  it('offers a retry when the failures cannot be loaded', async () => {
    const failures = vi.fn()
      .mockRejectedValueOnce(new Error('unreachable'))
      .mockResolvedValueOnce({ data: { failures: [NEWER] } })
    session.client = ref(clientWith({ failures }))
    mountSheet()
    await flushPromises()

    expect(dialogText(), 'the sheet names what went wrong').toContain('Cannot load failures')

    buttonNamed('Retry').click()
    await flushPromises()

    expect(failures, 'retry sends a second request').toHaveBeenCalledTimes(2)
    expect(dialogText()).toContain('repository is broken')
  })
})
