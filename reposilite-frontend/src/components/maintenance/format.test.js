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
import { formatBytes, formatDateTime, countLabel } from './format'

describe('countLabel', () => {
  it('uses the singular for exactly one', () => {
    expect(countLabel(1, 'artifact')).toBe('1 artifact')
  })

  it('uses the plural for zero and for more', () => {
    expect(countLabel(0, 'artifact')).toBe('0 artifacts')
    expect(countLabel(2, 'artifact')).toBe('2 artifacts')
  })

  it('takes an irregular plural when one is given', () => {
    expect(countLabel(2, 'repository', 'repositories')).toBe('2 repositories')
  })
})

describe('formatBytes', () => {
  it('formats byte counts the way the file browser does', () => {
    expect(formatBytes(1536)).toBe(prettyBytes(1536))
  })

  it('shows zero as zero bytes', () => {
    expect(formatBytes(0)).toBe('0 B')
  })

  it('treats a missing count as zero bytes', () => {
    expect(formatBytes(null)).toBe('0 B')
  })
})

describe('formatDateTime', () => {
  it('formats an ISO instant with the browser locale', () => {
    const iso = '2026-10-15T10:00:00Z'

    expect(formatDateTime(iso)).toBe(new Date(iso).toLocaleString())
  })

  it('shows the fallback when no instant is recorded', () => {
    expect(formatDateTime(null)).toBe('Not recorded')
  })

  it('shows the fallback for a value that is not a date', () => {
    expect(formatDateTime('yesterday-ish', 'Unknown')).toBe('Unknown')
  })
})
