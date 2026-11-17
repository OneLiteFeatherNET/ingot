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

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MdCopyableCode from './MdCopyableCode.vue'

// The copy helper is replaced by a fake, so no test touches a real clipboard or a real selection.
const { copyToClipboard, selectText } = vi.hoisted(() => ({
  copyToClipboard: vi.fn(),
  selectText: vi.fn()
}))
vi.mock('../card/copySnippet', () => ({ copyToClipboard, selectText }))

const SNIPPET = 'implementation("org.example:demo:1.0.0")'

const mountCopyable = (props: Record<string, unknown> = {}) =>
  mount(MdCopyableCode, {
    props: { text: SNIPPET, label: 'Gradle snippet', ...props }
  })

const copyButton = (wrapper: ReturnType<typeof mountCopyable>) => wrapper.find('button')
const status = (wrapper: ReturnType<typeof mountCopyable>) => wrapper.find('[data-test="copy-status"]')

beforeEach(() => {
  copyToClipboard.mockReset()
  selectText.mockReset()
})

describe('MdCopyableCode content', () => {
  it('shows the text in the code block', () => {
    expect(mountCopyable().find('pre code').text()).toBe(SNIPPET)
  })

  it('names the scroll region after its label', () => {
    expect(mountCopyable().find('[role="region"]').attributes('aria-label')).toBe('Gradle snippet')
  })
})

describe('MdCopyableCode copy action', () => {
  it('gives the copy button an accessible name, the default one when no copy label is given', () => {
    expect(copyButton(mountCopyable()).attributes('aria-label')).toBe('Copy code')
  })

  it('uses the copy label it is given as the accessible name', () => {
    expect(copyButton(mountCopyable({ copyLabel: 'Copy Gradle snippet' })).attributes('aria-label')).toBe('Copy Gradle snippet')
  })

  it('hands the text to the copy helper with the clipboard on a click', async () => {
    copyToClipboard.mockResolvedValue(true)
    const wrapper = mountCopyable()

    await copyButton(wrapper).trigger('click')
    await flushPromises()

    expect(copyToClipboard).toHaveBeenCalledWith(SNIPPET, navigator.clipboard)
  })
})

describe('MdCopyableCode status', () => {
  it('renders the status in a polite live region that is empty before any copy', () => {
    const line = status(mountCopyable())

    expect(line.attributes('role')).toBe('status')
    expect(line.attributes('aria-live')).toBe('polite')
    expect(line.text()).toBe('')
  })

  it('announces Copied once the copy helper succeeds, and selects nothing', async () => {
    copyToClipboard.mockResolvedValue(true)
    const wrapper = mountCopyable()

    await copyButton(wrapper).trigger('click')
    await flushPromises()

    expect(status(wrapper).text()).toBe('Copied')
    expect(selectText, 'a successful copy does not select the code').not.toHaveBeenCalled()
  })

  it('selects the code and tells the user how to finish the copy when the helper fails', async () => {
    copyToClipboard.mockResolvedValue(false)
    const wrapper = mountCopyable()

    await copyButton(wrapper).trigger('click')
    await flushPromises()

    expect(selectText, 'the code is selected for the keyboard shortcut').toHaveBeenCalledTimes(1)
    expect(status(wrapper).text(), 'the status names the shortcut').toContain('Ctrl+C')
    expect(status(wrapper).text(), 'a failed copy is not announced as copied').not.toBe('Copied')
  })
})

describe('MdCopyableCode anchors', () => {
  it('carries the md-copyable-code anchor on the root element', () => {
    expect(mountCopyable().attributes('data-ingot-component')).toBe('md-copyable-code')
  })
})
