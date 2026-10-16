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
import SnippetsCard from './SnippetsCard.vue'

// The repository level needs no request, so the card never touches the network here.
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  return {
    useSession: () => ({
      client: ref({ maven: { content: () => new Promise(() => {}) } })
    })
  }
})

let wrapper

const mountCard = async () => {
  wrapper = mount(SnippetsCard, {
    props: { qualifier: { path: '' } },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const copyButton = () =>
  wrapper.find('button[aria-label="Copy snippet"]')

// Installs a clipboard for one test. The original is put back in afterEach.
const withClipboard = (clipboard) => {
  Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true })
}

describe('SnippetsCard copy action', () => {
  beforeEach(() => {
    window.getSelection().removeAllRanges()
  })

  afterEach(() => {
    delete navigator.clipboard
    window.getSelection().removeAllRanges()
    wrapper?.unmount()
    wrapper = undefined
  })

  it('offers the copy action even when the browser has no clipboard API', async () => {
    await mountCard()

    expect(copyButton().exists(), 'the copy action is always available').toBe(true)
  })

  it('confirms a successful copy', async () => {
    const clipboard = { writeText: vi.fn(() => Promise.resolve()) }
    withClipboard(clipboard)
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    expect(clipboard.writeText, 'the snippet reaches the clipboard').toHaveBeenCalledTimes(1)
    expect(wrapper.text(), 'the user sees that the copy worked').toContain('Copied')
  })

  it('selects the snippet and explains how to copy it when the clipboard refuses', async () => {
    withClipboard({ writeText: vi.fn(() => Promise.reject(new Error('NotAllowedError'))) })
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    expect(window.getSelection().toString(), 'the snippet text is selected for a manual copy').toContain('repository')
    expect(wrapper.text(), 'the user is told to copy by hand').toContain('Ctrl+C')
  })

  it('selects the snippet when there is no clipboard API at all', async () => {
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    expect(window.getSelection().toString(), 'the snippet is selected without a clipboard').toContain('repository')
    expect(wrapper.text(), 'the user is told how to finish the copy').toContain('Ctrl+C')
  })
})

describe('SnippetsCard surface', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('renders the details as an outlined card on the surface', async () => {
    await mountCard()

    expect(wrapper.find('[data-variant]').attributes('data-variant')).toBe('outlined')
  })

  it('sets the snippet text in the md body small role', async () => {
    await mountCard()

    const editor = wrapper.find('.card-editor')
    expect(editor.exists()).toBe(true)
    expect(editor.classes()).toContain('md-body-small')
    expect(editor.classes()).not.toContain('text-ssm')
  })

  it('keeps the copy button out of the scrolling code block', async () => {
    await mountCard()

    expect(wrapper.find('.card-editor button[aria-label="Copy snippet"]').exists(), 'the copy button never covers a line of code').toBe(false)
    expect(copyButton().exists(), 'the copy action is still offered').toBe(true)
  })

  it('draws the snippet as preformatted text that scrolls sideways instead of wrapping', async () => {
    await mountCard()
    const editor = wrapper.find('.card-editor')

    expect(editor.classes(), 'the whitespace of the snippet is kept as written').toContain('whitespace-pre')
    expect(editor.classes(), 'the snippet is cut by scrolling, not by breaking words').toContain('overflow-x-auto')
    expect(editor.classes(), 'the snippet keeps its normal word boundaries').toContain('break-normal')
    expect(editor.classes(), 'no class forces the snippet to wrap').not.toEqual(expect.arrayContaining(['whitespace-pre-wrap', 'break-all', 'break-words']))
  })
})

describe('SnippetsCard snippet text', () => {
  afterEach(() => {
    delete navigator.clipboard
    wrapper?.unmount()
    wrapper = undefined
  })

  it('copies the long url line whole, however the editor wraps it on screen', async () => {
    const clipboard = { writeText: vi.fn(() => Promise.resolve()) }
    withClipboard(clipboard)
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    const [copied] = clipboard.writeText.mock.calls[0]
    expect(copied.split('\n'), 'the url element is one line of the copied text').toContainEqual(expect.stringMatching(/^\s*<url>.*<\/url>$/))
  })

  it('shows the copy status below the snippet, not over its text', async () => {
    withClipboard({ writeText: vi.fn(() => Promise.resolve()) })
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    expect(wrapper.find('.card-editor [role="status"]').exists(), 'no status line sits inside the editor').toBe(false)
    expect(wrapper.find('[role="status"]').text()).toBe('Copied')
  })
})
