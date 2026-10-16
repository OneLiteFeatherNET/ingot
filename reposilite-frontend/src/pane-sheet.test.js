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
 * The content sheet's brand bar inset is a CSS rule, so the test reads the stylesheet source.
 * Vitest does not process CSS imports, which is why the source is read directly.
 */
const globalStyles = readFileSync(fileURLToPath(new URL('./style.css', import.meta.url)), 'utf8')

describe('brand bar inside the content sheet', () => {
  it('insets the brand bar by the sheet corner radius from 600px up', () => {
    expect(globalStyles).toMatch(
      /@media \(width >= 600px\) \{\s*\.pane-sheet \.olf-brand-bar \{\s*margin-inline: var\(--md-sys-shape-corner-extra-large\);\s*\}\s*\}/
    )
  })

  it('keeps the inset rule inside the components layer', () => {
    const layerStart = globalStyles.indexOf('@layer components {')
    const insetRule = globalStyles.indexOf('.pane-sheet .olf-brand-bar')

    expect(layerStart).toBeGreaterThan(-1)
    expect(insetRule).toBeGreaterThan(layerStart)
  })
})
