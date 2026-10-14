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
