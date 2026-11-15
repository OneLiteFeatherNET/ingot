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

// Schema descriptions may contain literal <br> tags as line breaks. They are split here and
// rendered as text, so no HTML from the schema is ever inserted into the page.
const LINE_BREAK = /<br\s*\/?>/gi

/** Splits a schema description on <br>, <br/> and <br /> into trimmed, non-empty lines. */
export const splitDescription = (text) => {
  if (!text) return []
  return String(text)
    .split(LINE_BREAK)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

/** The lines joined with newlines, for an element styled with white-space: pre-line. */
export const descriptionText = (text) => splitDescription(text).join('\n')
