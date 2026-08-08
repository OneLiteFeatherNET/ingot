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

/**
 * Colours for everything the dashboard plots.
 *
 * The two categorical ramps are not a light set and a free-hand dark set: they are the
 * same eight hues, each stepped for the surface it is drawn on. Both were checked against
 * the surfaces the charts actually use (white cards on #f5f5f5, gray-900 cards on black)
 * for the lightness band, a chroma floor, colour-vision separation between neighbouring
 * slots, and contrast. Reordering the slots or dropping one in changes those results, so
 * treat the order as part of the data and re-validate before touching it.
 *
 * In light mode aqua, yellow and magenta sit below 3:1 against a white card. That is
 * allowed only while every series carries a visible label, which is why the multi-series
 * chart always draws its legend.
 */
const CATEGORICAL_LIGHT = [
  '#2a78d6', // blue
  '#eb6834', // orange
  '#1baf7a', // aqua
  '#eda100', // yellow
  '#e87ba4', // magenta
  '#008300', // green
  '#4a3aa7', // violet
  '#e34948'  // red
]

const CATEGORICAL_DARK = [
  '#3987e5',
  '#d95926',
  '#199e70',
  '#c98500',
  '#d55181',
  '#008300',
  '#9085e9',
  '#e66767'
]

/**
 * Reserved for state, never for a series. Each one is paired with a word in the interface
 * so the colour is never the only thing carrying the meaning.
 */
export const STATUS = {
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b'
}

/** Axis, grid and label colours. Deliberately recessive: the data is the ink. */
const CHROME_LIGHT = {
  axis: '#898781',
  grid: '#e1e0d9',
  baseline: '#c3c2b7',
  text: '#52514e'
}

const CHROME_DARK = {
  axis: '#898781',
  grid: '#2c2c2a',
  baseline: '#383835',
  text: '#c3c2b7'
}

export const categorical = (isDark) => (isDark ? CATEGORICAL_DARK : CATEGORICAL_LIGHT)

export const chrome = (isDark) => (isDark ? CHROME_DARK : CHROME_LIGHT)
