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
 * The touch target is a CSS rule, so the test reads the stylesheet source. Vitest does not
 * process CSS imports, which is why the source is read directly.
 */
const touchTargetCss = readFileSync(fileURLToPath(new URL('./touch-target.css', import.meta.url)), 'utf8')

// The body of the @utility block of the given name, which holds the ::after rule of the hit area.
function utilityBody(css, name) {
  const start = css.indexOf(`@utility ${name} {`)
  if (start === -1) return ''
  return css.slice(start, css.indexOf('\n}', start) + 2)
}

describe('md-touch-target', () => {
  const utility = utilityBody(touchTargetCss, 'md-touch-target')
  const after = utility.slice(utility.indexOf('&::after'))

  it('is declared as a utility that draws its hit area on ::after, because ::before belongs to the state layer', () => {
    expect(utility, 'a @utility md-touch-target block').not.toBe('')
    expect(utility).not.toContain('&::before')
    expect(after, 'the hit area rule').not.toBe('')
  })

  it('is absolutely positioned inside the element', () => {
    expect(utility).toContain('position: relative')
    expect(after).toContain('position: absolute')
  })

  it('is centred on the element', () => {
    expect(after).toContain('top: 50%')
    expect(after).toContain('left: 50%')
    expect(after).toContain('translate: -50% -50%')
  })

  it('takes its 48px minimum size from the touch target token', () => {
    expect(touchTargetCss, 'the touch target token is 48px').toContain('--md-sys-touch-target-size: 48px;')
    expect(after).toContain('min-width: var(--md-sys-touch-target-size)')
    expect(after).toContain('min-height: var(--md-sys-touch-target-size)')
  })

  it('is transparent, so it adds no paint', () => {
    expect(after).toContain('content: ""')
    expect(after).not.toContain('background')
  })
})
