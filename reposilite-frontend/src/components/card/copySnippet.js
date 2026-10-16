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
 * Writes the text to the clipboard and reports whether it did. A missing clipboard API
 * (plain http, an old browser, a locked-down embed) and a refused write both count as a
 * failure, so the caller can fall back instead of throwing. The clipboard is passed in so
 * the function has no hidden dependency on the global navigator.
 */
const copyToClipboard = async (text, clipboard) => {
  if (typeof clipboard?.writeText !== 'function') {
    return false
  }

  try {
    await clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

/*
 * Selects the whole text of an element, so that the user can finish the copy with the
 * keyboard when the clipboard is not available. Returns false when there is nothing to select.
 */
const selectText = (element) => {
  const doc = element?.ownerDocument
  const selection = doc?.getSelection?.()
  if (!selection) {
    return false
  }

  const range = doc.createRange()
  range.selectNodeContents(element)
  selection.removeAllRanges()
  selection.addRange(range)
  return true
}

export {
  copyToClipboard,
  selectText
}
