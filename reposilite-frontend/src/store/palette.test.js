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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { categorical, chrome, STATUS } from './palette'

const root = () => document.documentElement

// jsdom does not load color.css, so the test plays the stylesheet's part: it sets the same
// custom properties on the root that the real theme sets, and the palette has to read them.
const setRole = (name, value) => root().style.setProperty(name, value)

const CHART_VALUES = ['#100001', '#100002', '#100003', '#100004', '#100005', '#100006', '#100007', '#100008']

describe('palette', () => {
  beforeEach(() => {
    CHART_VALUES.forEach((value, index) => setRole(`--md-ext-color-chart-${index + 1}`, value))
    setRole('--md-sys-color-on-surface', '#404040')
    setRole('--md-sys-color-on-surface-variant', '#505050')
    setRole('--md-sys-color-outline', '#606060')
    setRole('--md-sys-color-outline-variant', '#707070')
    setRole('--md-sys-color-inverse-surface', '#808080')
    setRole('--md-sys-color-inverse-on-surface', '#909090')
  })

  afterEach(() => {
    for (const name of [...root().style]) {
      if (name.startsWith('--md-')) root().style.removeProperty(name)
    }
  })

  it('returns the eight chart series in token order, chart-1 first', () => {
    expect(categorical(), 'the categorical palette should be the eight chart tokens in order').toEqual(CHART_VALUES)
  })

  it('reads one colour per series, so no two series share a value', () => {
    expect(new Set(categorical()).size, 'every series needs its own colour').toBe(8)
  })

  it('follows the chart token values when the theme changes them', () => {
    setRole('--md-ext-color-chart-1', '#abcdef')

    expect(categorical()[0], 'the palette should pick up the new chart-1 value on the next read').toBe('#abcdef')
  })

  it('does not take series colours from the MD3 roles', () => {
    setRole('--md-sys-color-primary', '#999999')

    expect(categorical(), 'a change to a role must not alter the series palette').not.toContain('#999999')
  })

  it('takes the chart axis colour from on-surface-variant', () => {
    expect(chrome().axis, 'axis labels should use the on-surface-variant role').toBe('#505050')
  })

  it('takes the grid colour from outline-variant', () => {
    expect(chrome().grid, 'grid lines should use the outline-variant role').toBe('#707070')
  })

  it('draws the tooltip on the inverse surface', () => {
    const ink = chrome()

    expect(ink.tooltipSurface, 'the tooltip surface should be the inverse-surface role').toBe('#808080')
    expect(ink.tooltipText, 'the tooltip text should be the inverse-on-surface role').toBe('#909090')
  })

  it('maps the status palette to the success, warning and error roles', () => {
    expect(STATUS.good, 'good should be the success extension colour').toBe('var(--md-ext-color-success)')
    expect(STATUS.warning, 'warning should be the warning extension colour').toBe('var(--md-ext-color-warning)')
    expect(STATUS.critical, 'critical should be the error role').toBe('var(--md-sys-color-error)')
  })
})
