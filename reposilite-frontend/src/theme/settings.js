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
 * The operator's appearance settings: validation, the choice of logo, the favicon and the
 * development preview. Nothing here depends on the colour library, so the default theme pays
 * nothing for it. The scheme generator lives in runtime.js, which is loaded only when a seed is
 * set (see appearance.js).
 */

export const THEME_STYLE_ID = 'ingot-theme'

// The names the settings may use for schemeVariant. runtime.js maps each one to its scheme.
export const SCHEME_VARIANTS = Object.freeze([
  'MONOCHROME',
  'NEUTRAL',
  'TONAL_SPOT',
  'VIBRANT',
  'EXPRESSIVE',
  'FIDELITY',
  'CONTENT'
])

const DEFAULT_VARIANT = 'VIBRANT'

export const KEY_COLOURS = Object.freeze(['primaryColor', 'secondaryColor', 'tertiaryColor', 'errorColor'])

const HEX = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i

// The colour fields of the settings, by name. The settings editor draws each of them as a colour
// field, and it refuses to save a value that is not #RRGGBB or empty.
export const COLOUR_PROPERTIES = Object.freeze(['seedColor', 'primaryColor', 'secondaryColor', 'tertiaryColor', 'errorColor'])

const STRICT_HEX = /^#[0-9a-fA-F]{6}$/

/* Empty means not set. Otherwise the value must be #RRGGBB, the form the settings editor accepts. */
export const isColour = (value) =>
  value === undefined || value === null || value === '' || (typeof value === 'string' && STRICT_HEX.test(value))

/* The names of the colour fields, anywhere in a settings object, whose value is neither empty nor #RRGGBB. */
export const invalidColourFields = (data, found = []) => {
  if (Array.isArray(data)) {
    data.forEach(item => invalidColourFields(item, found))
  } else if (data && typeof data === 'object') {
    for (const [key, value] of Object.entries(data)) {
      if (COLOUR_PROPERTIES.includes(key) && !isColour(value)) {
        found.push(key)
      } else {
        invalidColourFields(value, found)
      }
    }
  }
  return found
}

/* An empty value means not set. Anything else must be a colour, or it is dropped with a warning. */
export const normalizeHex = (value, field) => {
  if (value === undefined || value === null || value === '') {
    return ''
  }
  const text = String(value).trim()
  const match = HEX.exec(text)
  if (!match) {
    console.warn(`Ignoring ${field}: "${text}" is not a hex colour such as #006a6a.`)
    return ''
  }
  const digits = match[1].toLowerCase()
  const full = digits.length === 3 ? [...digits].map(digit => digit + digit).join('') : digits
  return `#${full}`
}

const normalizeText = (value, field) => {
  if (value === undefined || value === null) {
    return ''
  }
  if (typeof value !== 'string') {
    console.warn(`Ignoring ${field}: expected text.`)
    return ''
  }
  return value
}

const normalizeVariant = (value) => {
  if (value === undefined || value === null || value === '') {
    return DEFAULT_VARIANT
  }
  if (SCHEME_VARIANTS.includes(value)) {
    return value
  }
  console.warn(`Ignoring schemeVariant: "${value}" is not one of ${SCHEME_VARIANTS.join(', ')}. Using ${DEFAULT_VARIANT}.`)
  return DEFAULT_VARIANT
}

/**
 * The appearance block with every field checked. Invalid fields come back empty (or VIBRANT for
 * the variant) and are reported, so one bad value never stops the others from applying.
 */
export const normalizeAppearance = (raw) => {
  const source = raw ?? {}
  const appearance = {
    seedColor: normalizeHex(source.seedColor, 'seedColor'),
    schemeVariant: normalizeVariant(source.schemeVariant),
    primaryColor: normalizeHex(source.primaryColor, 'primaryColor'),
    secondaryColor: normalizeHex(source.secondaryColor, 'secondaryColor'),
    tertiaryColor: normalizeHex(source.tertiaryColor, 'tertiaryColor'),
    errorColor: normalizeHex(source.errorColor, 'errorColor'),
    logoDark: normalizeText(source.logoDark, 'logoDark'),
    favicon: normalizeText(source.favicon, 'favicon'),
    footerText: normalizeText(source.footerText, 'footerText')
  }
  // A key colour replaces a role of the generated scheme, so without a seed it has nothing to act on.
  if (!appearance.seedColor && KEY_COLOURS.some(key => appearance[key])) {
    console.warn('Ignoring the key colours: they only apply together with a seedColor.')
  }
  return appearance
}

/* The dark logo is used in the dark theme when the operator set one, otherwise the logo stays. */
export const logoFor = (appearance, organizationLogo, isDark) =>
  isDark && appearance.logoDark ? appearance.logoDark : organizationLogo

/*
 * Replaces the icon of the page, or adds one when the page has none. An empty value puts back the
 * icon the page was built with, so clearing the setting in the editor takes effect at once. The
 * first href written is remembered for that.
 */
export const applyFavicon = (href, doc = document) => {
  const link = doc.querySelector('link[rel="icon"]')
  if (!href) {
    if (link?.dataset.ingotDefault !== undefined) {
      link.setAttribute('href', link.dataset.ingotDefault)
      delete link.dataset.ingotDefault
    }
    return
  }
  const target = link ?? doc.createElement('link')
  if (!link) {
    target.setAttribute('rel', 'icon')
    doc.head.append(target)
  }
  if (target.dataset.ingotDefault === undefined) {
    target.dataset.ingotDefault = target.getAttribute('href') ?? ''
  }
  target.setAttribute('href', href)
}

const THEME_COLOUR_META = 'meta[name="theme-color"]'

/* Sets the theme colour of the page, remembering the colour it had before, so removeTheme can restore it. */
export const setThemeColour = (colour, doc = document) => {
  const meta = doc.querySelector(THEME_COLOUR_META)
  if (!meta) {
    return
  }
  if (meta.dataset.ingotDefault === undefined) {
    meta.dataset.ingotDefault = meta.getAttribute('content') ?? ''
  }
  meta.setAttribute('content', colour)
}

/*
 * Takes the generated theme out of the page and restores the theme colour. This is the default
 * appearance, so it runs without loading the colour generator.
 */
export const removeTheme = (doc = document) => {
  doc.getElementById(THEME_STYLE_ID)?.remove()
  const meta = doc.querySelector(THEME_COLOUR_META)
  if (meta?.dataset.ingotDefault !== undefined) {
    meta.setAttribute('content', meta.dataset.ingotDefault)
    delete meta.dataset.ingotDefault
  }
}

/*
 * Development only: ?theme-seed=%23006a6a previews a seed without changing the server settings.
 * The caller decides whether it runs (it is gated on import.meta.env.DEV).
 */
export const devSeedFromSearch = (search = '') => {
  const value = new URLSearchParams(search).get('theme-seed')
  return value ? normalizeHex(value, 'theme-seed') : ''
}
