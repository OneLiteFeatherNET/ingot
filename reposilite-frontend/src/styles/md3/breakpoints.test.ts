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

// @vitest-environment node

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/*
 * The window classes are the only breakpoints. The test reads the stylesheet source, because Vitest does not process
 * CSS imports.
 */
const stylesheet = readFileSync(fileURLToPath(new URL('../../style.css', import.meta.url)), 'utf8')

describe('window class breakpoints', () => {
  const variants: Array<[string, string]> = [
    ['medium', '(width >= 600px)'],
    ['expanded', '(width >= 840px)'],
    ['large', '(width >= 1240px)'],
    ['xlarge', '(width >= 1600px)'],
    ['max-medium', '(width < 600px)'],
    ['max-expanded', '(width < 840px)']
  ]

  for (const [name, query] of variants) {
    it(`declares the ${name} variant on the ${query} media query`, () => {
      expect(stylesheet, `src/style.css declares the ${name} variant`).toContain(`@custom-variant ${name} (@media ${query});`)
    })
  }

  it('resets the default Tailwind breakpoints in @theme', () => {
    expect(stylesheet, '--breakpoint-*: initial is declared inside an @theme block').toMatch(/@theme\s*\{[^}]*--breakpoint-\*:\s*initial;/)
  })

  it('declares no breakpoint token other than the reset', () => {
    const tokens = stylesheet.match(/--breakpoint-[\w*-]+:\s*[^;]+;/g) ?? []
    expect(tokens, 'the reset is the only --breakpoint declaration').toEqual(['--breakpoint-*: initial;'])
  })
})
