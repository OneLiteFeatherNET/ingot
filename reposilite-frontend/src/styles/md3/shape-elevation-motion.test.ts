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

describe('named window size classes', () => {
  // The MD3 window size classes: compact below 600px, medium from 600px, expanded from 840px, large from 1240px and
  // extra large from 1600px. They are custom variants, so the built-in container keeps its width steps.
  const classes = [
    ['medium', '(width >= 600px)'],
    ['expanded', '(width >= 840px)'],
    ['large', '(width >= 1240px)'],
    ['xlarge', '(width >= 1600px)'],
    ['max-medium', '(width < 600px)'],
    ['max-expanded', '(width < 840px)'],
  ]

  for (const [name, query] of classes) {
    it(`declares the ${name} variant on the ${query} media query`, () => {
      expect(stylesheet, `src/style.css declares the ${name} variant`).toContain(`@custom-variant ${name} (@media ${query});`)
    })
  }

  it('declares no theme breakpoint, so that Tailwind keeps the width steps of its container', () => {
    expect(stylesheet, 'no --breakpoint token in src/style.css').not.toMatch(/--breakpoint-/)
  })
})

describe('window margin tokens in the theme', () => {
  it('declares the 16px margin of compact windows', () => {
    expect(theme, 'the compact window margin is 16px').toMatch(/--md-sys-window-margin-compact:\s*16px;/)
  })

  it('declares the 24px margin of medium windows and up', () => {
    expect(theme, 'the medium window margin is 24px').toMatch(/--md-sys-window-margin-medium:\s*24px;/)
  })
})

// The token file of the shape, elevation and motion foundation. Its theme block is the one Tailwind reads for the
// utilities (z-md-*, opacity keys, container keys, spacing keys and animate-* names).
const shapeCss = readFileSync(join(process.cwd(), 'src', 'styles', 'md3', 'shape-elevation-motion.css'), 'utf8')
const shapeTheme = blockAt(shapeCss, shapeCss.indexOf('@theme {'))
const shapeRoot = blockAt(shapeCss, shapeCss.indexOf(':root {'))

describe('named stacking, scrim, icon and layout tokens', () => {
  it.each([
    ['--z-index-md-sticky', '20'],
    ['--z-index-md-top-bar', '30'],
    ['--z-index-md-menu', '50'],
    ['--z-index-md-snackbar', '1100'],
  ])('declares the stacking token %s as %s in the theme', (name, value) => {
    expect(shapeTheme, `${name} is a theme token, so z-md-* utilities exist`).toContain(`${name}: ${value};`)
  })

  it('takes the dialog stacking token from the MD3 foundation, which the vue-final-modal rule also reads', () => {
    expect(shapeTheme, 'the dialog z-index token refers to --md-sys-z-dialog').toContain('--z-index-md-dialog: var(--md-sys-z-dialog);')
    expect(shapeRoot, 'the dialog value stays 1000 in the foundation').toMatch(/--md-sys-z-dialog:\s*1000;/)
  })

  it('declares the scrim opacity of the overlays and the stronger scrim of the migration dialog as opacity tokens', () => {
    expect(shapeTheme, 'the scrim opacity of the overlays').toContain('--opacity-md-scrim: 32%;')
    expect(shapeTheme, 'the stronger scrim of the migration dialog').toContain('--opacity-md-scrim-strong: 40%;')
  })

  it('declares the icon size as a spacing token of 18px', () => {
    expect(shapeTheme, 'the icon size is 1.125rem, which is 18px at the 16px root size').toContain('--spacing-md-icon: 1.125rem;')
  })

  it.each([
    ['--spacing-md-top-bar', '4rem'],
    ['--spacing-md-rail', '6rem'],
    ['--spacing-md-bottom-bar', '5rem'],
    ['--container-md-reading', '60rem'],
    ['--container-md-sheet', '20rem'],
    ['--container-md-snackbar', '35rem'],
  ])('declares the layout token %s as %s', (name, value) => {
    expect(shapeTheme, `${name} is a theme token`).toContain(`${name}: ${value};`)
  })

  it('declares the shadow roles once, and every elevation level refers to them', () => {
    expect(shapeRoot, 'the key shadow role').toContain('--md-sys-shadow-key: rgb(0 0 0 / 0.3);')
    expect(shapeRoot, 'the ambient shadow role').toContain('--md-sys-shadow-ambient: rgb(0 0 0 / 0.15);')
    for (const level of [1, 2, 3, 4, 5]) {
      const line = shapeRoot.split('\n').find((text) => text.includes(`--md-sys-elevation-level${level}:`))
      expect(line, `elevation level ${level} refers to the key shadow role`).toContain('var(--md-sys-shadow-key)')
      expect(line, `elevation level ${level} refers to the ambient shadow role`).toContain('var(--md-sys-shadow-ambient)')
    }
  })
})

describe('duration utilities', () => {
  const names = [...shapeRoot.matchAll(/--md-sys-motion-duration-([a-z0-9-]+):/g)].map((match) => match[1])

  it('finds the motion duration tokens in the foundation', () => {
    expect(names.length, 'the MD3 motion duration tokens').toBeGreaterThanOrEqual(16)
  })

  it.each(names)('declares duration-md-%s for the motion duration token', (name) => {
    const at = shapeCss.indexOf(`@utility duration-md-${name} {`)
    expect(at, `a @utility duration-md-${name} block`).toBeGreaterThanOrEqual(0)
    expect(blockAt(shapeCss, at), 'the utility sets the transition duration from its token').toContain(
      `transition-duration: var(--md-sys-motion-duration-${name});`
    )
  })
})

describe('animation tokens', () => {
  it.each([
    ['--animate-md-linear-slide', 'md-linear-slide 1.6s var(--md-sys-motion-easing-standard) infinite'],
    ['--animate-md-linear-slide-reduced', 'md-linear-slide 4s var(--md-sys-motion-easing-standard) infinite'],
    ['--animate-md-snackbar-enter', 'md-snackbar-enter var(--md-sys-motion-duration-short3) var(--md-sys-motion-easing-standard) both'],
    ['--animate-md-snackbar-leave', 'md-snackbar-leave var(--md-sys-motion-duration-short3) var(--md-sys-motion-easing-standard) both'],
    ['--animate-skeleton-fade-in', 'skeleton-fade-in var(--md-sys-motion-duration-extra-long2) ease-out var(--md-sys-motion-duration-short4) both'],
  ])('declares %s from motion tokens', (name, value) => {
    expect(shapeTheme, `${name} is an animate token built from the motion tokens`).toContain(`${name}: ${value};`)
  })

  it.each(['md-linear-slide', 'md-snackbar-enter', 'md-snackbar-leave', 'skeleton-fade-in'])(
    'declares the keyframes of %s next to the tokens',
    (name) => {
      expect(shapeCss, `@keyframes ${name} in the token file`).toContain(`@keyframes ${name} {`)
    }
  )
})
