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

import { describe, it, expect, afterEach } from 'vitest'
import { setClock, resetClock } from '../../helpers/clock'
import {
  DEFAULT_EXPIRY,
  EXPIRY_PRESETS,
  buildCreateBody,
  expiryFor,
  formatDate,
  formatDateTime,
  formatRelative,
  validateTokenName
} from './identityTokens'

// 2026-06-15 12:00:00 UTC. The helpers must read the time only through the clock helper.
const FIXED = new Date(Date.UTC(2026, 5, 15, 12))

describe('identityTokens', () => {
  afterEach(() => resetClock())

  describe('expiry presets', () => {
    it('offers 30 days, 90 days, 365 days and never, in that order', () => {
      expect(EXPIRY_PRESETS.map(preset => preset.label)).toEqual(['30 days', '90 days', '365 days', 'Never'])
    })

    it('defaults to 90 days', () => {
      expect(DEFAULT_EXPIRY).toBe('90')
    })

    it.each([
      ['30', '2026-07-15T12:00:00.000Z'],
      ['90', '2026-09-13T12:00:00.000Z'],
      ['365', '2027-06-15T12:00:00.000Z']
    ])('maps the %s day preset to a date counted from the injected clock', (key, expected) => {
      setClock(() => FIXED)

      expect(expiryFor(key)).toBe(expected)
    })

    it('maps never to no expiry', () => {
      setClock(() => FIXED)

      expect(expiryFor('never')).toBeNull()
    })
  })

  describe('validateTokenName', () => {
    it('asks for a name', () => {
      expect(validateTokenName('   ')).toBe('Token name is required')
    })

    it('rejects a colon, which the server reserves', () => {
      expect(validateTokenName('a:b')).toBe("Token name must not contain ':'")
    })

    it('rejects a name longer than 255 characters', () => {
      expect(validateTokenName('x'.repeat(256))).toBe('Token name must be at most 255 characters')
    })

    it('accepts an ordinary name', () => {
      expect(validateTokenName('ci-bot')).toBe('')
    })
  })

  describe('buildCreateBody', () => {
    it('sends scopes as null when no box is checked', () => {
      setClock(() => FIXED)

      expect(buildCreateBody({ name: ' laptop ', description: '', preset: 'never', scopes: [] })).toEqual({
        name: 'laptop',
        expiresAt: null,
        scopes: null
      })
    })

    it('sends the description, the computed expiry and the checked scopes', () => {
      setClock(() => FIXED)

      expect(buildCreateBody({ name: 'ci', description: ' build ', preset: '30', scopes: ['READ', 'WRITE'] })).toEqual({
        name: 'ci',
        description: 'build',
        expiresAt: '2026-07-15T12:00:00.000Z',
        scopes: ['READ', 'WRITE']
      })
    })
  })

  describe('dates', () => {
    it('formats an ISO timestamp as a UTC date', () => {
      expect(formatDate('2026-10-15T23:30:00Z')).toBe('2026-10-15')
    })

    it('formats a plain date string unchanged', () => {
      expect(formatDate('2026-10-04')).toBe('2026-10-04')
    })

    it('formats the year, month and day triple of an older token', () => {
      expect(formatDate([2026, 1, 9])).toBe('2026-01-09')
    })

    it('formats a timestamp with the time of day in UTC', () => {
      expect(formatDateTime('2026-10-15T09:05:00Z')).toBe('2026-10-15 09:05 UTC')
    })
  })

  describe('formatRelative', () => {
    // 2026-06-15 12:00:00 UTC
    it.each([
      ['2026-06-15T11:59:40Z', 'just now'],
      ['2026-06-15T11:59:00Z', '1 minute ago'],
      ['2026-06-15T09:00:00Z', '3 hours ago'],
      ['2026-06-12T12:00:00Z', '3 days ago'],
      ['2026-06-14T12:00:00Z', '1 day ago'],
      ['2026-03-15T12:00:00Z', '3 months ago'],
      ['2024-06-15T12:00:00Z', '2 years ago']
    ])('counts %s from the injected clock as %s', (value, expected) => {
      setClock(() => FIXED)

      expect(formatRelative(value)).toBe(expected)
    })
  })
})
