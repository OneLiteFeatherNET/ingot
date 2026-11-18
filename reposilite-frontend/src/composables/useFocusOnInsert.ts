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

/*
 * Focus for a new top-level page. The router asks for it when a page changes, but the page is not in the document yet:
 * a page leaves before the next one enters (out-in), so the new heading only exists after the old page has gone. The
 * focus therefore waits for the insertion of the new page into main, which is the start of its enter, and never for an
 * after-enter hook. A removal alone does not take the request.
 */

// The request is module state on purpose: the router and the page live in one app, and one page change is asked once.
let focusRequested = false

// A page change asks for the focus; the request is taken by the next insertion into main.
export const requestPageFocus = (): void => {
  focusRequested = true
}

export const pageFocusRequested = (): boolean => focusRequested

export const clearPageFocusRequest = (): void => {
  focusRequested = false
}

// The main element that holds the page, when the root is main or a part of it.
const mainOf = (root: Element): Element | null => (root.id === 'main' ? root : root.closest('#main'))

// The heading of the page when it has one, main otherwise, and null when neither exists.
export const focusTargetOf = (root: Element): HTMLElement | null => {
  const heading = root.querySelector<HTMLElement>('h1')
  if (heading) return heading
  return mainOf(root) as HTMLElement | null
}

const focusElement = (element: HTMLElement): void => {
  // A heading is not focusable unless it has a tabindex, so the page gets one when it has none.
  if (!element.hasAttribute('tabindex')) element.setAttribute('tabindex', '-1')
  element.focus()
}

/*
 * Observes the children of the element that holds the page. The first insertion of an element after a request moves
 * the focus to the target of the page, and uses the request up. Returns the function that stops the observer.
 */
export const useFocusOnInsert = (target: () => HTMLElement | null): (() => void) => {
  const observed = target()
  if (!observed || typeof MutationObserver === 'undefined') return () => {}

  const observer = new MutationObserver((records) => {
    if (!focusRequested) return
    const inserted = records.some(record => Array.from(record.addedNodes).some(node => node.nodeType === Node.ELEMENT_NODE))
    if (!inserted) return
    focusRequested = false
    const element = focusTargetOf(observed)
    if (element) focusElement(element)
  })
  observer.observe(observed, { childList: true })
  return () => observer.disconnect()
}

/*
 * Called once a page change has rendered. A page inside the frame that stays (one Administration page to the next)
 * replaces only part of main, so no element is inserted into main and the observer above never takes the request.
 * When nothing in main is leaving, the new page is already drawn and takes the focus now. While the old page is still
 * leaving, the request waits for the insertion of the new page.
 */
export const settlePageFocus = (main: HTMLElement | null): void => {
  if (!focusRequested || !main) return
  if (main.querySelector(':scope > [class*="-leave-"]')) return
  focusRequested = false
  const element = focusTargetOf(main)
  if (element) focusElement(element)
}
