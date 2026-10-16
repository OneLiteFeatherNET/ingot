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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { Variant, TonalPalette, Hct, argbFromHex, hexFromArgb } from '@material/material-color-utilities'
import {
  THEME_STYLE_ID,
  ROLE_NAMES,
  SCHEME_VARIANTS,
  buildThemeCss,
  normalizeAppearance,
  schemeClassFor,
  applyTheme,
  applyFavicon,
  logoFor,
  devSeedFromSearch
} from './runtime'

// Every test builds its own document and spies, so no state leaks between tests.
const freshDocument = () => document.implementation.createHTMLDocument('theme')

const SEED = '#006a6a'
const OVERRIDE = '#ec008b'

const SETTINGS = Object.freeze({
  seedColor: '',
  schemeVariant: 'VIBRANT',
  primaryColor: '',
  secondaryColor: '',
  tertiaryColor: '',
  errorColor: '',
  logoDark: '',
  favicon: '',
  footerText: ''
})

const withSettings = (overrides) => ({ ...SETTINGS, ...overrides })

// Rules are plain `selector { --name: value; }` blocks. The prefers-contrast block nests its
// rules, so the output is split at the @media keyword and each half is read on its own.
const splitMedia = (css) => {
  const index = css.indexOf('@media')
  return index < 0 ? [css, ''] : [css.slice(0, index), css.slice(index)]
}

const rulesIn = (text) =>
  [...text.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(([, selector, body]) => ({
    selector: selector.trim(),
    declarations: Object.fromEntries(
      [...body.matchAll(/--([\w-]+):\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim()])
    )
  }))

const blocksOf = (css) => {
  const [top, media] = splitMedia(css)
  const find = (text, selector) => rulesIn(text).find(rule => rule.selector === selector)?.declarations
  return {
    light: find(top, ':root'),
    dark: find(top, '.dark'),
    lightContrast: find(media, ':root:not(.dark)'),
    darkContrast: find(media, '.dark')
  }
}

const roleNames = (declarations) =>
  Object.keys(declarations)
    .filter(name => name.startsWith('md-sys-color-'))
    .map(name => name.slice('md-sys-color-'.length))
    .sort()

// The static colour file is the source of truth for the role names; the generated CSS has to
// define exactly the same roles, in every block, or a role would silently keep its static value.
const staticRootRoles = () => {
  const css = readFileSync(resolve(process.cwd(), 'src/styles/md3/color.css'), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
  const [top] = splitMedia(css)
  return roleNames(rulesIn(top).find(rule => rule.selector === ':root').declarations)
}

const warnings = () => vi.spyOn(console, 'warn').mockImplementation(() => {})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('buildThemeCss defaults', () => {
  it('emits nothing when no seed is set', () => {
    expect(buildThemeCss(SETTINGS), 'the default theme is the static stylesheet, so nothing is injected').toBe('')
  })

  it('emits nothing for an empty object', () => {
    expect(buildThemeCss({}), 'a response without an appearance block must not change the page').toBe('')
  })

  it('ignores key colours when no seed is set, and says so', () => {
    const warn = warnings()
    const css = buildThemeCss(withSettings({ primaryColor: OVERRIDE }))

    expect(css, 'an override has nothing to be an override of without a seed').toBe('')
    expect(warn, 'the operator is told the key colour had no effect').toHaveBeenCalledWith(expect.stringContaining('seedColor'))
  })
})

describe('buildThemeCss roles', () => {
  it('lists the same role names as the static colour file', () => {
    expect(ROLE_NAMES.slice().sort(), 'ROLE_NAMES must mirror color.css').toEqual(staticRootRoles())
  })

  it('defines every role in the light block', () => {
    const { light } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(roleNames(light), 'the light block must redefine each role of color.css').toEqual(staticRootRoles())
  })

  it('defines every role in the dark block', () => {
    const { dark } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(roleNames(dark), 'the dark block must redefine each role of color.css').toEqual(staticRootRoles())
  })

  it('defines every role in the high contrast blocks', () => {
    const { lightContrast, darkContrast } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(roleNames(lightContrast), 'the light high contrast block must cover every role').toEqual(staticRootRoles())
    expect(roleNames(darkContrast), 'the dark high contrast block must cover every role').toEqual(staticRootRoles())
  })

  it('writes hex values for every role', () => {
    const { light } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    for (const [name, value] of Object.entries(light).filter(([name]) => name.startsWith('md-sys-color-'))) {
      expect(value, `--${name} must be a hex colour`).toMatch(/^#[0-9a-f]{6}$/)
    }
  })

  it('keeps scrim and shadow black in both themes', () => {
    const blocks = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(blocks.light['md-sys-color-scrim'], 'the scrim is black, whatever the seed').toBe('#000000')
    expect(blocks.dark['md-sys-color-shadow'], 'the shadow is black, whatever the seed').toBe('#000000')
  })

  it('makes the high contrast on-surface lighter than the standard one in the dark theme', () => {
    const blocks = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(blocks.darkContrast['md-sys-color-on-surface'], 'contrast 1.0 pushes text to the extreme').toBe('#ffffff')
  })
})

describe('buildThemeCss overrides', () => {
  it('replaces the primary role with the tonal palette of the override', () => {
    const css = buildThemeCss(withSettings({ seedColor: SEED, primaryColor: OVERRIDE }))
    const palette = TonalPalette.fromInt(argbFromHex(OVERRIDE))
    const { light, dark } = blocksOf(css)

    expect(light['md-sys-color-primary'], 'light primary is tone 40 of the override palette').toBe(hexFromArgb(palette.tone(40)))
    expect(dark['md-sys-color-primary'], 'dark primary is tone 80 of the override palette').toBe(hexFromArgb(palette.tone(80)))
  })

  it('does not leave the seed-derived primary in place when a primary override is set', () => {
    const seedOnly = blocksOf(buildThemeCss(withSettings({ seedColor: SEED }))).light['md-sys-color-primary']
    const overridden = blocksOf(buildThemeCss(withSettings({ seedColor: SEED, primaryColor: OVERRIDE }))).light['md-sys-color-primary']

    expect(overridden, 'the override must change the primary, not only the seed').not.toBe(seedOnly)
  })

  it('takes the error role from the error override', () => {
    const { light } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED, errorColor: '#b41340' })))
    const expected = hexFromArgb(TonalPalette.fromInt(argbFromHex('#b41340')).tone(40))

    expect(light['md-sys-color-error'], 'light error is tone 40 of the error override').toBe(expected)
  })

  it('reads a three digit hex as its six digit form, in any case', () => {
    const short = buildThemeCss(withSettings({ seedColor: '#06A', primaryColor: '#E08' }))
    const long = buildThemeCss(withSettings({ seedColor: '#0066aa', primaryColor: '#ee0088' }))

    expect(short, '#06A is #0066aa and #E08 is #ee0088').toBe(long)
  })
})

