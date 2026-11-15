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
import SnippetsCard from './SnippetsCard.vue'
import { DETAILS_COLLAPSED_KEY } from './detailsCollapsed'

// By default the metadata request never answers, so the card stays in its loading state. A test that
// needs the repository level makes the request fail with a 404, as a repository path without artifacts does.
const { content } = vi.hoisted(() => ({ content: vi.fn<(path: string) => Promise<unknown>>(() => new Promise(() => {})) }))
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  return {
    useSession: () => ({
      client: ref({ maven: { content: (...args: [string]) => content(...args) } })
    })
  }
})

let wrapper: VueWrapper | undefined

// listing is the names of the files the browser lists for the path. Left out, the card runs without a listing.
const mountCard = async (path = '', listing?: string[] | null) => {
  wrapper = mount(SnippetsCard, {
    props: { qualifier: { path }, listing },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const copyButton = () =>
  wrapper!.find('button[aria-label="Copy snippet"]')

// Installs a clipboard for one test. The original is put back in afterEach.
const withClipboard = (clipboard: unknown) => {
  Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true })
}

describe('SnippetsCard copy action', () => {
  beforeEach(() => {
    window.getSelection()!.removeAllRanges()
  })

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'clipboard')
    window.getSelection()!.removeAllRanges()
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
    expect(wrapper!.text(), 'the user sees that the copy worked').toContain('Copied')
  })

  it('selects the snippet and explains how to copy it when the clipboard refuses', async () => {
    withClipboard({ writeText: vi.fn(() => Promise.reject(new Error('NotAllowedError'))) })
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    expect(window.getSelection()!.toString(), 'the snippet text is selected for a manual copy').toContain('repository')
    expect(wrapper!.text(), 'the user is told to copy by hand').toContain('Ctrl+C')
  })

  it('selects the snippet when there is no clipboard API at all', async () => {
    await mountCard()

    await copyButton().trigger('click')
    await flushPromises()

    expect(window.getSelection()!.toString(), 'the snippet is selected without a clipboard').toContain('repository')
    expect(wrapper!.text(), 'the user is told how to finish the copy').toContain('Ctrl+C')
  })
})

describe('SnippetsCard surface', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('renders the details as a sidebar on the page surface, without a frame of its own', async () => {
    await mountCard()

    const classes = wrapper!.classes()

    expect(classes, 'no border frames the heading row').not.toContain('border')
    expect(classes, 'no fill frames the heading row').not.toContain('bg-surface')
  })

  it('sets the snippet text in the md body small role', async () => {
    await mountCard()

    const editor = wrapper!.find('.card-editor')
    expect(editor.exists()).toBe(true)
    expect(editor.classes()).toContain('md-body-small')
    expect(editor.classes()).not.toContain('text-ssm')
  })

  it('keeps the copy button out of the scrolling code block', async () => {
    await mountCard()

    expect(wrapper!.find('.card-editor button[aria-label="Copy snippet"]').exists(), 'the copy button never covers a line of code').toBe(false)
    expect(copyButton().exists(), 'the copy action is still offered').toBe(true)
  })

  it('draws the snippet as preformatted text that scrolls sideways instead of wrapping', async () => {
    await mountCard()
    const editor = wrapper!.find('.card-editor')

    expect(editor.classes(), 'the whitespace of the snippet is kept as written').toContain('whitespace-pre')
    expect(editor.classes(), 'the snippet is cut by scrolling, not by breaking words').toContain('overflow-x-auto')
    expect(editor.classes(), 'the snippet keeps its normal word boundaries').toContain('break-normal')
    expect(editor.classes(), 'no class forces the snippet to wrap').not.toEqual(expect.arrayContaining(['whitespace-pre-wrap', 'break-all', 'break-words']))
  })
})

