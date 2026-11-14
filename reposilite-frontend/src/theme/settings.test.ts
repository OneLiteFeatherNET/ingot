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
import { isColour, invalidColourFields } from './settings'

describe('isColour', () => {
  it('accepts an empty value, which means not set', () => {
    expect(isColour('')).toBe(true)
    expect(isColour(undefined)).toBe(true)
  })

  it('accepts a six digit hex colour in either case', () => {
    expect(isColour('#006a6a')).toBe(true)
    expect(isColour('#AABBCC')).toBe(true)
  })

  it('rejects a short form, a missing hash and anything that is not hex', () => {
    expect(isColour('#abc'), 'the editor asks for #RRGGBB').toBe(false)
    expect(isColour('006a6a')).toBe(false)
    expect(isColour('#00zz6a')).toBe(false)
    expect(isColour(123456)).toBe(false)
  })
})

describe('invalidColourFields', () => {
  it('names every colour field that holds something other than a six digit hex colour', () => {
    const data = {
      appearance: { seedColor: '#006a6a', primaryColor: 'teal', errorColor: '#12' },
      title: 'Not a colour'
    }

    expect(invalidColourFields(data)).toEqual(['primaryColor', 'errorColor'])
  })

  it('looks inside lists as well', () => {
    expect(invalidColourFields([{ seedColor: 'nope' }])).toEqual(['seedColor'])
  })

  it('finds nothing in a settings object that is valid or empty', () => {
    expect(invalidColourFields({ appearance: { seedColor: '#006a6a', secondaryColor: '' } })).toEqual([])
  })
})
