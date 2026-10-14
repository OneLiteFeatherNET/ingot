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
import { resolve } from 'node:path'

// The colour values in color.css are the source of truth. The test reads the committed file
// once and never writes to disk. Vitest runs from the frontend directory, so the path is
// relative to it. (A ?raw import is empty under vitest, which stubs CSS.)
const css = readFileSync(resolve(process.cwd(), 'src/styles/md3/color.css'), 'utf8')

const TEXT_PAIRS = [
  ['on-primary', 'primary'],
  ['on-primary-container', 'primary-container'],
  ['on-secondary', 'secondary'],
  ['on-secondary-container', 'secondary-container'],
  ['on-tertiary', 'tertiary'],
  ['on-tertiary-container', 'tertiary-container'],
  ['on-error', 'error'],
  ['on-error-container', 'error-container'],
  ['on-primary-fixed', 'primary-fixed'],
  ['on-primary-fixed-variant', 'primary-fixed-dim'],
  ['on-secondary-fixed', 'secondary-fixed'],
  ['on-secondary-fixed-variant', 'secondary-fixed-dim'],
  ['on-tertiary-fixed', 'tertiary-fixed'],
  ['on-tertiary-fixed-variant', 'tertiary-fixed-dim'],
  ['on-background', 'background'],
  ['on-surface', 'surface'],
  ['on-surface', 'surface-container-highest'],
  ['on-surface-variant', 'surface'],
  ['on-surface-variant', 'surface-container-highest'],
  ['inverse-on-surface', 'inverse-surface'],
  ['primary', 'surface'],
  ['error', 'surface']
]

const CHART_SERIES = 8

const CONTRAST_ROOT_LIGHT = ':root:not(.dark)'
const CONTRAST_DARK = '.dark'

// Blocks are plain `selector { --name: value; }` rules. Comments are stripped first, and the
// @media (prefers-contrast: more) block is cut out on its own because it nests rules.
const stripComments = (text) => text.replace(/\/\*[\s\S]*?\*\//g, '')

const parseDeclarations = (body) => {
  const map = {}
  for (const match of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    map[match[1]] = match[2].trim()
  }
  return map
}

// Every rule whose selector is exactly `selector`, merged in source order (later wins).
const rulesFor = (text, selector) => {
  const map = {}
  for (const match of text.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (match[1].trim() === selector) Object.assign(map, parseDeclarations(match[2]))
  }
  return map
}

// The body of the first @media block whose prelude contains `query`, brace-matched.
const mediaBody = (text, query) => {
  const start = text.indexOf(`@media ${query}`)
  if (start === -1) return ''
  const open = text.indexOf('{', start)
  let depth = 0
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++
    if (text[i] === '}') {
      depth--
      if (depth === 0) return text.slice(open + 1, i)
    }
  }
  return ''
}

const sheet = stripComments(css)
// The base theme is the sheet without the high-contrast media block, which repeats .dark.
const baseSheet = sheet.replace(/@media \(prefers-contrast: more\) \{[\s\S]*?\n\}\n/, '')
const THEMES = {
  light: rulesFor(baseSheet, ':root'),
  dark: rulesFor(baseSheet, '.dark')
}
const CONTRAST = {
  light: rulesFor(mediaBody(sheet, '(prefers-contrast: more)'), CONTRAST_ROOT_LIGHT),
  dark: rulesFor(mediaBody(sheet, '(prefers-contrast: more)'), CONTRAST_DARK)
}

// Merged view for a theme: the base values, with the high-contrast values on top when asked.
const colours = (theme, { highContrast = false } = {}) => ({
  ...THEMES[theme],
  ...(highContrast ? CONTRAST[theme] : {})
})

const channel = (value) => {
  const c = value / 255
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

const hexToRgb = (hex) => {
  const full = hex.length === 4 ? `#${[...hex.slice(1)].map((d) => d + d).join('')}` : hex
  return [1, 3, 5].map((i) => parseInt(full.slice(i, i + 2), 16))
}

const luminance = (hex) => {
  const [r, g, b] = hexToRgb(hex).map(channel)
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

const contrastRatio = (a, b) => {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

// CIE76 colour difference, through CIE L*a*b* with a D65 white point.
const toLab = (hex) => {
  const [r, g, b] = hexToRgb(hex).map(channel)
  const x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
  const y = 0.2126 * r + 0.7152 * g + 0.0722 * b
  const z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
  const f = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116)
  return [116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))]
}

const deltaE = (a, b) => {
  const [l1, a1, b1] = toLab(a)
  const [l2, a2, b2] = toLab(b)
  return Math.hypot(l1 - l2, a1 - a2, b1 - b2)
}

const chartSeries = (values) => Array.from({ length: CHART_SERIES }, (_, i) => values[`--md-ext-color-chart-${i + 1}`])

