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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import {
  clearPageFocusRequest,
  focusTargetOf,
  pageFocusRequested,
  requestPageFocus,
  useFocusOnInsert
} from './useFocusOnInsert'

// Each test builds its own page inside its own main element, and removes it after the test.
let main!: HTMLElement

beforeEach(() => {
  clearPageFocusRequest()
  main = document.createElement('main')
  main.id = 'main'
  main.tabIndex = -1
  document.body.appendChild(main)
})

afterEach(() => {
  clearPageFocusRequest()
  main.remove()
  document.body.blur()
})

const headingIn = (page: HTMLElement, text = 'Search'): HTMLHeadingElement => {
  const heading = document.createElement('h1')
  heading.textContent = text
  page.appendChild(heading)
  return heading
}

describe('focusTargetOf', () => {
  it('takes the first h1 of the page when it has one', () => {
    const page = document.createElement('section')
    const first = headingIn(page, 'Search')
    headingIn(page, 'Second')

    expect(focusTargetOf(page), 'the first heading is the target').toBe(first)
  })

  it('takes main when the page has no heading', () => {
    const page = document.createElement('section')
    main.appendChild(page)

    expect(focusTargetOf(main), 'a page without a heading is announced by main').toBe(main)
  })

  it('takes null when the page has neither a heading nor main', () => {
    const page = document.createElement('section')

    expect(focusTargetOf(page), 'nothing to focus').toBeNull()
  })
})

describe('useFocusOnInsert', () => {
  it('moves focus to the heading of the page that is inserted after a request', async () => {
    const stop = useFocusOnInsert(() => main)
    requestPageFocus()
    const page = document.createElement('section')
    const heading = headingIn(page)

    main.appendChild(page)
    await nextTick()

    expect(document.activeElement, 'the new page takes the focus at its insertion').toBe(heading)
    expect(pageFocusRequested(), 'the request is used up by the insertion').toBe(false)
    stop()
  })

  it('moves focus to main when the inserted page has no heading', async () => {
    const stop = useFocusOnInsert(() => main)
    requestPageFocus()

    main.appendChild(document.createElement('section'))
    await nextTick()

    expect(document.activeElement, 'a page without a heading takes main').toBe(main)
    stop()
  })

  it('keeps the request while the old page is only removed', async () => {
    const oldPage = document.createElement('section')
    main.appendChild(oldPage)
    const stop = useFocusOnInsert(() => main)
    requestPageFocus()

    oldPage.remove()
    await nextTick()

    expect(pageFocusRequested(), 'a removal is not the insertion of the new page').toBe(true)
    expect(document.activeElement, 'nothing is focused before the insertion').not.toBe(main)
    stop()
  })

  it('moves no focus when no request was made', async () => {
    const stop = useFocusOnInsert(() => main)
    const page = document.createElement('section')
    headingIn(page)

    main.appendChild(page)
    await nextTick()

    expect(document.activeElement, 'a first page load keeps the focus where it is').not.toBe(main)
    stop()
  })

  it('stops observing once the returned function is called', async () => {
    const stop = useFocusOnInsert(() => main)
    stop()
    requestPageFocus()

    main.appendChild(document.createElement('section'))
    await nextTick()

    expect(document.activeElement, 'a stopped observer does not take the request').not.toBe(main)
  })
})