describe('SnippetsCard collapsible details', () => {
  const header = () => wrapper!.find('h1 button')
  const body = () => wrapper!.find('#card-menu')

  // A narrow or wide screen is simulated by the media query, which jsdom does not provide.
  const withScreen = (wide: boolean) => {
    window.matchMedia = vi.fn(() => ({ matches: wide }) as MediaQueryList)
  }

  beforeEach(() => {
    localStorage.clear()
    withScreen(true)
  })

  afterEach(() => {
    Reflect.deleteProperty(window, 'matchMedia')
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
    expect(wrapper!.find(`[id="${controlled}"]`).exists(), 'the controlled body exists').toBe(true)
  })

  it('hides the tabs and the snippet when the header is pressed', async () => {
    await mountCard()

    await header().trigger('click')

    expect(header().attributes('aria-expanded'), 'the header reports the card as closed').toBe('false')
    expect(body().isVisible(), 'the tabs are hidden with the body').toBe(false)
    expect(wrapper!.find('button[aria-label="Copy snippet"]').isVisible(), 'the copy action is hidden with the body').toBe(false)
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
    wrapper!.unmount()
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

    expect(wrapper!.emitted('update:collapsed'), 'the page follows each change').toEqual([[true], [false]])
  })

  it('still toggles when the browser refuses to read or write the collapsed state', async () => {
    // Only this card's key is refused: the tab memory of the card menu is a separate store.
    const refuse = (key: string) => {
      if (key === DETAILS_COLLAPSED_KEY) throw new DOMException('blocked', 'SecurityError')
    }
    const realGet = Storage.prototype.getItem
    const realSet = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) { refuse(key); return realGet.call(this, key) })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key: string, value: string) { refuse(key); return realSet.call(this, key, value) })

    await mountCard()
    await header().trigger('click')

    expect(header().attributes('aria-expanded'), 'the toggle works without storage').toBe('false')
  })
})

describe('SnippetsCard heading', () => {
  // The visible title is the short part; the full name of the card is the accessible name of its toggle.
  const heading = () => wrapper!.find('h1 button').attributes('aria-label')

  beforeEach(() => {
    content.mockImplementation(() => Promise.reject(Object.assign(new Error('Not Found'), { response: { status: 404 } })))
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

  it('shows the repository under the short heading, so the name is seen as well as announced', async () => {
    await mountCard('releases')

    expect(wrapper!.find('[data-test="details-subtitle"]').text(), 'the repository is written under the heading').toBe('releases')
    expect(wrapper!.find('h1 button').text(), 'the visible heading stays short').toBe('Repository details')
  })

  it('names only the first segment of a deeper repository path', async () => {
    await mountCard('releases/com/example')

    expect(heading(), 'the repository is the first segment of the path').toContain('Repository details: releases')
    expect(heading()).not.toContain('example')
  })

  it('says what to replace on the root page, outside the snippet text', async () => {
    await mountCard('')

    expect(wrapper!.text(), 'the root page tells the visitor to replace the placeholder').toContain('Replace <repository> with the id of a repository from the list')
    expect(wrapper!.find('.card-editor').text(), 'the caption is not part of the copied snippet').not.toContain('Replace')
  })

  it('does not say what to replace on a repository path', async () => {
    await mountCard('releases')

    expect(wrapper!.text()).not.toContain('Replace <repository>')
  })
})

describe('SnippetsCard copy status region', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'clipboard')
    wrapper?.unmount()
    wrapper = undefined
  })

  it('is present and empty before anything is copied, so the first message is announced', async () => {
    await mountCard()

    const status = wrapper!.find('[role="status"]')
    expect(status.exists(), 'the status region exists before the first copy').toBe(true)
    expect(status.text()).toBe('')
  })

  it('is the same element after a copy, so the message changes inside the region', async () => {
    withClipboard({ writeText: vi.fn(() => Promise.resolve()) })
    await mountCard()
    const before = wrapper!.find('[role="status"]').element

    await copyButton().trigger('click')
    await flushPromises()

    expect(wrapper!.find('[role="status"]').element, 'the region is not replaced by the message').toBe(before)
    expect(before.textContent).toBe('Copied')
  })
})