describe('invalid colours', () => {
  it('ignores an invalid override and warns', () => {
    const warn = warnings()
    const seedOnly = blocksOf(buildThemeCss(withSettings({ seedColor: SEED }))).light['md-sys-color-primary']
    const css = buildThemeCss(withSettings({ seedColor: SEED, primaryColor: 'hotpink' }))

    expect(blocksOf(css).light['md-sys-color-primary'], 'the invalid field falls back to the seed-derived primary').toBe(seedOnly)
    expect(warn, 'the operator must be told which field was ignored').toHaveBeenCalledWith(expect.stringContaining('primaryColor'))
  })

  it('treats an invalid seed as no seed and emits nothing', () => {
    const warn = warnings()

    expect(buildThemeCss(withSettings({ seedColor: '#12345' })), 'a five digit value is not a colour').toBe('')
    expect(warn, 'the invalid seed is reported').toHaveBeenCalledWith(expect.stringContaining('seedColor'))
  })

  it('does not warn for empty fields', () => {
    const warn = warnings()

    normalizeAppearance(SETTINGS)

    expect(warn, 'an empty field means not set and is not an error').not.toHaveBeenCalled()
  })

  it('rejects a value that is not a string instead of throwing', () => {
    warnings()

    expect(() => buildThemeCss(withSettings({ seedColor: 12345 })), 'a number in the JSON must not break the page').not.toThrow()
  })
})

