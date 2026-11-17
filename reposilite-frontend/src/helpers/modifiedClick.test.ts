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
import { isModifiedClick } from './modifiedClick'

type ClickInit = Partial<{ button: number; ctrlKey: boolean; shiftKey: boolean; metaKey: boolean; altKey: boolean }>

const click = (init: ClickInit = {}) => ({
  button: 0,
  ctrlKey: false,
  shiftKey: false,
  metaKey: false,
  altKey: false,
  ...init
})

describe('isModifiedClick', () => {
  it('treats a plain primary click as not modified', () => {
    expect(isModifiedClick(click()), 'a plain primary click is handled by the page').toBe(false)
  })

  it('treats a click with Ctrl as modified', () => {
    expect(isModifiedClick(click({ ctrlKey: true }))).toBe(true)
  })

  it('treats a click with Shift as modified', () => {
    expect(isModifiedClick(click({ shiftKey: true }))).toBe(true)
  })

  it('treats a click with Meta as modified', () => {
    expect(isModifiedClick(click({ metaKey: true }))).toBe(true)
  })

  it('treats a click with Alt as modified', () => {
    expect(isModifiedClick(click({ altKey: true }))).toBe(true)
  })

  it('treats a middle click as modified, because the browser opens a new tab for it', () => {
    expect(isModifiedClick(click({ button: 1 }))).toBe(true)
  })

  it('treats a secondary button click as modified, because the browser opens its menu for it', () => {
    expect(isModifiedClick(click({ button: 2 }))).toBe(true)
  })
})
