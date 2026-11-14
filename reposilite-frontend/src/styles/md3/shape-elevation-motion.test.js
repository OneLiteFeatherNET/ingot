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
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

// The theme block is read from the stylesheet on disk, as in src/style.test.js. Vitest does not process stylesheets
// for unit tests, so the source text is the only place the tokens can be checked.
const stylesheet = readFileSync(join(process.cwd(), 'src', 'style.css'), 'utf8')

// The text of the block that starts at `start`, from its at-rule to the brace that closes it.
const blockAt = (css, start) => {
  let depth = 0
  for (let index = css.indexOf('{', start); index < css.length; index++) {
    if (css[index] === '{') depth++
    if (css[index] === '}' && --depth === 0) return css.slice(start, index + 1)
  }
  return ''
}

const themeAt = stylesheet.indexOf('@theme {')
const theme = themeAt < 0 ? '' : blockAt(stylesheet, themeAt)

describe('named window breakpoints in the theme', () => {
  // The MD3 window size classes: compact below 600px, medium from 600px, expanded from 840px, large from 1240px and
  // extra large from 1600px. Tailwind derives the variants medium:, expanded:, large: and xlarge: from these tokens.
  const breakpoints = [
    ['medium', '600px'],
    ['expanded', '840px'],
    ['large', '1240px'],
    ['xlarge', '1600px'],
  ]

  for (const [name, width] of breakpoints) {
    it(`declares the ${name} window class at ${width}`, () => {
      expect(theme, `the @theme block declares --breakpoint-${name}`).toMatch(
        new RegExp(`--breakpoint-${name}:\\s*${width};`)
      )
    })
  }
})

describe('window margin tokens in the theme', () => {
  it('declares the 16px margin of compact windows', () => {
    expect(theme, 'the compact window margin is 16px').toMatch(/--md-sys-window-margin-compact:\s*16px;/)
  })

  it('declares the 24px margin of medium windows and up', () => {
    expect(theme, 'the medium window margin is 24px').toMatch(/--md-sys-window-margin-medium:\s*24px;/)
  })
})
