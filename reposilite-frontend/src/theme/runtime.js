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

/*
 * Generates the operator's MD3 scheme from a seed colour and writes it into the page.
 *
 * With a seed, this emits one <style id="ingot-theme"> with the same selector structure as the
 * static file in src/styles/md3/color.css: :root for light, .dark for dark, and the
 * prefers-contrast block for the high contrast pair. The static file loads first, so the
 * injected rules win at equal specificity, and the high contrast rules are repeated in full for
 * the same reason. Without a seed nothing is generated at all.
 *
 * This module imports @material/material-color-utilities, which is around 21 KB gzipped. It is
 * therefore only loaded by appearance.js when a seed is set; the validation helpers live in
 * settings.js, which the default theme loads.
 */

import {
  DynamicScheme,
  Hct,
  TonalPalette,
  SchemeMonochrome,
  SchemeNeutral,
  SchemeTonalSpot,
  SchemeVibrant,
  SchemeExpressive,
  SchemeFidelity,
  SchemeContent,
  argbFromHex,
  hexFromArgb
} from '@material/material-color-utilities'
import { THEME_STYLE_ID, normalizeAppearance } from './settings'

export {
  THEME_STYLE_ID,
  SCHEME_VARIANTS,
  normalizeAppearance,
  normalizeHex,
  logoFor,
  applyFavicon,
  devSeedFromSearch
} from './settings'

// The settings names map onto the library's schemes one for one. Each class carries its variant.
const SCHEME_CLASSES = Object.freeze({
  MONOCHROME: SchemeMonochrome,
  NEUTRAL: SchemeNeutral,
  TONAL_SPOT: SchemeTonalSpot,
  VIBRANT: SchemeVibrant,
  EXPRESSIVE: SchemeExpressive,
  FIDELITY: SchemeFidelity,
  CONTENT: SchemeContent
})

export const schemeClassFor = (name) => SCHEME_CLASSES[name]

// Each entry is [CSS role name without the prefix, DynamicScheme getter]. The CSS names must
// match src/styles/md3/color.css exactly; runtime.test.js checks that against the file.
const ROLES = Object.freeze([
  ['primary', 'primary'],
  ['on-primary', 'onPrimary'],
  ['primary-container', 'primaryContainer'],
  ['on-primary-container', 'onPrimaryContainer'],
  ['primary-fixed', 'primaryFixed'],
  ['primary-fixed-dim', 'primaryFixedDim'],
  ['on-primary-fixed', 'onPrimaryFixed'],
  ['on-primary-fixed-variant', 'onPrimaryFixedVariant'],
  ['secondary', 'secondary'],
  ['on-secondary', 'onSecondary'],
  ['secondary-container', 'secondaryContainer'],
  ['on-secondary-container', 'onSecondaryContainer'],
  ['secondary-fixed', 'secondaryFixed'],
  ['secondary-fixed-dim', 'secondaryFixedDim'],
  ['on-secondary-fixed', 'onSecondaryFixed'],
  ['on-secondary-fixed-variant', 'onSecondaryFixedVariant'],
  ['tertiary', 'tertiary'],
  ['on-tertiary', 'onTertiary'],
  ['tertiary-container', 'tertiaryContainer'],
  ['on-tertiary-container', 'onTertiaryContainer'],
  ['tertiary-fixed', 'tertiaryFixed'],
  ['tertiary-fixed-dim', 'tertiaryFixedDim'],
  ['on-tertiary-fixed', 'onTertiaryFixed'],
  ['on-tertiary-fixed-variant', 'onTertiaryFixedVariant'],
  ['error', 'error'],
  ['on-error', 'onError'],
  ['error-container', 'errorContainer'],
  ['on-error-container', 'onErrorContainer'],
  ['background', 'background'],
  ['on-background', 'onBackground'],
  ['surface', 'surface'],
  ['on-surface', 'onSurface'],
  ['surface-variant', 'surfaceVariant'],
  ['on-surface-variant', 'onSurfaceVariant'],
  ['surface-tint', 'surfaceTint'],
  ['surface-dim', 'surfaceDim'],
  ['surface-bright', 'surfaceBright'],
  ['surface-container-lowest', 'surfaceContainerLowest'],
  ['surface-container-low', 'surfaceContainerLow'],
  ['surface-container', 'surfaceContainer'],
  ['surface-container-high', 'surfaceContainerHigh'],
  ['surface-container-highest', 'surfaceContainerHighest'],
  ['inverse-surface', 'inverseSurface'],
  ['inverse-on-surface', 'inverseOnSurface'],
  ['inverse-primary', 'inversePrimary'],
  ['outline', 'outline'],
  ['outline-variant', 'outlineVariant'],
  ['scrim', 'scrim'],
  ['shadow', 'shadow']
])

