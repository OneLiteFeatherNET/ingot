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
import { mount } from '@vue/test-utils'
import {
  siAuth0,
  siAuthentik,
  siGitea,
  siGithub,
  siGitlab,
  siGoogle,
  siKeycloak,
  siOkta
} from 'simple-icons'
import ProviderIcon from './ProviderIcon.vue'
import { GENERIC_KEY_PATH, PROVIDER_ICONS, brandPathOf } from './providerIcons'

// The brand marks are the simple-icons paths (CC0), drawn on a 24 unit grid. The generic mark is the
// Material Symbols "key" that the token pages use, on the 960 unit grid.
const BRANDS = {
  GITHUB: siGithub,
  GITLAB: siGitlab,
  GOOGLE: siGoogle,
  KEYCLOAK: siKeycloak,
  AUTHENTIK: siAuthentik,
  OKTA: siOkta,
  AUTH0: siAuth0,
  GITEA: siGitea
}

const svgOf = (icon) => mount(ProviderIcon, { props: { icon } }).find('svg')
const pathOf = (icon) => svgOf(icon).find('path').attributes('d')

describe('ProviderIcon', () => {
  it.each(Object.entries(BRANDS))('draws the simple-icons mark for %s', (icon, brand) => {
    expect(pathOf(icon), `${icon} must be drawn with the brand mark, not the generic key`).toBe(brand.path)
    expect(svgOf(icon).attributes('viewBox')).toBe('0 0 24 24')
  })

  it.each(['GENERIC', 'MICROSOFT'])('draws the generic key for %s, which has no brand mark in the set', (icon) => {
    expect(pathOf(icon)).toBe(GENERIC_KEY_PATH)
    expect(svgOf(icon).attributes('viewBox')).toBe('0 -960 960 960')
  })

  it('falls back to the generic key for a value the set does not know', () => {
    expect(pathOf('SOMETHING_NEW')).toBe(GENERIC_KEY_PATH)
  })

  it('draws the generic key when no icon is given', () => {
    expect(mount(ProviderIcon).find('path').attributes('d')).toBe(GENERIC_KEY_PATH)
  })

  it('is monochrome: every mark takes the current text colour, with no brand colour in the markup', () => {
    for (const icon of PROVIDER_ICONS) {
      const svg = svgOf(icon)
      expect(svg.attributes('fill'), `${icon} takes its colour from the text`).toBe('currentColor')
      expect(svg.html(), `${icon} must not carry a hex colour`).not.toMatch(/#[0-9a-f]{6}/i)
    }
  })

  it('is hidden from assistive technology, because the button it sits in carries the name', () => {
    expect(svgOf('GITHUB').attributes('aria-hidden')).toBe('true')
  })

  it('lists every provider of the backend contract exactly once', () => {
    expect(PROVIDER_ICONS).toEqual([
      'GENERIC', 'GITHUB', 'GITLAB', 'GOOGLE', 'MICROSOFT', 'KEYCLOAK', 'AUTHENTIK', 'OKTA', 'AUTH0', 'GITEA'
    ])
  })

  it('has a brand mark for each provider that has one in simple-icons, and none for Microsoft', () => {
    expect(brandPathOf('MICROSOFT')).toBeNull()
    for (const [icon, brand] of Object.entries(BRANDS)) {
      expect(brandPathOf(icon), `${icon} resolves to its simple-icons path`).toBe(brand.path)
    }
  })
})
