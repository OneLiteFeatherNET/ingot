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
import stylesheet from './style.css?raw'

// The text of the block that starts at `start`, from its at-rule to the brace that closes it.
const blockAt = (css, start) => {
  let depth = 0
  for (let index = css.indexOf('{', start); index < css.length; index++) {
    if (css[index] === '{') depth++
    if (css[index] === '}' && --depth === 0) return css.slice(start, index + 1)
  }
  return ''
}

describe('global reduced motion rule in the stylesheet', () => {
  const baseLayer = stylesheet.indexOf('@layer base {')
  const reducedMotion = stylesheet.indexOf('@media (prefers-reduced-motion: reduce)', baseLayer)

  it('has a reduced motion block inside the base layer', () => {
    expect(baseLayer, 'the base layer of the stylesheet').toBeGreaterThanOrEqual(0)
    expect(reducedMotion, 'a prefers-reduced-motion block inside the base layer').toBeGreaterThan(baseLayer)
  })

  it('applies to every element', () => {
    expect(blockAt(stylesheet, reducedMotion), 'the selector of the reduced motion rule').toMatch(/\*/)
  })

  it('shortens animations and transitions to 0.01ms and plays each animation once', () => {
    const block = blockAt(stylesheet, reducedMotion)

    expect(block, 'the animation duration').toContain('animation-duration: 0.01ms')
    expect(block, 'the animation iteration count').toContain('animation-iteration-count: 1')
    expect(block, 'the transition duration').toContain('transition-duration: 0.01ms')
  })
})
