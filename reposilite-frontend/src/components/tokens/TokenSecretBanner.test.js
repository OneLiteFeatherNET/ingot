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

import { describe, it, expect, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TokenSecretBanner from './TokenSecretBanner.vue'

// The clipboard is replaced per test; jsdom does not provide one.
const stubClipboard = (writeText) => {
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
}

const mountBanner = () => mount(TokenSecretBanner, { props: { name: 'ci', value: 's3cr3t' } })

describe('TokenSecretBanner', () => {
  afterEach(() => {
    delete navigator.clipboard
  })

  it('copies the secret to the clipboard when the copy button is pressed', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    stubClipboard(writeText)
    const wrapper = mountBanner()

    await wrapper.find('[aria-label="Copy the secret of token ci"]').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('s3cr3t')
  })

  it('confirms the copy in the status region', async () => {
    stubClipboard(() => Promise.resolve())
    const wrapper = mountBanner()

    await wrapper.find('[aria-label="Copy the secret of token ci"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="status"]').text()).toContain('Copied')
  })

  it('reports that copying failed when the clipboard rejects the secret', async () => {
    stubClipboard(() => Promise.reject(new Error('denied')))
    const wrapper = mountBanner()

    await wrapper.find('[aria-label="Copy the secret of token ci"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Copying failed')
  })

  it('emits dismiss when the secret is dismissed', async () => {
    const wrapper = mountBanner()

    await wrapper.findAll('button').find(button => button.text() === 'Dismiss').trigger('click')

    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })
})
