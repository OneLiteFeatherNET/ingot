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
import {
  MAX_TAGS,
  validateTag,
  parseSearch,
  matchesSearch,
  filterRepositories,
  tagUsage,
  tagsInUse
} from './repositories'

const repositories = [
  { id: 'snapshots', visibility: 'PUBLIC', description: 'Nightly builds', tags: ['team-core', 'ci'] },
  { id: 'releases', visibility: 'PUBLIC', description: 'Released artifacts', tags: ['team-core', 'public'] },
  { id: 'private', visibility: 'PRIVATE', description: 'Internal libraries', tags: ['internal'] },
  { id: 'archive', visibility: 'HIDDEN', description: '', tags: [] }
]

describe('validateTag', () => {
  it('accepts a lower case tag with letters, digits and hyphens', () => {
    expect(validateTag('team-core-2', [])).toBe('')
  })

  it('accepts a tag of exactly 32 characters', () => {
    expect(validateTag('a'.repeat(32), [])).toBe('')
  })

  it('refuses a tag of 33 characters', () => {
    expect(validateTag('a'.repeat(33), [])).not.toBe('')
  })

  it('refuses upper case letters, so the stored form is the typed form', () => {
    expect(validateTag('Team', [])).not.toBe('')
  })

  it('refuses spaces and other punctuation', () => {
    expect(validateTag('team core', [])).not.toBe('')
    expect(validateTag('team_core', [])).not.toBe('')
  })

  it('refuses a tag that starts with a hyphen', () => {
    expect(validateTag('-team', [])).not.toBe('')
  })

  it('names a tag that is already added, so the duplicate is not stored twice', () => {
    expect(validateTag('ci', ['team-core', 'ci'])).toBe('This tag is already added.')
  })

  it('refuses an eleventh tag, the limit of the contract', () => {
    const full = Array.from({ length: MAX_TAGS }, (_, index) => `tag-${index}`)

    expect(validateTag('one-more', full)).toBe(`A repository takes at most ${MAX_TAGS} tags.`)
  })

  it('asks for a tag when the text is empty', () => {
    expect(validateTag('', [])).toBe('Enter a tag.')
  })
})

describe('parseSearch', () => {
  it('reads plain text as the text to match and no tag', () => {
    expect(parseSearch('  snap ')).toEqual({ text: 'snap', tags: [] })
  })

  it('reads a tag: token as a tag, in lower case', () => {
    expect(parseSearch('tag:Team-Core')).toEqual({ text: '', tags: ['team-core'] })
  })

  it('separates the tag tokens from the text around them', () => {
    expect(parseSearch('tag:ci nightly tag:team-core')).toEqual({ text: 'nightly', tags: ['ci', 'team-core'] })
  })

  it('ignores a tag: token without a value', () => {
    expect(parseSearch('tag: releases')).toEqual({ text: 'releases', tags: [] })
  })
})

describe('matchesSearch', () => {
  const item = repositories[0]

  it('matches everything when there is no criterion', () => {
    expect(matchesSearch(item, { text: '', tags: [] })).toBe(true)
  })

  it('matches the id without regard to case', () => {
    expect(matchesSearch(item, { text: 'SNAP', tags: [] })).toBe(true)
  })

  it('matches the description', () => {
    expect(matchesSearch(item, { text: 'nightly', tags: [] })).toBe(true)
  })

  it('matches a part of a tag as text', () => {
    expect(matchesSearch(item, { text: 'core', tags: [] })).toBe(true)
  })

  it('does not match text that is in none of the three fields', () => {
    expect(matchesSearch(item, { text: 'releases', tags: [] })).toBe(false)
  })

  it('matches a tag only as a whole, so tag:team does not find team-core', () => {
    expect(matchesSearch(item, { text: '', tags: ['team'] })).toBe(false)
    expect(matchesSearch(item, { text: '', tags: ['team-core'] })).toBe(true)
  })

  it('requires every tag of the criteria, not any of them', () => {
    expect(matchesSearch(item, { text: '', tags: ['team-core', 'ci'] })).toBe(true)
    expect(matchesSearch(item, { text: '', tags: ['team-core', 'internal'] })).toBe(false)
  })

  it('treats an entry without a description or tags as having none', () => {
    expect(matchesSearch({ id: 'bare' }, { text: 'bare', tags: [] })).toBe(true)
    expect(matchesSearch({ id: 'bare' }, { text: '', tags: ['ci'] })).toBe(false)
  })
})

describe('filterRepositories', () => {
  it('returns the repositories sorted by id, as the endpoint does', () => {
    expect(filterRepositories(repositories, {}).map(item => item.id)).toEqual(['archive', 'private', 'releases', 'snapshots'])
  })

  it('keeps only the repositories that match the text and every tag', () => {
    const matches = filterRepositories(repositories, { text: 'builds', tags: ['team-core'] })

    expect(matches.map(item => item.id)).toEqual(['snapshots'])
  })

  it('returns an empty list when nothing matches', () => {
    expect(filterRepositories(repositories, { text: 'nothing', tags: [] })).toEqual([])
  })

  it('does not change the list it is given', () => {
    const before = repositories.map(item => item.id)

    filterRepositories(repositories, {})

    expect(repositories.map(item => item.id)).toEqual(before)
  })
})

describe('tagUsage', () => {
  it('counts each tag once per repository, most used first, then by name', () => {
    expect(tagUsage(repositories)).toEqual([
      { tag: 'team-core', count: 2 },
      { tag: 'ci', count: 1 },
      { tag: 'internal', count: 1 },
      { tag: 'public', count: 1 }
    ])
  })

  it('is empty when no repository has a tag', () => {
    expect(tagUsage([{ id: 'bare' }])).toEqual([])
  })
})

describe('tagsInUse', () => {
  it('lists every tag once, sorted by name', () => {
    expect(tagsInUse(repositories)).toEqual(['ci', 'internal', 'public', 'team-core'])
  })
})
