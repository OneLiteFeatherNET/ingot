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
import { addMember, choosableMembers, membersOf, moveMember, proxiedOfMembers, removeMember } from './groupMembers'

describe('members of a group', () => {
  it('reads the repository ids from the mirrored entries, in their order', () => {
    expect(membersOf([{ reference: 'releases' }, { reference: 'snapshots' }])).toEqual(['releases', 'snapshots'])
  })

  it('reads no members when the group has no mirrored entries yet', () => {
    expect(membersOf(undefined)).toEqual([])
    expect(membersOf([])).toEqual([])
  })

  it('writes each member as a loopback reference and nothing else', () => {
    expect(proxiedOfMembers(['releases', 'snapshots'])).toEqual([{ reference: 'releases' }, { reference: 'snapshots' }])
  })
})

describe('changing the members', () => {
  it('adds a repository at the end, once', () => {
    expect(addMember(['releases'], 'snapshots')).toEqual(['releases', 'snapshots'])
    expect(addMember(['releases'], 'releases')).toEqual(['releases'])
  })

  it('ignores an empty choice', () => {
    expect(addMember(['releases'], '')).toEqual(['releases'])
  })

  it('removes a repository and keeps the order of the others', () => {
    expect(removeMember(['a', 'b', 'c'], 'b')).toEqual(['a', 'c'])
  })

  it('moves a repository up or down by one place', () => {
    expect(moveMember(['a', 'b', 'c'], 2, -1)).toEqual(['a', 'c', 'b'])
    expect(moveMember(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c'])
  })

  it('does not move past the first or the last place', () => {
    expect(moveMember(['a', 'b'], 0, -1)).toEqual(['a', 'b'])
    expect(moveMember(['a', 'b'], 1, 1)).toEqual(['a', 'b'])
  })

  it('does not change the list it is given', () => {
    const list = ['a', 'b']
    moveMember(list, 0, 1)
    expect(list).toEqual(['a', 'b'])
  })

  it('offers the repositories that are not members yet, in their order', () => {
    expect(choosableMembers(['all', 'releases', 'snapshots'], ['releases'])).toEqual(['all', 'snapshots'])
  })
})
