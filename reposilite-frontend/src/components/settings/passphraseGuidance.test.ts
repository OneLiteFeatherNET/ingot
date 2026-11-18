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
import { passphraseGuidance } from './passphraseGuidance'

const withLength = (length: number): string => 'a'.repeat(length)

describe('passphrase strength line', () => {
  it('reports a passphrase of eleven characters as too short', () => {
    expect(passphraseGuidance({ password: withLength(11), confirmation: withLength(11) }).label).toBe('too short')
  })

  it('reports a passphrase of twelve characters as acceptable', () => {
    expect(passphraseGuidance({ password: withLength(12), confirmation: withLength(12) }).label).toBe('acceptable')
  })

  it('reports a passphrase of fifteen characters as acceptable', () => {
    expect(passphraseGuidance({ password: withLength(15), confirmation: withLength(15) }).label).toBe('acceptable')
  })

  it('reports a passphrase of sixteen characters as long', () => {
    expect(passphraseGuidance({ password: withLength(16), confirmation: withLength(16) }).label).toBe('long')
  })

  it('shows no strength for an empty passphrase', () => {
    expect(passphraseGuidance({ password: '', confirmation: '' }).level).toBe('empty')
  })

  it('counts an emoji as one character, so eleven emoji are still too short', () => {
    const elevenEmoji = '🔑'.repeat(11)

    expect(passphraseGuidance({ password: elevenEmoji, confirmation: elevenEmoji }).label).toBe('too short')
  })
})

describe('passphrase submission', () => {
  it('does not allow a too short passphrase to be submitted', () => {
    expect(passphraseGuidance({ password: withLength(11), confirmation: withLength(11) }).submittable).toBe(false)
  })

  it('does not allow an empty passphrase to be submitted', () => {
    expect(passphraseGuidance({ password: '', confirmation: '' }).submittable).toBe(false)
  })

  it('allows an acceptable passphrase that the confirmation repeats', () => {
    expect(passphraseGuidance({ password: withLength(14), confirmation: withLength(14) }).submittable).toBe(true)
  })

  it('does not allow a passphrase whose confirmation differs', () => {
    expect(passphraseGuidance({ password: withLength(14), confirmation: withLength(13) }).submittable).toBe(false)
  })

  it('reports the mismatch of the confirmation', () => {
    expect(passphraseGuidance({ password: withLength(14), confirmation: withLength(13) }).confirmationMatches).toBe(false)
  })

  it('skips the confirmation check when no confirmation is asked, as on import', () => {
    const guidance = passphraseGuidance({ password: withLength(14), confirmation: null })

    expect(guidance.confirmationMatches).toBe(true)
    expect(guidance.submittable).toBe(true)
  })
})
