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

import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MdBadge from './MdBadge.vue'

// The role classes of each tone, as the design names them (design.md D9).
const TONE_ROLES: Record<string, string[]> = {
  neutral: ['border', 'border-outline', 'text-on-surface-variant'],
  accent: ['bg-secondary-container', 'text-on-secondary-container'],
  success: ['border', 'border-outline', 'text-success'],
  error: ['bg-error-container', 'text-on-error-container'],
  hosted: ['bg-tertiary-container', 'text-on-tertiary-container'],
  proxy: ['bg-secondary-container', 'text-on-secondary-container'],
  group: ['bg-primary-container', 'text-on-primary-container'],
  mixed: ['border', 'border-outline', 'text-on-surface-variant']
}

const mountBadge = (props: Record<string, unknown> = {}, text = 'Hosted') =>
  mount(MdBadge, { props, slots: { default: () => text } })

describe('MdBadge tones', () => {
  for (const [tone, roles] of Object.entries(TONE_ROLES)) {
    it(`draws the ${tone} tone with its MD3 role classes`, () => {
      const classes = mountBadge({ tone }).classes()

      for (const role of roles) {
        expect(classes, `the ${tone} tone carries ${role}`).toContain(role)
      }
    })
  }

  it('uses the neutral tone when no tone is given', () => {
    const wrapper = mountBadge()

    expect(wrapper.attributes('data-tone')).toBe('neutral')
    expect(wrapper.classes()).toContain('text-on-surface-variant')
  })

  it('never draws a tone with a colour class outside the MD3 and status roles', () => {
    for (const tone of Object.keys(TONE_ROLES)) {
      const colourClasses = mountBadge({ tone }).classes().filter((name) => /^(text|bg|border)-/.test(name))

      for (const name of colourClasses) {
        expect(name, `the ${tone} tone uses only the roles of the catalogue`).toMatch(
          /^(text|bg|border)-(outline|on-|secondary|tertiary|primary|error|success|surface)/
        )
      }
    }
  })
})

describe('MdBadge content', () => {
  it('shows the label text, so the meaning never depends on the colour alone', () => {
    for (const tone of Object.keys(TONE_ROLES)) {
      const wrapper = mountBadge({ tone }, 'Derived')

      expect(wrapper.text(), `the ${tone} tone shows its label`).toBe('Derived')
    }
  })

  it('keeps one height for every tone', () => {
    for (const tone of Object.keys(TONE_ROLES)) {
      const classes = mountBadge({ tone }).classes()

      expect(classes, `the ${tone} tone is 24px high`).toContain('h-6')
    }
  })

  it('draws its label in the label-medium type role', () => {
    expect(mountBadge().classes()).toContain('md-label-medium')
  })
})

describe('MdBadge anchors', () => {
  it('carries the md-badge anchor on the root element', () => {
    const wrapper = mountBadge()

    expect(wrapper.attributes('data-ingot-component')).toBe('md-badge')
  })
})
