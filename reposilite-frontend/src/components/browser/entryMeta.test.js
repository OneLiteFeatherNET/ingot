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

import { describe, it, expect } from 'vitest'
import prettyBytes from 'pretty-bytes'
import { describeEntry } from './entryMeta'

// A fixed instant, so the expectation does not depend on when the suite runs.
const STORED_AT = Date.UTC(2026, 9, 15, 12, 0, 0)

// The API sends java.time.Instant as Unix epoch seconds, with a fraction for milliseconds.
const asEpochSeconds = (millis) => millis / 1000 + 0.123

const dateLabel = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })
  .format(new Date(STORED_AT))

describe('describeEntry modification date', () => {
  it('shows the date of a file the API sends as epoch seconds', () => {
    const file = {
      name: 'mycelium-bom-1.8.9.pom',
      type: 'FILE',
      contentLength: 2420,
      lastModifiedTime: asEpochSeconds(STORED_AT)
    }

    expect(describeEntry(file), 'the size, then the date the file was stored')
      .toBe(`${prettyBytes(2420)} · ${dateLabel}`)
  })

  it('leaves the date out when the API sends no modification time', () => {
    const file = { name: 'legacy.pom', type: 'FILE', contentLength: 2420 }

    expect(describeEntry(file), 'only the size is known').toBe(prettyBytes(2420))
  })
})
