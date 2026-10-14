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
 * The shared page gutter is a CSS rule, so the test reads the stylesheet source. Vitest does
 * not process CSS imports, which is why the source is read directly.
 *
 * MD3 window size classes set the margins: 16px on compact windows (below 600px), 24px on
 * medium and expanded windows (600px and up). The rule has to sit inside @layer components.
 * Outside a layer it would beat every view's own px-* utility.
 */
const globalStyles = readFileSync(fileURLToPath(new URL('../../style.css', import.meta.url)), 'utf8')

describe('shared container gutter', () => {
  it('pads the container 16px on compact windows', () => {
    expect(globalStyles).toMatch(/\.container \{\s*padding-inline: 16px;\s*\}/)
  })

  it('pads the container 24px from 600px up, the medium window size', () => {
    expect(globalStyles).toMatch(/@media \(width >= 600px\) \{\s*\.container \{\s*padding-inline: 24px;\s*\}\s*\}/)
  })

  it('keeps the gutter rule inside the components layer', () => {
    const layerStart = globalStyles.indexOf('@layer components {')
    const containerRule = globalStyles.indexOf('.container {')

    expect(layerStart).toBeGreaterThan(-1)
    expect(containerRule).toBeGreaterThan(layerStart)
  })

  it('no longer uses the 40px padding of the old rule', () => {
    expect(globalStyles).not.toContain('px-10 max-sm:px-4')
  })
})
