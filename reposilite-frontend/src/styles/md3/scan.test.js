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

// The lines of every shipped source that match a pattern, as `file:line`.
const offendersOf = (pattern) => sourceFiles(SOURCE_ROOT).flatMap((file) =>
  readFileSync(file, 'utf8')
    .split('\n')
    .flatMap((line, index) => (pattern.test(line) ? [`${relative(process.cwd(), file)}:${index + 1}`] : []))
)

// Values that a view must take from a token. Each one names the replacement in its message.
const TOKEN_LITERALS = [
  [/(?:peer|group)-[\w:-]*:opacity-\[/, 'a state opacity: use opacity-md-state-hover, -focus or -pressed'],
  [/group-hover:\[--md-state/, 'an inline state opacity: use the md-state-hover utility with a variant'],
  [/has-focus-visible:outline|has-\[input:focus/, 'a focus ring on a container: use has-focus-visible:md-focus-ring-within'],
  [/bg-scrim\/(?:32|40)/, 'a scrim opacity: use bg-scrim/md-scrim or bg-scrim/md-scrim-strong'],
  [/\b(?:size|h|w)-\[18px\]/, 'an icon size: use size-md-icon'],
  [/top-\[calc\(4rem/, 'the top bar offset: use top-[calc(var(--spacing-md-top-bar)+--spacing(4))]'],
  [/min-h-\[calc\(100dvh_-_4rem\)\]/, 'the page height under the top bar: use a spacing token'],
  [/max-w-\[960px\]/, 'the reading measure: use max-w-md-reading'],
  [/\bpane-sheet\b/, 'a class that no stylesheet defines: remove it'],
]

describe('style literals of the views and primitives', () => {
  for (const [pattern, advice] of TOKEN_LITERALS) {
    it(`writes no ${pattern.source} literal in the shipped sources`, () => {
      expect(offendersOf(pattern), advice).toEqual([])
    })
  }

  it('takes the snackbar width from the snackbar container token', () => {
    const snackbar = readFileSync(join(SOURCE_ROOT, 'components', 'md', 'MdSnackbar.vue'), 'utf8')
    expect(snackbar, 'max-w-md-snackbar replaces max-w-[560px]').not.toContain('max-w-[560px]')
  })

  it('takes the navigation rail width from the rail token', () => {
    const rail = readFileSync(join(SOURCE_ROOT, 'components', 'md', 'MdNavigationRail.vue'), 'utf8')
    expect(rail, 'w-md-rail replaces w-24').not.toMatch(/\bw-24\b/)
  })

  it('keeps the bottom bar offset in the spacing token and not in a 5rem literal', () => {
    expect(offendersOf(/\b5rem\b/).filter((line) => line.includes('src/pages/IndexPage.vue')), 'the bottom bar height is md-bottom-bar').toEqual([])
  })
})