describe('colour roles', () => {
  for (const theme of ['light', 'dark']) {
    it(`defines every role as a hex value in the ${theme} theme`, () => {
      const values = colours(theme)
      const roles = Object.keys(values).filter((name) => name.startsWith('--md-sys-color-'))

      expect(roles.length, 'the theme should define all 49 MD3 colour roles, fixed roles included').toBe(49)
      for (const name of roles) {
        expect(values[name], `${name} should be a six digit hex value`).toMatch(/^#[0-9a-f]{6}$/i)
      }
    })

    it(`keeps every text pair at 4.5:1 or more in the ${theme} theme`, () => {
      const values = colours(theme)
      for (const [text, background] of TEXT_PAIRS) {
        const ratio = contrastRatio(values[`--md-sys-color-${text}`], values[`--md-sys-color-${background}`])
        expect(ratio, `${text} on ${background} is ${ratio.toFixed(2)}:1, below 4.5:1`).toBeGreaterThanOrEqual(4.5)
      }
    })

    it(`keeps every text pair at 7:1 or more under prefers-contrast: more in the ${theme} theme`, () => {
      const values = colours(theme, { highContrast: true })
      for (const [text, background] of TEXT_PAIRS) {
        const ratio = contrastRatio(values[`--md-sys-color-${text}`], values[`--md-sys-color-${background}`])
        expect(ratio, `high contrast ${text} on ${background} is ${ratio.toFixed(2)}:1, below 7:1`).toBeGreaterThanOrEqual(7)
      }
    })

    it(`takes the accent roles from the official OneLiteFeather tables in the ${theme} theme`, () => {
      const expected = theme === 'light'
        ? { '--md-sys-color-primary': '#ac1c67', '--md-sys-color-secondary': '#94387c', '--md-sys-color-tertiary': '#4d4fb7' }
        : { '--md-sys-color-primary': '#ff88b7', '--md-sys-color-secondary': '#f88cd6', '--md-sys-color-tertiary': '#9496ff' }

      for (const [name, value] of Object.entries(expected)) {
        expect(THEMES[theme][name], `${name} should be the official OLF value`).toBe(value)
      }
    })
  }

  it('keeps the surfaces neutral, without the pink tint of the official table', () => {
    for (const theme of ['light', 'dark']) {
      const [, a, b] = toLab(THEMES[theme]['--md-sys-color-surface'])
      const chroma = Math.hypot(a, b)
      expect(chroma, `the ${theme} surface should be near neutral (CIE LCh chroma ${chroma.toFixed(1)})`).toBeLessThan(6)
    }
  })

  it('keeps the light surface near white and the dark surface near the charcoal brand tone', () => {
    expect(toLab(THEMES.light['--md-sys-color-surface'])[0], 'light surface should be near white (L* above 96)').toBeGreaterThan(96)
    const darkL = toLab(THEMES.dark['--md-sys-color-surface'])[0]
    expect(darkL, `dark surface L* is ${darkL.toFixed(1)}, expected a near-black charcoal`).toBeLessThan(12)
  })
})

describe('high contrast', () => {
  it('defines the light high-contrast values on :root:not(.dark), so a dark root keeps the dark values', () => {
    expect(CONTRAST.light['--md-sys-color-on-surface'], 'the light high-contrast body text should be defined').toBeDefined()
  })

  it('defines the dark high-contrast values on .dark', () => {
    expect(CONTRAST.dark['--md-sys-color-on-surface'], 'the dark high-contrast body text should be defined').toBeDefined()
  })

  it('does not change the surfaces, only the text and outline roles', () => {
    for (const name of ['--md-sys-color-surface', '--md-sys-color-surface-container', '--md-sys-color-background']) {
      expect(CONTRAST.light[name], `${name} should keep its standard value under high contrast`).toBeUndefined()
      expect(CONTRAST.dark[name], `${name} should keep its standard value under high contrast`).toBeUndefined()
    }
  })
})

describe('brand expression', () => {
  it('defines the four-stop OLF gradient at 135 degrees', () => {
    const gradient = THEMES.light['--olf-gradient']

    expect(gradient, 'the gradient should be defined on :root').toBeDefined()
    expect(gradient, 'the gradient should run at 135 degrees').toMatch(/^linear-gradient\(135deg,/)
    for (const stop of ['#f7931d', '#ec008b', '#91268f', '#2a388f']) {
      expect(gradient.toLowerCase(), `the gradient should contain the OLF stop ${stop}`).toContain(stop)
    }
  })

  it('draws the brand bar as a 4px band filled with the gradient', () => {
    expect(sheet, 'the brand bar should be a 4px band filled with the gradient').toMatch(
      /\.olf-brand-bar\s*\{[^}]*height:\s*4px[^}]*background:\s*var\(--olf-gradient\)/
    )
  })

  it('keeps the brand bar visible under forced colours', () => {
    expect(mediaBody(sheet, '(forced-colors: active)'), 'forced colours should give the brand bar a visible fill').toMatch(/\.olf-brand-bar/)
  })
})

describe('chart series', () => {
  for (const theme of ['light', 'dark']) {
    it(`gives every series at least 3:1 against the surfaces in the ${theme} theme`, () => {
      const series = chartSeries(THEMES[theme])
      const surfaces = ['--md-sys-color-surface', '--md-sys-color-surface-container-highest']
      for (const [index, colour] of series.entries()) {
        for (const surface of surfaces) {
          const ratio = contrastRatio(colour, THEMES[theme][surface])
          expect(ratio, `chart-${index + 1} is ${ratio.toFixed(2)}:1 on ${surface}, below 3:1`).toBeGreaterThanOrEqual(3)
        }
      }
    })

    it(`keeps every pair of series at least 15 CIE76 units apart in the ${theme} theme`, () => {
      const series = chartSeries(THEMES[theme])
      for (let i = 0; i < series.length; i++) {
        for (let j = i + 1; j < series.length; j++) {
          const difference = deltaE(series[i], series[j])
          expect(difference, `chart-${i + 1} and chart-${j + 1} differ by only ${difference.toFixed(1)}`).toBeGreaterThanOrEqual(15)
        }
      }
    })
  }

  it('starts the series with the brand pink, a hue between magenta and red', () => {
    const [light] = chartSeries(THEMES.light)
    const [, a, b] = toLab(light)
    const hue = ((Math.atan2(b, a) * 180) / Math.PI + 360) % 360
    // Pink and magenta sit between roughly 330 and 25 degrees in CIE LCh.
    expect(hue >= 330 || hue <= 25, `chart-1 is at ${hue.toFixed(0)} degrees, expected the OLF pink family`).toBe(true)
  })
})
