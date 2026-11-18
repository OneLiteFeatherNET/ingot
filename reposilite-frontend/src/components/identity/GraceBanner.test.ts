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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import GraceBanner from './GraceBanner.vue'

// The banner reads the grace end of the session. The session is replaced by a double, so the banner
// is tested without a server and without the browser clock.
const session = await vi.hoisted(async () => {
  const { ref } = await import('vue')
  return { graceUntil: ref<string | undefined>(undefined) }
})

vi.mock('../../store/session', () => ({
  useSession: () => ({ graceUntil: session.graceUntil })
}))

// The notice is the status region of the alert: the test selects on the role, never on a data-ingot anchor.
const banner = (wrapper: VueWrapper) => wrapper.find('[role="status"]')

describe('GraceBanner', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    session.graceUntil.value = undefined
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('is absent when the security status names no grace end', async () => {
    wrapper = mount(GraceBanner) as VueWrapper
    await flushPromises()

    expect(banner(wrapper).exists(), 'no grace period, no banner').toBe(false)
  })

  it('shows the year of the grace end it was given', async () => {
    session.graceUntil.value = '2026-12-02T00:00:00Z'
    wrapper = mount(GraceBanner) as VueWrapper
    await flushPromises()

    expect(banner(wrapper).text(), 'the end date is named in the banner').toContain('2026')
  })

  it('tells the user that a second factor is required before the grace end', async () => {
    session.graceUntil.value = '2026-12-02T00:00:00Z'
    wrapper = mount(GraceBanner) as VueWrapper
    await flushPromises()

    expect(banner(wrapper).text().toLowerCase(), 'the banner names the next step').toContain('second factor')
  })

  it('offers no dismiss control, because it is the only signal before the session is restricted', async () => {
    session.graceUntil.value = '2026-12-02T00:00:00Z'
    wrapper = mount(GraceBanner) as VueWrapper
    await flushPromises()

    expect(banner(wrapper).findAll('button'), 'the banner cannot be dismissed').toHaveLength(0)
  })

  it('is absent when the grace end is not a readable time', async () => {
    session.graceUntil.value = 'not a time'
    wrapper = mount(GraceBanner) as VueWrapper
    await flushPromises()

    expect(banner(wrapper).exists(), 'an unreadable date is not shown as a banner').toBe(false)
  })
})
