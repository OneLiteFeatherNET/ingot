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

const stateLayerCss = readFileSync(fileURLToPath(new URL('./state-layer.css', import.meta.url)), 'utf8')

// The text of the @utility block with the given name, from its opening brace to the brace that closes it.
function utilityBlock(css, name) {
  const start = css.indexOf(`@utility ${name} {`)
  if (start === -1) return ''
  let depth = 0
  for (let index = css.indexOf('{', start); index < css.length; index++) {
    if (css[index] === '{') depth++
    if (css[index] === '}' && --depth === 0) return css.slice(start, index + 1)
  }
  return ''
}

describe('state layers, focus rings and the touch target are utilities', () => {
  it.each(['md-state-layer', 'md-focus-ring', 'md-focus-ring-inset'])(
    'declares %s with @utility',
    (name) => {
      expect(utilityBlock(stateLayerCss, name), `a @utility ${name} block`).not.toBe('')
    }
  )

  it('declares md-touch-target with @utility, in the touch target file', () => {
    const touchTargetCss = readFileSync(fileURLToPath(new URL('./touch-target.css', import.meta.url)), 'utf8')
    expect(utilityBlock(touchTargetCss, 'md-touch-target'), 'a @utility md-touch-target block').not.toBe('')
  })

  it.each(['md-state-hover', 'md-state-focus', 'md-state-pressed', 'md-state-dragged', 'md-focus-ring-within'])(
    'declares the variant-driven utility %s',
    (name) => {
      expect(utilityBlock(stateLayerCss, name), `a @utility ${name} block`).not.toBe('')
    }
  )

  it('keeps no plain class selector for the state layer, so the helpers only exist as utilities', () => {
    expect(stateLayerCss, 'no .md-state-layer class rule outside a utility').not.toMatch(/(^|\n)\s*\.md-[a-z-]+[^{]*\{/)
  })
})

describe('state opacity tokens', () => {
  it.each([
    ['--md-sys-state-hover-opacity', '0.08'],
    ['--md-sys-state-focus-opacity', '0.12'],
    ['--md-sys-state-pressed-opacity', '0.12'],
    ['--md-sys-state-dragged-opacity', '0.16'],
    ['--md-sys-state-disabled-container-opacity', '0.12'],
    ['--md-sys-state-disabled-content-opacity', '0.38'],
  ])('declares %s as %s', (name, value) => {
    expect(stateLayerCss, `${name} is an MD3 state token`).toContain(`${name}: ${value};`)
  })

  it('exposes the disabled opacities as theme keys for the opacity modifiers', () => {
    expect(stateLayerCss, 'bg-on-surface/md-disabled-container reads this key').toContain('--opacity-md-disabled-container:')
    expect(stateLayerCss, 'text-on-surface/md-disabled-content reads this key').toContain('--opacity-md-disabled-content:')
  })
})

describe('md-state-layer opacities', () => {
  const layer = utilityBlock(stateLayerCss, 'md-state-layer')

  it('takes the focus state opacity from its token on keyboard focus', () => {
    expect(layer).toContain('&:focus-visible')
    expect(layer).toContain('--md-state-opacity: var(--md-sys-state-focus-opacity)')
  })

  it('takes the pressed state opacity from its token', () => {
    expect(layer).toContain('--md-state-opacity: var(--md-sys-state-pressed-opacity)')
  })

  it('takes the hover state opacity from its token, on devices that can hover', () => {
    expect(layer).toContain('--md-state-opacity: var(--md-sys-state-hover-opacity)')
    expect(layer).toMatch(/@media \(hover: hover\)/)
  })

  it('takes the dragged state opacity from its token', () => {
    expect(layer).toContain('--md-state-opacity: var(--md-sys-state-dragged-opacity)')
  })

  it('writes no literal opacity inside the utility', () => {
    expect(layer, 'a state value is a token, not a number').not.toMatch(/--md-state-opacity:\s*0\.\d/)
  })

  it.each([
    ['md-state-hover', '--md-sys-state-hover-opacity'],
    ['md-state-focus', '--md-sys-state-focus-opacity'],
    ['md-state-pressed', '--md-sys-state-pressed-opacity'],
    ['md-state-dragged', '--md-sys-state-dragged-opacity'],
  ])('%s sets the state opacity from %s, for use with a variant', (name, token) => {
    expect(utilityBlock(stateLayerCss, name)).toContain(`--md-state-opacity: var(${token})`)
  })
})

describe('md-focus-ring', () => {
  const rule = utilityBlock(stateLayerCss, 'md-focus-ring')

  it('draws a 3px outline on keyboard focus only', () => {
    expect(rule).toContain('&:focus-visible')
    expect(rule).toContain('outline: var(--md-sys-focus-ring-width) solid var(--md-sys-color-secondary)')
  })

  it('takes the width and the outward offset from the focus ring tokens', () => {
    expect(stateLayerCss).toContain('--md-sys-focus-ring-width: 3px;')
    expect(stateLayerCss).toContain('--md-sys-focus-ring-offset: 2px;')
    expect(rule).toContain('outline-offset: var(--md-sys-focus-ring-offset)')
  })
})

describe('md-focus-ring-inset', () => {
  const rule = utilityBlock(stateLayerCss, 'md-focus-ring-inset')

  it('draws the same 3px secondary outline as the outset ring', () => {
    expect(rule).toContain('outline: var(--md-sys-focus-ring-width) solid var(--md-sys-color-secondary)')
  })

  it('draws the outline inside the element by its full width', () => {
    expect(rule, 'a negative offset of the width keeps the ring inside the window edge').toContain(
      'outline-offset: calc(var(--md-sys-focus-ring-width) * -1)'
    )
  })

  it('shows only for keyboard focus, not for a pointer press', () => {
    expect(rule).toContain('&:focus-visible')
  })
})

describe('md-focus-ring-within', () => {
  const rule = utilityBlock(stateLayerCss, 'md-focus-ring-within')

  it('draws the focus ring of its container without a selector of its own, so a variant can drive it', () => {
    expect(rule).not.toContain(':focus')
    expect(rule).toContain('outline: var(--md-sys-focus-ring-width) solid var(--md-sys-color-secondary)')
    expect(rule).toContain('outline-offset: var(--md-sys-focus-ring-offset)')
  })
})