describe('schemeVariant', () => {
  it('maps every settings name to the matching MD3 variant', () => {
    expect(SCHEME_VARIANTS, 'the enum has exactly these seven values').toEqual([
      'MONOCHROME', 'NEUTRAL', 'TONAL_SPOT', 'VIBRANT', 'EXPRESSIVE', 'FIDELITY', 'CONTENT'
    ])
    for (const name of SCHEME_VARIANTS) {
      const scheme = new (schemeClassFor(name))(Hct.fromInt(argbFromHex(SEED)), false, 0)
      expect(scheme.variant, `${name} must build the ${name} scheme`).toBe(Variant[name])
    }
  })

  it('falls back to VIBRANT for an unknown name and warns', () => {
    const warn = warnings()

    expect(normalizeAppearance(withSettings({ schemeVariant: 'RAINBOW' })).schemeVariant).toBe('VIBRANT')
    expect(warn, 'an unknown variant is reported').toHaveBeenCalledWith(expect.stringContaining('schemeVariant'))
  })

  it('defaults to VIBRANT when the field is empty', () => {
    expect(normalizeAppearance(withSettings({ schemeVariant: '' })).schemeVariant).toBe('VIBRANT')
  })

  it('makes the monochrome variant produce a grey primary', () => {
    const { light } = blocksOf(buildThemeCss(withSettings({ seedColor: OVERRIDE, schemeVariant: 'MONOCHROME' })))
    const hex = light['md-sys-color-primary']
    const [r, g, b] = [1, 3, 5].map(index => parseInt(hex.slice(index, index + 2), 16))

    expect(Math.max(r, g, b) - Math.min(r, g, b), 'monochrome has no chroma').toBeLessThanOrEqual(2)
  })
})

describe('chart palette and brand gradient', () => {
  it('derives chart series 1 to 3 from primary, tertiary and secondary', () => {
    const { light, dark } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(light['md-ext-color-chart-1'], 'series 1 is the primary').toBe(light['md-sys-color-primary'])
    expect(light['md-ext-color-chart-2'], 'series 2 is the tertiary').toBe(light['md-sys-color-tertiary'])
    expect(light['md-ext-color-chart-3'], 'series 3 is the secondary').toBe(light['md-sys-color-secondary'])
    expect(dark['md-ext-color-chart-1'], 'the dark series 1 is the dark primary').toBe(dark['md-sys-color-primary'])
  })

  it('leaves chart series 4 to 8 on their static values', () => {
    const { light } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(Object.keys(light).filter(name => /^md-ext-color-chart-[4-8]$/.test(name)), 'only the first three series are derived').toEqual([])
  })

  it('draws the brand gradient from primary to tertiary', () => {
    const { light } = blocksOf(buildThemeCss(withSettings({ seedColor: SEED })))

    expect(light['olf-gradient'], 'the gradient is a custom property with the seed roles').toBe(
      `linear-gradient(135deg, ${light['md-sys-color-primary']} 0%, ${light['md-sys-color-tertiary']} 100%)`
    )
  })

  it('does not carry the OLF stops into a custom seed', () => {
    const css = buildThemeCss(withSettings({ seedColor: SEED }))

    expect(css, 'the OLF orange stop belongs to the default theme only').not.toContain('#f7931d')
  })
})

describe('logoFor', () => {
  const appearance = { logoDark: 'https://example.org/logo-dark.svg' }

  it('uses the dark logo in the dark theme when one is set', () => {
    expect(logoFor(appearance, 'https://example.org/logo.svg', true)).toBe('https://example.org/logo-dark.svg')
  })

  it('keeps the organisation logo in the light theme', () => {
    expect(logoFor(appearance, 'https://example.org/logo.svg', false)).toBe('https://example.org/logo.svg')
  })

  it('keeps the organisation logo in the dark theme when no dark logo is set', () => {
    expect(logoFor({ logoDark: '' }, 'https://example.org/logo.svg', true)).toBe('https://example.org/logo.svg')
  })
})

