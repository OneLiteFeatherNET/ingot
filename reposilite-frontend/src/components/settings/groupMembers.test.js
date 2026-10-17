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
import { addMember, choosableMembers, moveMember, referenceOf, removeMemberAt } from './groupMembers'

const member = (reference, extra = {}) => ({ reference, ...extra })

describe('the reference of a mirrored entry', () => {
  it('reads the repository id of an entry, and nothing for an entry without one', () => {
    expect(referenceOf(member('releases'))).toBe('releases')
    expect(referenceOf({})).toBe('')
    expect(referenceOf(undefined)).toBe('')
  })
})

describe('changing the members of a group', () => {
  it('adds a repository at the end as a bare reference, and keeps the entries that were there', () => {
    const releases = member('releases', { store: true, authorization: { method: 'LOOPBACK_LINK', login: 'mirror', password: '********' } })

    const next = addMember([releases], 'snapshots')

    expect(next).toEqual([member('releases', { store: true, authorization: { method: 'LOOPBACK_LINK', login: 'mirror', password: '********' } }), { reference: 'snapshots' }])
    expect(next[0]).toBe(releases)
  })

  it('adds nothing for an empty choice or a repository already listed', () => {
    const entries = [member('releases')]

    expect(addMember(entries, '')).toBe(entries)
    expect(addMember(entries, 'releases')).toBe(entries)
  })

  it('removes the entry at the place given and keeps the others as they are', () => {
    const all = member('all', { authorization: { login: 'all' } })
    const snapshots = member('snapshots', { authorization: { login: 'snapshots' } })

    const next = removeMemberAt([member('releases'), all, snapshots], 0)

    expect(next).toEqual([all, snapshots])
    expect(next[0]).toBe(all)
    expect(next[1]).toBe(snapshots)
  })

  it('removes only the entry at the place given, when two entries name the same repository', () => {
    const first = member('releases', { authorization: { login: 'first' } })
    const second = member('releases', { authorization: { login: 'second' } })

    const next = removeMemberAt([first, second], 1)

    expect(next).toEqual([first])
    expect(next[0]).toBe(first)
  })

  it('does not change the list it is given when it removes or moves', () => {
    const list = [member('a'), member('b')]

    removeMemberAt(list, 0)
    moveMember(list, 0, 1)

    expect(list).toEqual([member('a'), member('b')])
  })

  it('moves an entry up or down by one place, with its settings', () => {
    const a = member('a', { store: true })
    const b = member('b')
    const c = member('c')

    expect(moveMember([a, b, c], 2, -1)).toEqual([a, c, b])
    expect(moveMember([a, b, c], 0, 1)).toEqual([b, a, c])
    expect(moveMember([a, b, c], 0, 1)[1]).toBe(a)
  })

  it('does not move past the first or the last place', () => {
    const list = [member('a'), member('b')]

    expect(moveMember(list, 0, -1)).toBe(list)
    expect(moveMember(list, 1, 1)).toBe(list)
  })

  it('offers the repositories that are not members yet, in their order', () => {
    expect(choosableMembers(['all', 'releases', 'snapshots'], [member('releases')])).toEqual(['all', 'snapshots'])
  })
})
