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
 * The state layer and focus ring are CSS, so the tests read the declarations of the rule
 * they pin. Vitest does not process CSS imports (it hands back an empty string), so the
 * stylesheet source is read directly. The values are the Material Design 3 state opacities
 * (material-web tokens, `_md-sys-state.scss`, v0_192) and the MD3 focus indicator.
 */
const stateLayerCss = readFileSync(fileURLToPath(new URL('./state-layer.css', import.meta.url)), 'utf8')

function declarationsOf(css, selector) {
  const start = css.indexOf(`${selector} {`)
  if (start === -1) return ''
  return css.slice(start, css.indexOf('}', start))
}

describe('md-state-layer opacities', () => {
  it('uses the MD3 focus state layer opacity of 12 percent', () => {
    const rule = declarationsOf(stateLayerCss, '.md-state-layer:focus-visible')

    expect(rule).toContain('--md-state-opacity: 0.12')
  })

  it('uses the MD3 pressed state layer opacity of 12 percent', () => {
    const rule = declarationsOf(stateLayerCss, '.md-state-layer:active')

    expect(rule).toContain('--md-state-opacity: 0.12')
  })

  it('keeps the MD3 hover state layer opacity of 8 percent', () => {
    expect(stateLayerCss).toContain('.md-state-layer:hover {\n      --md-state-opacity: 0.08;')
  })

  it('keeps the MD3 dragged state layer opacity of 16 percent', () => {
    const rule = declarationsOf(stateLayerCss, '.md-state-layer.is-dragged')

    expect(rule).toContain('--md-state-opacity: 0.16')
  })
})

describe('md-focus-ring', () => {
  const rule = declarationsOf(stateLayerCss, '.md-focus-ring:focus-visible')

  it('draws a 3px outline', () => {
    expect(rule).toContain('outline: 3px solid')
  })

  it('draws the outline in the secondary colour role', () => {
    expect(rule).toContain('outline: 3px solid var(--md-sys-color-secondary)')
  })

  it('sets the outline outward by 2px', () => {
    expect(rule).toContain('outline-offset: 2px')
  })
})

describe('md-focus-ring-inset', () => {
  const rule = declarationsOf(stateLayerCss, '.md-focus-ring-inset:focus-visible')

  it('draws the same 3px secondary outline as the outset ring', () => {
    expect(rule).toContain('outline: 3px solid var(--md-sys-color-secondary)')
  })

  it('draws the outline inside the element by its full width', () => {
    expect(rule, 'a negative offset keeps the ring inside the window edge').toContain('outline-offset: -3px')
  })

  it('shows only for keyboard focus, not for a pointer press', () => {
    expect(stateLayerCss).not.toContain('.md-focus-ring-inset:focus {')
    expect(declarationsOf(stateLayerCss, '.md-focus-ring-inset')).toBe('')
  })
})
