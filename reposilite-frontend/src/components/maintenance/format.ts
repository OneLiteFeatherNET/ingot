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

import prettyBytes from 'pretty-bytes'

// The same formatting as the file browser, so a size reads the same on every page.
export const formatBytes = (bytes) => prettyBytes(Math.max(0, Number(bytes) || 0))

// "1 artifact", "2 artifacts". The plural defaults to the singular with an s.
export const countLabel = (count, singular, plural = `${singular}s`) =>
  `${count} ${count === 1 ? singular : plural}`

/*
 * An ISO-8601 instant in the browser's locale and time zone. A missing or unreadable value
 * gets the fallback, so a half-written record never shows "Invalid Date".
 */
export const formatDateTime = (value, fallback = 'Not recorded') => {
  if (!value) return fallback
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? fallback : date.toLocaleString()
}
