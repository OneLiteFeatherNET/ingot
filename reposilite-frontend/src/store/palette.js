/*
 * Copyright (c) 2023 dzikoysk
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
 * Colours for everything the dashboard plots, read from the Material Design 3 roles and the
 * chart tokens.
 *
 * Canvas cannot resolve a `var(...)` reference, so the charts need the concrete value of
 * each role. It is read from the root element on every call, which means a theme switch
 * (which rewrites the role values under `.dark`) is picked up by the next render, with no
 * second copy of the scheme to keep in step.
 *
 * The categorical order is part of the data: the first series takes the first chart token.
 * The chart tokens are a dedicated set of eight hues, harmonised toward the seed, and are
 * not derived from the MD3 roles: the roles of a copper seed sit too close together to tell
 * series apart. See DESIGN.md, the chart section.
 */

const FALLBACK = 'CanvasText'

const readVariable = (name) => {
  if (typeof document === 'undefined') return FALLBACK
  const root = document.documentElement
  const resolved = getComputedStyle(root).getPropertyValue(name).trim()
  const value = resolved || root.style.getPropertyValue(name).trim()
  return value || FALLBACK
}

const role = (name) => readVariable(`--md-sys-color-${name}`)

const CHART_SERIES = 8

/**
 * Reserved for state, never for a series. Each one is paired with a word in the interface
 * so the colour is never the only thing carrying the meaning. These are CSS references,
 * so a template can use them directly in a style binding, and they follow the theme on
 * their own.
 */
export const STATUS = {
  good: 'var(--md-ext-color-success)',
  warning: 'var(--md-ext-color-warning)',
  critical: 'var(--md-sys-color-error)'
}

/** Resolved colours for the categorical series, in the order they are assigned. */
export const categorical = () =>
  Array.from({ length: CHART_SERIES }, (_, index) => readVariable(`--md-ext-color-chart-${index + 1}`))

/** Axis, grid, label and tooltip colours. Deliberately recessive: the data is the ink. */
export const chrome = () => ({
  axis: role('on-surface-variant'),
  grid: role('outline-variant'),
  baseline: role('outline'),
  text: role('on-surface'),
  tooltipSurface: role('inverse-surface'),
  tooltipText: role('inverse-on-surface')
})