export const ROLE_NAMES = Object.freeze(ROLES.map(([name]) => name))

/*
 * The scheme for one mode and contrast level. The seed sets the scheme. A key colour replaces the
 * palette of its role, so the seed still decides the neutrals and everything a key colour does
 * not name.
 */
const schemeFor = (appearance, isDark, contrastLevel) => {
  const sourceColorHct = Hct.fromInt(argbFromHex(appearance.seedColor))
  const Scheme = schemeClassFor(appearance.schemeVariant)
  const base = new Scheme(sourceColorHct, isDark, contrastLevel)
  const palette = (hex, fallback) => (hex ? TonalPalette.fromInt(argbFromHex(hex)) : fallback)

  return new DynamicScheme({
    sourceColorHct,
    variant: base.variant,
    contrastLevel,
    isDark,
    primaryPalette: palette(appearance.primaryColor, base.primaryPalette),
    secondaryPalette: palette(appearance.secondaryColor, base.secondaryPalette),
    tertiaryPalette: palette(appearance.tertiaryColor, base.tertiaryPalette),
    neutralPalette: base.neutralPalette,
    neutralVariantPalette: base.neutralVariantPalette,
    errorPalette: palette(appearance.errorColor, base.errorPalette)
  })
}

const roleValues = (scheme) =>
  new Map(ROLES.map(([name, getter]) => [name, hexFromArgb(scheme[getter])]))

// Declarations for one block: every role, chart series 1 to 3, and the brand gradient.
// Series 1 is the primary, 2 the tertiary and 3 the secondary; the gradient runs primary to tertiary.
const declarationsFor = (scheme) => {
  const values = roleValues(scheme)
  const primary = values.get('primary')
  const secondary = values.get('secondary')
  const tertiary = values.get('tertiary')
  return [
    ...[...values].map(([name, hex]) => `--md-sys-color-${name}: ${hex};`),
    `--md-ext-color-chart-1: ${primary};`,
    `--md-ext-color-chart-2: ${tertiary};`,
    `--md-ext-color-chart-3: ${secondary};`,
    `--olf-gradient: linear-gradient(135deg, ${primary} 0%, ${tertiary} 100%);`
  ]
}

const ruleFor = (selector, scheme, indent = '') =>
  [
    `${indent}${selector} {`,
    ...declarationsFor(scheme).map(line => `${indent}  ${line}`),
    `${indent}}`
  ].join('\n')

const generate = (appearance) => {
  if (!appearance.seedColor) {
    return { css: '', lightSurface: '' }
  }

  const light = schemeFor(appearance, false, 0)
  const dark = schemeFor(appearance, true, 0)
  const lightContrast = schemeFor(appearance, false, 1)
  const darkContrast = schemeFor(appearance, true, 1)

  const css = [
    ruleFor(':root', light),
    ruleFor('.dark', dark),
    [
      '@media (prefers-contrast: more) {',
      ruleFor(':root:not(.dark)', lightContrast, '  '),
      ruleFor('.dark', darkContrast, '  '),
      '}'
    ].join('\n')
  ].join('\n\n')

  return { css, lightSurface: hexFromArgb(light.surface) }
}

/**
 * The stylesheet for an appearance block, or '' when no seed is set (the default theme).
 * Accepts the raw settings object and validates it first.
 */
export const buildThemeCss = (raw) => generate(normalizeAppearance(raw)).css

/*
 * Writes the theme into the document. An empty result removes a previously injected theme, so
 * clearing the seed restores the static stylesheet. The theme-color meta is only updated when
 * the page already has one, and it takes the light surface, which is the colour of the header.
 */
export const applyTheme = (raw, doc = document) => {
  const { css, lightSurface } = generate(normalizeAppearance(raw))
  const existing = doc.getElementById(THEME_STYLE_ID)

  if (!css) {
    existing?.remove()
    return ''
  }

  const style = existing ?? doc.createElement('style')
  style.id = THEME_STYLE_ID
  style.textContent = css
  if (!existing) {
    doc.head.append(style)
  }

  doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', lightSurface)
  return css
}