describe('SnippetsCard snippet text', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'clipboard')
    wrapper?.unmount()
    wrapper = undefined
  })

  it('copies the long url line whole, however the editor wraps it on screen', async () => {
    const clipboard = { writeText: vi.fn<(text: string) => Promise<void>>(() => Promise.resolve()) }
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

    expect(wrapper!.find('.card-editor [role="status"]').exists(), 'no status line sits inside the editor').toBe(false)
    expect(wrapper!.find('[role="status"]').text()).toBe('Copied')
  })
})

describe('SnippetsCard snippet box height', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('keeps the minimum height on the code block while the skeleton is shown', async () => {
    await mountCard('releases/com/example')

    expect(wrapper!.find('.card-editor .skeleton-bars').exists(), 'the loading skeleton is shown').toBe(true)
    expect(wrapper!.find('.card-editor').classes(), 'the skeleton keeps its four bars inside the box').toContain('min-h-29')
  })

  it('lets the code block hug the loaded snippet instead of keeping the skeleton height', async () => {
    await mountCard('')

    expect(wrapper!.find('.card-editor .skeleton-bars').exists(), 'the snippet has loaded').toBe(false)
    expect(wrapper!.find('.card-editor').classes(), 'a one-line snippet does not get an empty four-line box').not.toContain('min-h-29')
  })
})

