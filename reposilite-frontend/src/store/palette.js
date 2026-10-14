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
 * Colours for everything the dashboard plots, read from the Material Design 3 roles.
 *
 * Canvas cannot resolve a `var(...)` reference, so the charts need the concrete value of
 * each role. It is read from the root element on every call, which means a theme switch
 * (which rewrites the role values under `.dark`) is picked up by the next render, with no
 * second copy of the scheme to keep in step.
 *
 * The categorical order is part of the data: the first series takes the first role. The
 * roles lead with the three seed-derived hues (primary, tertiary, secondary) and then step
 * into their on-container tones, which stay distinct from one another in both themes.
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

const CATEGORICAL_ROLES = [
  'primary',
  'tertiary',
  'secondary',
  'on-primary-container',
  'on-tertiary-container',
  'on-secondary-container',
  'outline',
  'on-surface-variant'
]

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
export const categorical = () => CATEGORICAL_ROLES.map(role)

/** Axis, grid, label and tooltip colours. Deliberately recessive: the data is the ink. */
export const chrome = () => ({
  axis: role('on-surface-variant'),
  grid: role('outline-variant'),
  baseline: role('outline'),
  text: role('on-surface'),
  tooltipSurface: role('inverse-surface'),
  tooltipText: role('inverse-on-surface')
})
