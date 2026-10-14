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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { copyToClipboard, selectText } from './copySnippet'

describe('copyToClipboard', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reports success and hands the text to the clipboard', async () => {
    const clipboard = { writeText: vi.fn(() => Promise.resolve()) }

    const copied = await copyToClipboard('implementation "a:b:1"', clipboard)

    expect(copied, 'a clipboard that accepts the text reports success').toBe(true)
    expect(clipboard.writeText, 'the snippet is written to the clipboard').toHaveBeenCalledWith('implementation "a:b:1"')
  })

  it('reports failure when there is no clipboard API', async () => {
    const copied = await copyToClipboard('text', undefined)

    expect(copied, 'without the clipboard API the copy cannot succeed').toBe(false)
  })

  it('reports failure when the clipboard refuses the text', async () => {
    const clipboard = { writeText: vi.fn(() => Promise.reject(new Error('NotAllowedError'))) }

    const copied = await copyToClipboard('text', clipboard)

    expect(copied, 'a refused write is a failed copy, not an exception').toBe(false)
  })
})

describe('selectText', () => {
  it('selects the whole text of the element so the user can copy it by hand', () => {
    const element = document.createElement('pre')
    element.textContent = 'maven { url "https://repo.example" }'
    document.body.appendChild(element)

    const selected = selectText(element)

    expect(selected, 'a selectable element is selected').toBe(true)
    expect(window.getSelection().toString(), 'the selection covers the snippet').toBe(element.textContent)

    window.getSelection().removeAllRanges()
    element.remove()
  })

  it('does nothing and reports failure without an element', () => {
    expect(selectText(null), 'no element means nothing can be selected').toBe(false)
  })
})