describe('SnippetsCard metadata probes', () => {
  const GROUP = 'releases/eu/cloudnetservice'
  const ARTIFACT = `${GROUP}/cloudnet`
  const VERSION = `${ARTIFACT}/3.0.0`
  const SNAPSHOT = 'onelitefeather-proxy/eu/cloudnetservice/cloudnet/wrapper-jvm-api/4.0.0-RC16-SNAPSHOT'

  // The artifact level lists its versions.
  const ARTIFACT_LEVEL = `<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>eu.cloudnetservice.cloudnet</groupId>
  <artifactId>cloudnet</artifactId>
  <versioning>
    <versions>
      <version>2.9.0</version>
      <version>3.0.0</version>
    </versions>
  </versioning>
</metadata>`

  // The version level of a snapshot names its one version and has no list of versions.
  const SNAPSHOT_VERSION_LEVEL = `<?xml version="1.0" encoding="UTF-8"?>
<metadata modelVersion="1.1.0">
  <groupId>eu.cloudnetservice.cloudnet</groupId>
  <artifactId>wrapper-jvm-api</artifactId>
  <version>4.0.0-RC16-SNAPSHOT</version>
  <versioning>
    <snapshot>
      <localCopy>true</localCopy>
    </snapshot>
    <lastUpdated>20260101000000</lastUpdated>
  </versioning>
</metadata>`

  const answer = (xml: string) => Promise.resolve({ data: xml })
  const refuse = (status: number) => Promise.reject(Object.assign(new Error(`HTTP ${status}`), { response: { status } }))
  const requestedPaths = () => content.mock.calls.map(([path]) => path)
  const heading = () => wrapper!.find('h1 button').text()

  beforeEach(() => {
    content.mockClear()
    content.mockImplementation(() => new Promise(() => {}))
  })

  afterEach(() => {
    vi.restoreAllMocks()
    content.mockImplementation(() => new Promise(() => {}))
    wrapper?.unmount()
    wrapper = undefined
  })

  it('sends no metadata request for a group directory whose listing has no metadata and no version files', async () => {
    await mountCard(GROUP, ['cloudnet', 'readme.txt'])

    expect(requestedPaths(), 'a plain directory is not probed').toEqual([])
    expect(heading(), 'the repository snippet is shown straight away').toBe('Repository details')
    expect(wrapper!.find('.card-editor .skeleton-bars').exists(), 'the card does not wait for a request').toBe(false)
  })

  it('requests the directory metadata exactly once when the listing contains maven-metadata.xml', async () => {
    content.mockImplementation(() => answer(ARTIFACT_LEVEL))

    await mountCard(ARTIFACT, ['maven-metadata.xml', 'cloudnet-3.0.0.jar'])

    expect(requestedPaths(), 'only the directory own metadata is asked for').toEqual([`${ARTIFACT}/maven-metadata.xml`])
    expect(heading(), 'the directory is an artifact').toBe('Artifact details')
  })

  it('requests the parent metadata once for a version directory listing', async () => {
    content.mockImplementation((path: string) => path === `${ARTIFACT}/maven-metadata.xml` ? answer(ARTIFACT_LEVEL) : refuse(404))

    await mountCard(VERSION, ['cloudnet-3.0.0.pom', 'cloudnet-3.0.0.jar'])

    expect(requestedPaths(), 'the version directory itself is not probed, only its parent').toEqual([`${ARTIFACT}/maven-metadata.xml`])
    expect(heading()).toBe('Artifact details')
    expect(wrapper!.text(), 'the snippet names the version of the directory').toContain('3.0.0')
  })

  it('treats a .module file as the marker of a version directory', async () => {
    content.mockImplementation(() => answer(ARTIFACT_LEVEL))

    await mountCard(VERSION, ['cloudnet-3.0.0.module'])

    expect(requestedPaths()).toEqual([`${ARTIFACT}/maven-metadata.xml`])
  })

  it('shows the artifact snippet for a snapshot version directory whose metadata is version-level', async () => {
    content.mockImplementation(() => answer(SNAPSHOT_VERSION_LEVEL))

    await mountCard(SNAPSHOT, [
      'maven-metadata.xml',
      'wrapper-jvm-api-4.0.0-RC16-SNAPSHOT.pom',
      'wrapper-jvm-api-4.0.0-RC16-SNAPSHOT.module',
      'wrapper-jvm-api-4.0.0-RC16-SNAPSHOT.jar',
      'wrapper-jvm-api-4.0.0-RC16-SNAPSHOT-sources.jar',
      'wrapper-jvm-api-4.0.0-RC16-SNAPSHOT-javadoc.jar'
    ])

    expect(requestedPaths(), 'the own version-level metadata answers, so no parent is asked').toEqual([`${SNAPSHOT}/maven-metadata.xml`])
    expect(heading(), 'the snapshot directory is shown as an artifact').toBe('Artifact details')
    expect(wrapper!.text(), 'the group of the snapshot is in the snippet').toContain('eu.cloudnetservice.cloudnet')
    expect(wrapper!.text(), 'the artifact of the snapshot is in the snippet').toContain('wrapper-jvm-api')
    expect(wrapper!.text(), 'the version of the snapshot is in the snippet').toContain('4.0.0-RC16-SNAPSHOT')
  })

  it('sends no request while the listing is still loading', async () => {
    await mountCard(VERSION, null)

    expect(requestedPaths(), 'nothing is probed before the listing is known').toEqual([])
    expect(wrapper!.find('.card-editor .skeleton-bars').exists(), 'the card shows its loading state').toBe(true)
  })

  it('shows the repository snippet for a version directory whose parent has no metadata, logging nothing on 406', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    content.mockImplementation(() => refuse(406))

    await mountCard(VERSION, ['cloudnet-3.0.0.pom'])

    expect(heading(), 'the card falls back to the repository snippet').toBe('Repository details')
    expect(log, 'a 406 is an expected not-there and is not logged').not.toHaveBeenCalled()
  })

  it('logs nothing when the probes without a listing answer 406', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    content.mockImplementation(() => refuse(406))

    await mountCard(VERSION)

    expect(log, 'the probe without a listing does not log a 406 either').not.toHaveBeenCalled()
  })

  it('still logs an unexpected error from the parent probe without a listing', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    content.mockImplementation(() => refuse(500))

    await mountCard(VERSION)

    expect(log, 'a server error stays visible in the console').toHaveBeenCalledTimes(1)
  })

  it('probes its own metadata first, then the parent, when no listing is given', async () => {
    content.mockImplementation(() => refuse(404))

    await mountCard(VERSION)

    expect(requestedPaths(), 'without a listing the card keeps asking both levels').toEqual([
      `${VERSION}/maven-metadata.xml`,
      `${ARTIFACT}/maven-metadata.xml`
    ])
  })
})
