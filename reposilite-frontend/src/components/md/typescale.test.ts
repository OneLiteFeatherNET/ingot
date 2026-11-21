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
import { typescale } from './typescale'

/*
 * Code that cannot take a class, such as the options of a chart drawn on a canvas, reads its font sizes from
 * `typescale.ts`. The tokens live in `typography.css`, so this test reads the stylesheet and checks that each
 * size in the module is the size of the token with the same role.
 */
const typography = readFileSync(fileURLToPath(new URL('../../styles/md3/typography.css', import.meta.url)), 'utf8')

const kebab = (role: string) => role.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`)

const tokenSize = (role: string): number | undefined => {
  const match = typography.match(new RegExp(`--md-sys-typescale-${kebab(role)}-size:\\s*(\\d+(?:\\.\\d+)?)px;`))
  return match ? Number(match[1]) : undefined
}

describe('typescale module', () => {
  it('names the label roles that the chart reads', () => {
    expect(Object.keys(typescale), 'the chart reads label small and label medium').toEqual(
      expect.arrayContaining(['labelSmall', 'labelMedium'])
    )
  })

  it.each(Object.entries(typescale))('gives %s the font size of its token in typography.css', (role, scale) => {
    expect(scale.size, `the size of ${role} equals --md-sys-typescale-${kebab(role)}-size`).toBe(tokenSize(role))
  })
})
