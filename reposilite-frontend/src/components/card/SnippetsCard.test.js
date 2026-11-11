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
import { DETAILS_COLLAPSED_KEY } from './detailsCollapsed'

// By default the metadata request never answers, so the card stays in its loading state. A test that
// needs the repository level makes the request fail with a 404, as a repository path without artifacts does.
const { content } = vi.hoisted(() => ({ content: vi.fn(() => new Promise(() => {})) }))
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  return {
    useSession: () => ({
      client: ref({ maven: { content: (...args) => content(...args) } })
    })
  }
})

let wrapper

const mountCard = async (path = '') => {
  wrapper = mount(SnippetsCard, {
    props: { qualifier: { path } },
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

describe('SnippetsCard collapsible details', () => {
  const header = () => wrapper.find('h1 button')
  const body = () => wrapper.find('#card-menu')

  // A narrow or wide screen is simulated by the media query, which jsdom does not provide.
  const withScreen = (wide) => {
    window.matchMedia = vi.fn(() => ({ matches: wide }))
  }

  beforeEach(() => {
    localStorage.clear()
    withScreen(true)
  })

  afterEach(() => {
    delete window.matchMedia
    vi.restoreAllMocks()
    localStorage.clear()
    wrapper?.unmount()
    wrapper = undefined
  })

  it('offers the title as a native button that says whether the details are open', async () => {
    await mountCard()

    expect(header().exists(), 'the header is a button').toBe(true)
    expect(header().element.tagName, 'a native button answers the keyboard by itself').toBe('BUTTON')
    expect(header().attributes('type')).toBe('button')
    expect(header().text(), 'the root page names what to do with the card').toContain('Use a repository')
    expect(header().attributes('aria-expanded'), 'the details are open on a wide screen').toBe('true')
  })

  it('names the body the header controls', async () => {
    await mountCard()

    const controlled = header().attributes('aria-controls')
    expect(controlled, 'the header points at the body it toggles').toBeTruthy()
    expect(wrapper.find(`[id="${controlled}"]`).exists(), 'the controlled body exists').toBe(true)
  })

  it('hides the tabs and the snippet when the header is pressed', async () => {
    await mountCard()

    await header().trigger('click')

    expect(header().attributes('aria-expanded'), 'the header reports the card as closed').toBe('false')
    expect(body().isVisible(), 'the tabs are hidden with the body').toBe(false)
    expect(wrapper.find('button[aria-label="Copy snippet"]').isVisible(), 'the copy action is hidden with the body').toBe(false)
  })

  it('keeps the title visible while the card is collapsed', async () => {
    await mountCard()

    await header().trigger('click')

    expect(header().text(), 'a collapsed card still shows what it is').toContain('Use a repository')
  })

  it('opens the body again on a second press', async () => {
    await mountCard()

    await header().trigger('click')
    await header().trigger('click')

    expect(header().attributes('aria-expanded')).toBe('true')
    expect(body().isVisible()).toBe(true)
  })

  it('remembers a collapsed card for the next visit of this viewer', async () => {
    await mountCard()
    await header().trigger('click')
    wrapper.unmount()
    wrapper = undefined

    await mountCard()

    expect(header().attributes('aria-expanded'), 'the card opens folded again').toBe('false')
  })

  it('folds the card by default on a narrow screen', async () => {
    withScreen(false)
    await mountCard()

    expect(header().attributes('aria-expanded')).toBe('false')
  })

  it('tells the page when the card is folded or opened', async () => {
    await mountCard()

    await header().trigger('click')
    await header().trigger('click')

    expect(wrapper.emitted('update:collapsed'), 'the page follows each change').toEqual([[true], [false]])
  })

  it('still toggles when the browser refuses to read or write the collapsed state', async () => {
    // Only this card's key is refused: the tab memory of the card menu is a separate store.
    const refuse = (key) => {
      if (key === DETAILS_COLLAPSED_KEY) throw new DOMException('blocked', 'SecurityError')
    }
    const realGet = Storage.prototype.getItem
    const realSet = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (key) { refuse(key); return realGet.call(this, key) })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (key, value) { refuse(key); return realSet.call(this, key, value) })

    await mountCard()
    await header().trigger('click')

    expect(header().attributes('aria-expanded'), 'the toggle works without storage').toBe('false')
  })
})

describe('SnippetsCard heading', () => {
  const heading = () => wrapper.find('h1 button').text()

  beforeEach(() => {
    content.mockImplementation(() => Promise.reject({ response: { status: 404 } }))
  })

  afterEach(() => {
    content.mockImplementation(() => new Promise(() => {}))
    wrapper?.unmount()
    wrapper = undefined
  })

  it('names the repository the snippet is for on a repository path', async () => {
    await mountCard('releases')

    expect(heading(), 'the heading says which repository the snippet describes').toContain('Repository details: releases')
  })

  it('names only the first segment of a deeper repository path', async () => {
    await mountCard('releases/com/example')

    expect(heading(), 'the repository is the first segment of the path').toContain('Repository details: releases')
    expect(heading()).not.toContain('example')
  })

  it('says what to replace on the root page, outside the snippet text', async () => {
    await mountCard('')

    expect(wrapper.text(), 'the root page tells the visitor to replace the placeholder').toContain('Replace <repository> with the id of a repository from the list')
    expect(wrapper.find('.card-editor').text(), 'the caption is not part of the copied snippet').not.toContain('Replace')
  })

  it('does not say what to replace on a repository path', async () => {
    await mountCard('releases')

    expect(wrapper.text()).not.toContain('Replace <repository>')
  })
})

describe('SnippetsCard copy status region', () => {
  afterEach(() => {
    delete navigator.clipboard
    wrapper?.unmount()
    wrapper = undefined
  })

  it('is present and empty before anything is copied, so the first message is announced', async () => {
    await mountCard()

    const status = wrapper.find('[role="status"]')
    expect(status.exists(), 'the status region exists before the first copy').toBe(true)
    expect(status.text()).toBe('')
  })

  it('is the same element after a copy, so the message changes inside the region', async () => {
    withClipboard({ writeText: vi.fn(() => Promise.resolve()) })
    await mountCard()
    const before = wrapper.find('[role="status"]').element

    await copyButton().trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="status"]').element, 'the region is not replaced by the message').toBe(before)
    expect(before.textContent).toBe('Copied')
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
