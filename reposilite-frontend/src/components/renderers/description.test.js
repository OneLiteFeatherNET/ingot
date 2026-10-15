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
import { splitDescription, descriptionText } from './description.js'

describe('splitDescription', () => {
  it('returns a plain description as a single line', () => {
    expect(splitDescription('The name of the bucket.')).toEqual(['The name of the bucket.'])
  })

  it('splits on a self-closing br tag', () => {
    expect(splitDescription('First<br/>Second')).toEqual(['First', 'Second'])
  })

  it('splits on a br tag with a space before the slash', () => {
    expect(splitDescription('First<br />Second')).toEqual(['First', 'Second'])
  })

  it('splits on an open br tag and ignores its case', () => {
    expect(splitDescription('First<BR>Second<Br>Third')).toEqual(['First', 'Second', 'Third'])
  })

  it('trims the lines and drops the empty ones', () => {
    expect(splitDescription('Examples: <br/> HTTP 127.0.0.1:1081 <br/><br/> SOCKS 127.0.0.1:1080 ')).toEqual([
      'Examples:',
      'HTTP 127.0.0.1:1081',
      'SOCKS 127.0.0.1:1080'
    ])
  })

  it('keeps html-like text as literal text rather than markup', () => {
    expect(splitDescription('Use <b>bold</b> <br/> done')).toEqual(['Use <b>bold</b>', 'done'])
  })

  it('returns no lines for a missing or blank description', () => {
    expect(splitDescription(undefined)).toEqual([])
    expect(splitDescription(null)).toEqual([])
    expect(splitDescription('  <br/> ')).toEqual([])
  })
})

describe('descriptionText', () => {
  it('joins the lines with a newline for a pre-line element', () => {
    expect(descriptionText('Examples: <br/> HTTP 127.0.0.1:1081')).toBe('Examples:\nHTTP 127.0.0.1:1081')
  })

  it('returns an empty string for a missing description', () => {
    expect(descriptionText(undefined)).toBe('')
  })
})
