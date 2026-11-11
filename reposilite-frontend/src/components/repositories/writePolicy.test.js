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
import { WRITE_CHOICES, writePolicyChange, writePolicyOf } from './writePolicy'

const hosted = (extra = {}) => ({ id: 'releases', type: 'HOSTED', visibility: 'PUBLIC', redeployment: true, ...extra })

describe('write policy of a hosted repository, derived from its stored keys', () => {
  it('is Allow when redeployment is true and read only is not set', () => {
    expect(writePolicyOf(hosted())).toBe('allow')
  })

  it('is Allow once when redeployment is false', () => {
    expect(writePolicyOf(hosted({ redeployment: false }))).toBe('allowOnce')
  })

  it('reads a missing redeployment as off, which is the server default', () => {
    expect(writePolicyOf({ id: 'releases', type: 'HOSTED' })).toBe('allowOnce')
  })

  it('is Read only when read only is true, whatever redeployment says', () => {
    expect(writePolicyOf(hosted({ readOnly: true }))).toBe('readOnly')
    expect(writePolicyOf(hosted({ readOnly: true, redeployment: false }))).toBe('readOnly')
  })

  it('offers the three choices in their order, with their words', () => {
    expect(WRITE_CHOICES).toEqual([
      { value: 'allow', label: 'Allow' },
      { value: 'allowOnce', label: 'Allow once' },
      { value: 'readOnly', label: 'Read only' }
    ])
  })
})

describe('changing the write policy writes only the keys the choice needs', () => {
  it('sets redeployment to false for Allow once and writes no read only key', () => {
    const next = writePolicyChange(hosted(), 'allowOnce')
    expect(next).toEqual({ id: 'releases', type: 'HOSTED', visibility: 'PUBLIC', redeployment: false })
    expect('readOnly' in next).toBe(false)
  })

  it('sets read only without touching redeployment', () => {
    const next = writePolicyChange(hosted({ redeployment: false }), 'readOnly')
    expect(next.readOnly).toBe(true)
    expect(next.redeployment).toBe(false)
  })

  it('restores the earlier state after Read only, with no data loss', () => {
    const readOnly = writePolicyChange(hosted(), 'readOnly')
    const once = writePolicyChange(readOnly, 'allowOnce')
    expect(writePolicyOf(once)).toBe('allowOnce')
    expect(once.readOnly).not.toBe(true)
    expect(writePolicyChange(readOnly, 'allow').redeployment).toBe(true)
  })

  it('removes a read only flag when Allow is chosen again', () => {
    const next = writePolicyChange(hosted({ readOnly: true }), 'allow')
    expect(next.readOnly).toBeUndefined()
    expect(next.redeployment).toBe(true)
  })

  it('returns the same entry when the choice is the one already stored, so nothing is written', () => {
    const entry = hosted()
    expect(writePolicyChange(entry, 'allow')).toBe(entry)
    const readOnly = hosted({ readOnly: true })
    expect(writePolicyChange(readOnly, 'readOnly')).toBe(readOnly)
  })

  it('does not change the entry it is given', () => {
    const entry = hosted()
    writePolicyChange(entry, 'readOnly')
    expect(entry.readOnly).toBeUndefined()
  })
})
