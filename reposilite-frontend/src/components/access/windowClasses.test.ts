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

import { describe, expect, it } from 'vitest'

const sources = import.meta.glob<string>(['./*.vue', './nodes/*.vue', './check/*.vue'], { query: '?raw', import: 'default', eager: true })

// The default Tailwind breakpoints (640, 768, 1024, 1280 and 1536 pixels) are not used by the dashboard, which follows
// the MD3 window classes medium:, expanded:, large: and xlarge: instead.
const defaultBreakpoint = /(?:^|[\s"'`:!([-])(?:sm|md|lg|xl|2xl):[\w[]/

describe('access window classes', () => {
  it('finds the single file components of the folder', () => {
    expect(Object.keys(sources).length, 'single file components found').toBeGreaterThan(0)
  })

  it.each(Object.entries(sources))('%s uses no default Tailwind breakpoint prefix', (path, source) => {
    const offending = source.split('\n').filter((line) => defaultBreakpoint.test(line))
    expect(offending, `${path} lines with a default breakpoint prefix`).toEqual([])
  })
})
