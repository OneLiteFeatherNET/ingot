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
 * The colours of the repository type badges, checked against the theme values in color.css. The pairs are the ones
 * TypeBadge.vue uses (its TONES and the border of the Mixed badge); the warning text of a Mixed repository is the
 * extension token. Text must reach 4.5 to 1, and icons and borders 3 to 1 (WCAG 1.4.3, 1.4.11). The test reads the
 * committed file and computes the ratios; nothing is rendered and nothing is written.
 */

const css = readFileSync(fileURLToPath(new URL('../../styles/md3/color.css', import.meta.url)), 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')

// The declarations of a top level rule, such as `:root` or `.dark`. The first rule with that selector is the default theme.
const ruleOf = (selector) => {
  const start = css.indexOf(`${selector} {`)
  if (start === -1) throw new Error(`no rule ${selector} in color.css`)
  return css.slice(start, css.indexOf('}', start))
}

// The rule of a selector that declares the given name. The extension tokens sit in a second :root and .dark rule.
const ruleWith = (selector, name) => {
  for (let start = css.indexOf(`${selector} {`); start !== -1; start = css.indexOf(`${selector} {`, start + 1)) {
    const rule = css.slice(start, css.indexOf('}', start))
    if (rule.includes(name)) return rule
  }
  throw new Error(`no rule ${selector} declares ${name}`)
}

const valueOf = (rule, name) => {
  const match = new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`).exec(rule)
  if (!match) throw new Error(`no value for ${name}`)
  return match[1]
}

const channel = (hex, offset) => {
  const value = parseInt(hex.slice(offset, offset + 2), 16) / 255
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}

const luminance = (hex) => 0.2126 * channel(hex, 1) + 0.7152 * channel(hex, 3) + 0.0722 * channel(hex, 5)

const contrast = (foreground, background) => {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (lighter + 0.05) / (darker + 0.05)
}

// The badge tones of TypeBadge.vue: the background and the text (and icon) colour of each type.
const BADGE_TONES = {
  HOSTED: ['tertiary-container', 'on-tertiary-container'],
  PROXY: ['secondary-container', 'on-secondary-container'],
  GROUP: ['primary-container', 'on-primary-container']
}

const THEMES = {
  light: ':root',
  dark: '.dark'
}

for (const [theme, selector] of Object.entries(THEMES)) {
  describe(`repository type badges in the ${theme} theme`, () => {
    const rule = ruleOf(selector)
    const colour = (name) => valueOf(rule, `--md-sys-color-${name}`)
    const surface = colour('surface')

    for (const [type, [background, text]] of Object.entries(BADGE_TONES)) {
      it(`names the ${type} type in text at 4.5 to 1 or more`, () => {
        const ratio = contrast(colour(text), colour(background))
        expect(ratio, `${text} on ${background} for ${type}`).toBeGreaterThanOrEqual(4.5)
      })

      it(`draws the ${type} icon at 3 to 1 or more, in the text colour`, () => {
        // The icon takes the text colour (currentColor), so its ratio is the one of the text on the badge.
        const ratio = contrast(colour(text), colour(background))
        expect(ratio, `icon of ${type} on ${background}`).toBeGreaterThanOrEqual(3)
      })
    }

    it('names the derived marker and the Mixed type in on-surface-variant at 4.5 to 1 or more on the surface', () => {
      expect(contrast(colour('on-surface-variant'), surface), 'on-surface-variant on surface').toBeGreaterThanOrEqual(4.5)
    })

    it('draws the Mixed badge border in outline at 3 to 1 or more on the surface', () => {
      expect(contrast(colour('outline'), surface), 'outline on surface').toBeGreaterThanOrEqual(3)
    })

    it('shows the Mixed warning in the warning colour at 4.5 to 1 or more on the surface', () => {
      const warning = valueOf(ruleWith(selector, '--md-ext-color-warning'), '--md-ext-color-warning')
      expect(contrast(warning, surface), 'warning text on surface').toBeGreaterThanOrEqual(4.5)
    })
  })
}
