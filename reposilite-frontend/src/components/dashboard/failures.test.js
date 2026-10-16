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
import { absoluteTime, relativeTime } from './failures'

// The reference instant is passed in, so no test reads the system clock.
const NOW = Date.parse('2026-10-15T12:00:00Z')

describe('relativeTime', () => {
  it('reads a failure from a few seconds ago as just now', () => {
    expect(relativeTime('2026-10-15T11:59:58Z', NOW)).toBe('just now')
  })

  it('counts a failure from under a minute ago in seconds', () => {
    expect(relativeTime('2026-10-15T11:59:30Z', NOW)).toBe('30 seconds ago')
  })

  it('counts a failure from under an hour ago in minutes', () => {
    expect(relativeTime('2026-10-15T11:55:00Z', NOW)).toBe('5 minutes ago')
  })

  it('counts a failure from under a day ago in hours', () => {
    expect(relativeTime('2026-10-15T10:00:00Z', NOW)).toBe('2 hours ago')
  })

  it('counts an older failure in days', () => {
    expect(relativeTime('2026-10-12T12:00:00Z', NOW)).toBe('3 days ago')
  })
})

describe('absoluteTime', () => {
  it('writes the instant in UTC to the second, without the fraction', () => {
    expect(absoluteTime('2026-10-15T11:59:30.123456Z')).toBe('2026-10-15 11:59:30 UTC')
  })
})
