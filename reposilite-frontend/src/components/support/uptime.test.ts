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
import { formatUptime } from './uptime'

const SECOND = 1000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

describe('formatUptime', () => {
  it.each([
    ['11 min 40 s', 11 * MINUTE + 40 * SECOND],
    ['3 h 12 min', 3 * HOUR + 12 * MINUTE],
    ['2 d 4 h', 2 * DAY + 4 * HOUR],
  ])('shows %s for the milliseconds of the same length', (text, millis) => {
    expect(formatUptime(millis)).toBe(text)
  })

  it('shows seconds below a minute', () => {
    expect(formatUptime(45 * SECOND)).toBe('45 s')
  })

  it('drops the unit that is zero at the end, so a whole hour reads 3 h', () => {
    expect(formatUptime(3 * HOUR)).toBe('3 h')
  })

  it('drops the seconds of a whole number of minutes', () => {
    expect(formatUptime(11 * MINUTE)).toBe('11 min')
  })

  it('drops the hours of a whole number of days', () => {
    expect(formatUptime(2 * DAY)).toBe('2 d')
  })

  it('shows at most two adjacent units, so a long uptime stays short', () => {
    expect(formatUptime(2 * DAY + 4 * HOUR + 30 * MINUTE)).toBe('2 d 4 h')
  })

  it('shows zero seconds for an uptime below one second, and for a value that is not a time', () => {
    expect(formatUptime(0)).toBe('0 s')
    expect(formatUptime(999)).toBe('0 s')
    expect(formatUptime(-5000)).toBe('0 s')
    expect(formatUptime(Number.NaN)).toBe('0 s')
  })
})
