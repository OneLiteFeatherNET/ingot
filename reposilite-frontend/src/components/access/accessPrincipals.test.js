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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setClock, resetClock } from '../../helpers/clock'
import {
  buildCreateBody,
  detailOf,
  filterPrincipals,
  isInstanceAdmin,
  legacyGrants,
  newestLastUsed,
  seenLabel,
  usedLabel,
  validateNewPassword,
  validatePrincipalName
} from './accessPrincipals'

// The API sends an Instant as epoch seconds: the fixtures carry the same number.
const wireInstant = (iso) => Date.parse(iso) / 1000

const ALICE = { name: 'alice', displayName: 'Alice Example' }
const BOB = { name: 'bob', displayName: 'Robert Builder' }
const CI = { name: 'ci-bot', displayName: '' }

describe('filterPrincipals', () => {
  it('keeps everything for an empty query', () => {
    expect(filterPrincipals([ALICE, BOB], '')).toEqual([ALICE, BOB])
  })

  it('matches the username, ignoring case', () => {
    expect(filterPrincipals([ALICE, BOB, CI], 'CI-')).toEqual([CI])
  })

  it('matches the display name', () => {
    expect(filterPrincipals([ALICE, BOB], 'builder')).toEqual([BOB])
  })

  it('ignores the spaces around the query', () => {
    expect(filterPrincipals([ALICE, BOB], '  alice ')).toEqual([ALICE])
  })

  it('returns nothing when no name matches', () => {
    expect(filterPrincipals([ALICE, BOB], 'zed')).toEqual([])
  })
})

describe('relative labels', () => {
  beforeEach(() => {
    setClock(() => new Date(Date.UTC(2026, 9, 15, 12)))
  })

  afterEach(() => {
    resetClock()
  })

  it('names a last sign-in relative to the clock and keeps the absolute time as the tooltip', () => {
    expect(seenLabel(wireInstant('2026-10-12T12:00:00Z'))).toEqual({
      text: '3 days ago',
      title: '2026-10-12 12:00 UTC'
    })
  })

  it('says that a user who never signed in never did', () => {
    expect(seenLabel(null)).toEqual({ text: 'Never signed in', title: undefined })
  })

  it('names the last use of a machine relative to the clock', () => {
    expect(usedLabel(wireInstant('2026-10-15T09:00:00Z'))).toEqual({
      text: '3 hours ago',
      title: '2026-10-15 09:00 UTC'
    })
  })

  it('says that a machine without a used token was never used', () => {
    expect(usedLabel(null)).toEqual({ text: 'Never used', title: undefined })
  })
})

describe('newestLastUsed', () => {
  it('picks the most recent use among the tokens', () => {
    const tokens = [
      { lastUsedAt: wireInstant('2026-10-01T00:00:00Z') },
      { lastUsedAt: wireInstant('2026-10-14T00:00:00Z') },
      { lastUsedAt: null }
    ]

    expect(newestLastUsed(tokens)).toBe(wireInstant('2026-10-14T00:00:00Z'))
  })

  it('is null when no token was ever used', () => {
    expect(newestLastUsed([{ lastUsedAt: null }, {}])).toBeNull()
  })

  it('is null without tokens', () => {
    expect(newestLastUsed(undefined)).toBeNull()
  })
})

describe('validateNewPassword', () => {
  it('accepts a long enough password that is confirmed', () => {
    expect(validateNewPassword('a-long-password', 'a-long-password')).toEqual({ password: '', confirm: '' })
  })

  it('rejects a password under 12 characters', () => {
    expect(validateNewPassword('short-11-ch', 'short-11-ch').password).toBe('Use at least 12 characters')
  })

  it('accepts exactly 12 characters', () => {
    expect(validateNewPassword('twelve-chars', 'twelve-chars').password).toBe('')
  })

  it('rejects a confirmation that differs', () => {
    expect(validateNewPassword('a-long-password', 'another-password').confirm).toBe('Passwords do not match')
  })
})

describe('validatePrincipalName', () => {
  it('requires a name', () => {
    expect(validatePrincipalName('  ')).toBe('A name is required')
  })

  it('rejects a name with a colon or a slash', () => {
    expect(validatePrincipalName('a:b')).not.toBe('')
    expect(validatePrincipalName('a/b')).not.toBe('')
  })

  it('accepts an ordinary name', () => {
    expect(validatePrincipalName('ci-bot')).toBe('')
  })
})

describe('buildCreateBody', () => {
  it('builds the body of a user without display name or disabled flag', () => {
    expect(buildCreateBody({ kind: 'USER', name: ' alice ', displayName: ' ' })).toEqual({ kind: 'USER', name: 'alice' })
  })

  it('carries a display name and a disabled machine', () => {
    expect(buildCreateBody({ kind: 'MACHINE', name: 'ci-bot', displayName: 'CI bot', disabled: true }))
      .toEqual({ kind: 'MACHINE', name: 'ci-bot', displayName: 'CI bot', disabled: true })
  })
})

describe('detailOf and isInstanceAdmin', () => {
  it('reads the flat detail shape', () => {
    const detail = detailOf({ name: 'alice', teams: ['dev'], instanceRole: true, tokens: [] })

    expect(detail.name).toBe('alice')
    expect(isInstanceAdmin(detail)).toBe(true)
  })

  it('flattens a detail that nests the principal', () => {
    const detail = detailOf({ principal: { name: 'alice', disabled: true }, teams: ['dev'], instanceRole: false, tokens: [] })

    expect(detail).toMatchObject({ name: 'alice', disabled: true, teams: ['dev'] })
    expect(isInstanceAdmin(detail)).toBe(false)
  })

  it('defaults the lists of a sparse answer', () => {
    expect(detailOf({ name: 'alice' })).toMatchObject({ teams: [], tokens: [] })
  })
})

describe('legacyGrants', () => {
  it('groups the read and write permissions of a route', () => {
    const token = {
      routes: [
        { path: '/releases', permission: { identifier: 'route:read' } },
        { path: '/releases', permission: { identifier: 'route:write' } },
        { path: '/snapshots', permission: { identifier: 'route:read' } }
      ]
    }

    expect(legacyGrants(token)).toEqual([
      { path: '/releases', read: true, write: true },
      { path: '/snapshots', read: true, write: false }
    ])
  })

  it('is empty for a token without routes', () => {
    expect(legacyGrants({})).toEqual([])
  })
})
