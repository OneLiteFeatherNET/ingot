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
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

const SOURCE_ROOT = join(process.cwd(), 'src')

// Every component and script under src, read from disk. Test files are not shipped, so they are left out.
const sourceFiles = (directory) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const path = join(directory, entry.name)
  if (entry.isDirectory()) return sourceFiles(path)
  const shipped = (entry.name.endsWith('.vue') || entry.name.endsWith('.js')) && !entry.name.endsWith('.test.js')
  return shipped ? [path] : []
})

// The MD3 window classes, written as pixel literals. A component uses the named breakpoint instead.
const LITERAL_BREAKPOINT = /(?:min|max)-\[(?:599|600|839|840|1240|1600)px\]:/

describe('window breakpoints in the components', () => {
  it('writes the named window breakpoints and no pixel literal for them', () => {
    const offenders = sourceFiles(SOURCE_ROOT).flatMap((file) =>
      readFileSync(file, 'utf8')
        .split('\n')
        .flatMap((line, index) => (LITERAL_BREAKPOINT.test(line) ? [`${relative(process.cwd(), file)}:${index + 1}`] : []))
    )

    expect(offenders, 'a window breakpoint literal in a component: use medium:, expanded:, large:, xlarge:, max-medium: or max-expanded:').toEqual([])
  })
})
