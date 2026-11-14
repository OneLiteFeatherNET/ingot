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
 * Turns one console line into segments of plain text, each with a list of style classes.
 *
 * No HTML is generated. The view renders the text with interpolation, which escapes & < > " '
 * itself, and every class comes from the fixed table below, so nothing from the log can end up
 * in markup or in an attribute.
 */

export const COLOUR_CLASSES = {
  30: 'text-gray-900 dark:text-gray-500',
  31: 'text-red-700 dark:text-red-400',
  32: 'text-green-700 dark:text-green-400',
  33: 'text-yellow-700 dark:text-yellow-400',
  34: 'text-blue-700 dark:text-blue-400',
  35: 'text-fuchsia-700 dark:text-fuchsia-400',
  36: 'text-cyan-700 dark:text-cyan-400',
  37: 'text-gray-700 dark:text-gray-300',
  90: 'text-gray-500',
  91: 'text-red-600 dark:text-red-300',
  92: 'text-green-600 dark:text-green-300',
  93: 'text-yellow-600 dark:text-yellow-300',
  94: 'text-blue-600 dark:text-blue-300',
  95: 'text-fuchsia-600 dark:text-fuchsia-300',
  96: 'text-cyan-600 dark:text-cyan-300',
  97: 'text-gray-900 dark:text-white'
}

const ESC = '\u001b'
const CONTROL_SEQUENCE = new RegExp(`${ESC}\\[([0-9;]*)([A-Za-z])`, 'g')

const applySgr = (style, params) => {
  const codes = params === '' ? [0] : params.split(';').map(Number)
  for (let i = 0; i < codes.length; i++) {
    const code = codes[i]
    if (code === 0) {
      style.bold = false
      style.underline = false
      style.colour = null
    } else if (code === 1) {
      style.bold = true
    } else if (code === 22) {
      style.bold = false
    } else if (code === 4) {
      style.underline = true
    } else if (code === 24) {
      style.underline = false
    } else if (code === 39) {
      style.colour = null
    } else if (code in COLOUR_CLASSES) {
      style.colour = code
    } else if (code === 38 || code === 48) {
      // Extended colours (5;n or 2;r;g;b) are not supported; skip their parameters.
      if (codes[i + 1] === 5) i += 2
      else if (codes[i + 1] === 2) i += 4
    }
  }
}

const classesOf = style => [
  ...(style.bold ? ['font-bold'] : []),
  ...(style.underline ? ['underline'] : []),
  ...(style.colour === null ? [] : [COLOUR_CLASSES[style.colour]])
]

export function parseAnsi(message) {
  const segments = []
  const style = { bold: false, underline: false, colour: null }
  let position = 0

  const pushText = text => {
    if (text.length > 0) segments.push({ text, classes: classesOf(style) })
  }

  for (const match of message.matchAll(CONTROL_SEQUENCE)) {
    pushText(message.slice(position, match.index))
    if (match[2] === 'm') applySgr(style, match[1])
    position = match.index + match[0].length
  }
  pushText(message.slice(position))

  return segments
}
