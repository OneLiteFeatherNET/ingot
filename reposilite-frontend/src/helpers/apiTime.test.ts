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
import { instantToDate, localDateToDate } from './apiTime'

// The shapes below are what ApiTimeWireFormatTest pins on the backend.
const NOON_UTC_MILLIS = Date.UTC(2026, 9, 14, 12)

describe('instantToDate', () => {
  it('reads a number of epoch seconds, with its fraction, as the instant it names', () => {
    expect(instantToDate(NOON_UTC_MILLIS / 1000 + 0.123).getTime(), 'the fraction is milliseconds').toBe(NOON_UTC_MILLIS + 123)
  })

  it('reads a whole number of epoch seconds as that second', () => {
    expect(instantToDate(NOON_UTC_MILLIS / 1000).toISOString()).toBe('2026-10-14T12:00:00.000Z')
  })

  it('returns null when the API sent no instant', () => {
    expect(instantToDate(null), 'null').toBeNull()
    expect(instantToDate(undefined), 'undefined').toBeNull()
  })

  it('treats epoch zero as no instant', () => {
    expect(instantToDate(0), 'zero is unknown, not 1970').toBeNull()
  })

  it('does not read an ISO string as seconds', () => {
    expect(instantToDate('2026-10-15T12:00:00Z'), 'the API never sends an ISO instant here').toBeNull()
  })
})

describe('localDateToDate', () => {
  it('reads a year, month and day array as midnight UTC of that day', () => {
    expect(localDateToDate([2026, 10, 4]).toISOString()).toBe('2026-10-04T00:00:00.000Z')
  })

  it('returns null for anything but a year, month and day array', () => {
    expect(localDateToDate('2026-10-04'), 'an ISO day string is not what the API sends').toBeNull()
    expect(localDateToDate(null), 'null').toBeNull()
    expect(localDateToDate([2026, 10]), 'a partial array').toBeNull()
  })
})
