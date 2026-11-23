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

import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import PrincipalFactorsCard from './PrincipalFactorsCard.vue'
import MdCard from '../md/MdCard.vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'

type ErrorLike = { message?: string; response?: { data?: { message?: string } } }

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: ErrorLike | undefined) => error?.response?.data?.message ?? error?.message ?? 'request failed'
}))

const rejection = (status: number, message: string | undefined) =>
  Object.assign(new Error('failed'), { response: { status, data: { message } } })

// The factors of alice carry names and times, never a seed or a code.
const ALICE_FACTORS = [
  { id: 1, kind: 'TOTP', name: 'Phone', createdAt: '2026-10-02T09:00:00Z', lastUsedAt: null },
  { id: 2, kind: 'TOTP', name: 'Tablet', createdAt: '2026-10-03T09:00:00Z', lastUsedAt: null },
  { id: 3, kind: 'PASSKEY', name: 'Laptop', createdAt: '2026-10-04T09:00:00Z', lastUsedAt: null }
]

// The dialog is teleported to the body, so it is read from the document and not from the wrapper.
const dialog = (): HTMLElement => document.querySelector<HTMLElement>('[role="dialog"]')!
const dialogButton = (label: string) =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent?.trim() === label)!

describe('PrincipalFactorsCard', () => {
  let wrapper: VueWrapper
  let api: Record<'list' | 'resetAll', Mock>

  const mountCard = async () => {
    wrapper = mount(PrincipalFactorsCard, { props: { name: 'alice', api }, attachTo: document.body }) as VueWrapper
    await flushPromises()
  }

  it('shows the loading factors as a loading state panel', async () => {
    api.list.mockReturnValue(new Promise(() => {}))
    await mountCard()

    expect(wrapper.find('[aria-busy="true"]').exists(), 'the loading line is a busy state panel').toBe(true)
  })

  const countRows = (): string[] =>
    wrapper.findAll('[data-test="factor-count"]').map(row => row.text().replace(/\s+/g, ' ').trim())

  const openReset = async () => {
    await wrapper.find('[data-test="reset-factors"]').trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    vi.mocked(createErrorToast).mockClear()
    vi.mocked(createSuccessToast).mockClear()
    api = {
      list: vi.fn().mockResolvedValue({ data: ALICE_FACTORS }),
      resetAll: vi.fn().mockResolvedValue({ data: { removed: 3 } })
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined as unknown as VueWrapper
    document.body.innerHTML = ''
  })

  it('draws the second factors on the shared outlined card', async () => {
    await mountCard()

    const target = wrapper.get('#principal-factors-title').element
    const card = wrapper.findAllComponents(MdCard).filter(candidate => (candidate.element as Element).contains(target)).at(-1)
    expect(card, 'the factors sit on the card primitive, not on a hand-built container').toBeDefined()
    expect(card!.props('variant'), 'the card keeps the outlined filled surface').toBe('filled')
  })

  it('counts the authenticator apps and the passkeys of the user', async () => {
    await mountCard()

    expect(countRows(), 'one row per kind, with the number of factors of that kind').toEqual([
      'Authenticator apps: 2',
      'Passkeys: 1'
    ])
  })

  it('says that the user has no second factor when the list is empty', async () => {
    api.list.mockResolvedValue({ data: [] })
    await mountCard()

    expect(wrapper.text(), 'an empty summary names the state').toContain('No second factors')
    expect(countRows(), 'no count rows without factors').toEqual([])
  })

  it('does not send the reset before the administrator confirms it', async () => {
    await mountCard()
    await openReset()

    expect(api.resetAll, 'opening the confirmation sends nothing').not.toHaveBeenCalled()
  })

  it('names the user and says that the sessions end in the confirmation', async () => {
    await mountCard()
    await openReset()

    expect(dialog().textContent, 'the confirmation names the user').toContain('alice')
    expect(dialog().textContent, 'the confirmation says the sessions of the user end').toContain('All sessions of alice end')
  })

  it('closes the confirmation without a request when the administrator cancels', async () => {
    await mountCard()
    await openReset()
    dialogButton('Cancel').click()
    await flushPromises()

    expect(api.resetAll, 'cancelling sends nothing').not.toHaveBeenCalled()
  })

  it('resets the factors of the user once after the confirmation and shows the empty summary', async () => {
    await mountCard()
    api.list.mockResolvedValue({ data: [] })
    await openReset()
    dialogButton('Reset second factors').click()
    await flushPromises()

    expect(api.resetAll, 'the reset is sent once').toHaveBeenCalledTimes(1)
    expect(createSuccessToast, 'the administrator sees the success').toHaveBeenCalled()
    expect(wrapper.text(), 'the summary is read again after the reset').toContain('No second factors')
  })

  it('keeps the confirmation open and shows the server reason when the reset fails', async () => {
    api.resetAll.mockRejectedValue(rejection(403, 'Confirm with a second factor first, then try again.'))
    await mountCard()
    await openReset()
    dialogButton('Reset second factors').click()
    await flushPromises()

    expect(dialog().textContent, 'the server reason is shown in the dialog').toContain('Confirm with a second factor first')
    expect(countRows(), 'the summary still shows the factors that were not removed').toEqual([
      'Authenticator apps: 2',
      'Passkeys: 1'
    ])
  })
})
