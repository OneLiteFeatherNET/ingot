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
 * The type scale and the motion durations are CSS tokens, so the tests read the stylesheet
 * sources. Vitest does not process CSS imports, which is why the sources are read directly.
 * The values follow the material-web token references (`_md-sys-typescale.scss` and
 * `_md-sys-motion.scss`, v0_192).
 */
const read = (relativePath) => readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')

const typography = read('./typography.css')
const motion = read('./shape-elevation-motion.css')
const globalStyles = read('../../style.css')

describe('type scale is the md3 scale', () => {
  it('does not define the off-scale text sizes in the theme', () => {
    expect(globalStyles).not.toMatch(/--text-(xxs|xm|ssm)\b/)
  })

  it('sets the prominent label large weight to 700', () => {
    expect(typography).toContain('--md-sys-typescale-label-large-weight-prominent: 700;')
  })

  it('sets the prominent label medium weight to 700', () => {
    expect(typography).toContain('--md-sys-typescale-label-medium-weight-prominent: 700;')
  })

  it('defines md-label-large-prominent with the label large size and the prominent weight', () => {
    expect(typography).toMatch(
      /@utility md-label-large-prominent \{[^}]*font-size: var\(--md-sys-typescale-label-large-size\)[^}]*font-weight: var\(--md-sys-typescale-label-large-weight-prominent\)/
    )
  })

  it('defines md-label-medium-prominent with the label medium size and the prominent weight', () => {
    expect(typography).toMatch(
      /@utility md-label-medium-prominent \{[^}]*font-size: var\(--md-sys-typescale-label-medium-size\)[^}]*font-weight: var\(--md-sys-typescale-label-medium-weight-prominent\)/
    )
  })
})

describe('extra long motion durations', () => {
  it.each([
    ['extra-long1', '700ms'],
    ['extra-long2', '800ms'],
    ['extra-long3', '900ms'],
    ['extra-long4', '1000ms']
  ])('defines %s as %s', (name, value) => {
    expect(motion).toContain(`--md-sys-motion-duration-${name}: ${value};`)
  })
})