describe('applyTheme', () => {
  it('inserts a single style element with the generated css', () => {
    const doc = freshDocument()

    applyTheme(withSettings({ seedColor: SEED }), doc)

    const styles = doc.querySelectorAll(`style#${THEME_STYLE_ID}`)
    expect(styles, 'exactly one theme style element').toHaveLength(1)
    expect(styles[0].textContent, 'the element carries the generated primary').toContain('--md-sys-color-primary')
  })

  it('replaces the style element when the seed changes', () => {
    const doc = freshDocument()

    applyTheme(withSettings({ seedColor: SEED }), doc)
    applyTheme(withSettings({ seedColor: OVERRIDE }), doc)

    const styles = doc.querySelectorAll(`style#${THEME_STYLE_ID}`)
    expect(styles, 'a second apply replaces rather than adds').toHaveLength(1)
    expect(styles[0].textContent, 'the new seed is in effect').toBe(buildThemeCss(withSettings({ seedColor: OVERRIDE })))
  })

  it('injects nothing when no seed is set', () => {
    const doc = freshDocument()

    applyTheme(SETTINGS, doc)

    expect(doc.getElementById(THEME_STYLE_ID), 'the default theme adds no style element').toBeNull()
  })

  it('removes a previously injected style when the seed is cleared', () => {
    const doc = freshDocument()

    applyTheme(withSettings({ seedColor: SEED }), doc)
    applyTheme(SETTINGS, doc)

    expect(doc.getElementById(THEME_STYLE_ID), 'clearing the seed restores the static theme').toBeNull()
  })

  it('updates an existing theme-color meta and leaves the document otherwise alone', () => {
    const doc = freshDocument()
    const meta = doc.createElement('meta')
    meta.setAttribute('name', 'theme-color')
    meta.setAttribute('content', '#000000')
    doc.head.append(meta)

    applyTheme(withSettings({ seedColor: SEED }), doc)

    expect(meta.getAttribute('content'), 'the browser chrome follows the light surface').toMatch(/^#[0-9a-f]{6}$/)
    expect(meta.getAttribute('content')).not.toBe('#000000')
  })

  it('does not create a theme-color meta that the page did not have', () => {
    const doc = freshDocument()

    applyTheme(withSettings({ seedColor: SEED }), doc)

    expect(doc.querySelector('meta[name="theme-color"]'), 'the meta is only updated, never added').toBeNull()
  })
})

describe('applyFavicon', () => {
  it('replaces the href of the existing icon link', () => {
    const doc = freshDocument()
    const link = doc.createElement('link')
    link.setAttribute('rel', 'icon')
    link.setAttribute('href', 'favicon.png')
    doc.head.append(link)

    applyFavicon('https://example.org/icon.svg', doc)

    expect(link.getAttribute('href'), 'the operator icon replaces the bundled one').toBe('https://example.org/icon.svg')
    expect(doc.querySelectorAll('link[rel="icon"]'), 'no second icon link is added').toHaveLength(1)
  })

  it('adds an icon link when the page has none', () => {
    const doc = freshDocument()

    applyFavicon('https://example.org/icon.svg', doc)

    expect(doc.querySelector('link[rel="icon"]')?.getAttribute('href')).toBe('https://example.org/icon.svg')
  })

  it('leaves the icon alone when no favicon is set', () => {
    const doc = freshDocument()
    const link = doc.createElement('link')
    link.setAttribute('rel', 'icon')
    link.setAttribute('href', 'favicon.png')
    doc.head.append(link)

    applyFavicon('', doc)

    expect(link.getAttribute('href')).toBe('favicon.png')
  })
})

describe('devSeedFromSearch', () => {
  it('reads the seed from the theme-seed query parameter', () => {
    expect(devSeedFromSearch('?theme-seed=%23006A6A')).toBe('#006a6a')
  })

  it('returns nothing when the parameter is absent', () => {
    expect(devSeedFromSearch('?other=1')).toBe('')
  })

  it('returns nothing for a value that is not a colour, and reports it', () => {
    const warn = warnings()

    expect(devSeedFromSearch('?theme-seed=nope')).toBe('')
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('theme-seed'))
  })
})

describe('normalizeAppearance', () => {
  it('lower cases and expands valid hex', () => {
    expect(normalizeAppearance(withSettings({ seedColor: '#ABC' })).seedColor).toBe('#aabbcc')
  })

  it('passes footer text through unchanged, as plain text', () => {
    expect(normalizeAppearance(withSettings({ footerText: '<b>Hosted</b>' })).footerText).toBe('<b>Hosted</b>')
  })

  it('tolerates a missing appearance block', () => {
    expect(normalizeAppearance(undefined).schemeVariant).toBe('VIBRANT')
  })
})

describe('hex validation', () => {
  it('rejects an eight digit value, because the alpha channel has no role here', () => {
    warnings()

    expect(normalizeAppearance(withSettings({ primaryColor: '#ec008bff' })).primaryColor).toBe('')
  })
})
