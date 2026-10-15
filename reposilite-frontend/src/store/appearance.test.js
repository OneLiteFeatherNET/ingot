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

import { describe, it, expect, afterEach } from 'vitest'
import { setAppearance, useAppearance, applyAppearance } from './appearance'
import { THEME_STYLE_ID } from '../theme/settings'
// applyAppearance imports the colour library on first use. Loading it while the file is collected
// keeps that cold load out of the first test, which would otherwise be charged for it.
import '../theme/runtime'

// The appearance is module state, as it is in the app, so each test returns it to the defaults.
// The documents are fresh per test.
const freshDocument = () => document.implementation.createHTMLDocument('appearance')

afterEach(() => {
  setAppearance({})
})

describe('applyAppearance', () => {
  it('leaves the page on the default theme when no seed is set', async () => {
    const doc = freshDocument()
    setAppearance({ footerText: 'Hosted by OneLiteFeather' })

    await applyAppearance(doc)

    expect(doc.getElementById(THEME_STYLE_ID), 'no seed means no generated theme').toBeNull()
  })

  it('applies the generated theme when a seed is set', async () => {
    const doc = freshDocument()
    setAppearance({ seedColor: '#006a6a' })

    await applyAppearance(doc)

    expect(doc.getElementById(THEME_STYLE_ID), 'the seed generates a theme style element').not.toBeNull()
  })

  it('applies the favicon even without a seed', async () => {
    const doc = freshDocument()
    setAppearance({ favicon: 'https://example.org/icon.svg' })

    await applyAppearance(doc)

    expect(doc.querySelector('link[rel="icon"]')?.getAttribute('href')).toBe('https://example.org/icon.svg')
  })
})

describe('setAppearance', () => {
  it('keeps the default settings when the server sends none', () => {
    setAppearance(undefined)

    expect(useAppearance().seedColor, 'an older server means the default theme').toBe('')
    expect(useAppearance().schemeVariant).toBe('VIBRANT')
  })

  it('validates the settings it receives', () => {
    setAppearance({ seedColor: 'not-a-colour' })

    expect(useAppearance().seedColor, 'an invalid seed is dropped').toBe('')
  })
})

describe('applyAppearance after a change in the settings', () => {
  // The page as the server built it: a theme colour and an icon of its own.
  const builtPage = () => {
    const doc = freshDocument()
    const meta = doc.createElement('meta')
    meta.setAttribute('name', 'theme-color')
    meta.setAttribute('content', '#fff8f8')
    const icon = doc.createElement('link')
    icon.setAttribute('rel', 'icon')
    icon.setAttribute('href', '/favicon.png')
    doc.head.append(meta, icon)
    return doc
  }

  it('takes the generated theme out again when the seed is cleared', async () => {
    const doc = builtPage()
    setAppearance({ seedColor: '#006a6a' })
    await applyAppearance(doc)

    setAppearance({})
    await applyAppearance(doc)

    expect(doc.getElementById(THEME_STYLE_ID), 'clearing the seed restores the static theme').toBeNull()
  })

  it('puts the built theme colour back when the seed is cleared', async () => {
    const doc = builtPage()
    setAppearance({ seedColor: '#006a6a' })
    await applyAppearance(doc)
    expect(doc.querySelector('meta[name="theme-color"]').getAttribute('content')).not.toBe('#fff8f8')

    setAppearance({})
    await applyAppearance(doc)

    expect(doc.querySelector('meta[name="theme-color"]').getAttribute('content')).toBe('#fff8f8')
  })

  it('puts the built icon back when the favicon is cleared', async () => {
    const doc = builtPage()
    setAppearance({ favicon: 'https://example.org/icon.svg' })
    await applyAppearance(doc)

    setAppearance({})
    await applyAppearance(doc)

    expect(doc.querySelector('link[rel="icon"]').getAttribute('href')).toBe('/favicon.png')
  })

  it('replaces the theme on the page when the seed changes, without a second style element', async () => {
    const doc = builtPage()
    setAppearance({ seedColor: '#006a6a' })
    await applyAppearance(doc)

    setAppearance({ seedColor: '#8a1c1c' })
    await applyAppearance(doc)

    expect(doc.querySelectorAll(`#${THEME_STYLE_ID}`)).toHaveLength(1)
    expect(doc.getElementById(THEME_STYLE_ID).textContent).toContain('--md-sys-color-primary')
  })
})
