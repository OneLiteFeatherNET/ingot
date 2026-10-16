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

import { describe, expect, it } from 'vitest'
import { COLOUR_CLASSES, parseAnsi } from './ansi.js'

const ESC = '\u001b'

describe('parseAnsi', () => {
  it('keeps an img tag payload as literal text', () => {
    const segments = parseAnsi('<img src=x onerror=alert(1)>')

    expect(segments).toEqual([{ text: '<img src=x onerror=alert(1)>', classes: [] }])
  })

  it('keeps an already escaped entity as literal text', () => {
    const segments = parseAnsi('&amp;')

    expect(segments).toEqual([{ text: '&amp;', classes: [] }])
  })

  it('keeps a double quote that tries to close an attribute as text', () => {
    const segments = parseAnsi('" onmouseover="alert(1)')

    expect(segments).toEqual([{ text: '" onmouseover="alert(1)', classes: [] }])
  })

  it('keeps a single quote as text', () => {
    const segments = parseAnsi("it's")

    expect(segments).toEqual([{ text: "it's", classes: [] }])
  })

  it('splits a line into coloured and plain segments at an SGR colour code', () => {
    const segments = parseAnsi(`${ESC}[31mERROR${ESC}[0m done`)

    expect(segments).toEqual([
      { text: 'ERROR', classes: [COLOUR_CLASSES[31]] },
      { text: ' done', classes: [] }
    ])
  })

  it('combines bold and colour in a single segment', () => {
    const segments = parseAnsi(`${ESC}[1;32mok`)

    expect(segments).toEqual([{ text: 'ok', classes: ['font-bold', COLOUR_CLASSES[32]] }])
  })

  it('treats a bare reset code as ending the colour', () => {
    const segments = parseAnsi(`${ESC}[34mA${ESC}[mB`)

    expect(segments).toEqual([
      { text: 'A', classes: [COLOUR_CLASSES[34]] },
      { text: 'B', classes: [] }
    ])
  })

  it('drops escape sequences that are not colour or style codes', () => {
    const segments = parseAnsi(`${ESC}[2Kx`)

    expect(segments).toEqual([{ text: 'x', classes: [] }])
  })

  it('returns no segments for an empty line', () => {
    expect(parseAnsi('')).toEqual([])
  })
})
