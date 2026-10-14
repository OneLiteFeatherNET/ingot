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

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

/*
 * The supporting line of a file row: its size and when it was last modified. Directories
 * carry neither, so their line is empty. Unknown values are left out rather than shown as
 * zero bytes or the epoch.
 */
const describeEntry = (file) => {
  if (file?.type === 'DIRECTORY') {
    return ''
  }

  const parts = []

  if (typeof file?.contentLength === 'number' && file.contentLength >= 0) {
    parts.push(prettyBytes(file.contentLength))
  }

  if (file?.lastModifiedTime) {
    const modified = new Date(file.lastModifiedTime)
    if (!Number.isNaN(modified.getTime())) {
      parts.push(dateFormat.format(modified))
    }
  }

  return parts.join(' · ')
}

export {
  describeEntry
}
