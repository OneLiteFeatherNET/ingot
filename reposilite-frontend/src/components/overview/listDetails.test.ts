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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { LIST_DETAILS_KEY, readListDetails, writeListDetails } from './listDetails'

// A stand-in for localStorage that keeps its items in a map, so a test can read back what was written.
const memoryStorage = () => {
  const items = new Map<string, string>()
  return {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => {
      items.set(key, value)
    },
    items
  }
}

// A stand-in for storage that is blocked, as a private window or a disabled site can make it.
const blockedStorage = () => ({
  getItem: () => {
    throw new DOMException('Access is denied for this document.', 'SecurityError')
  },
  setItem: () => {
    throw new DOMException('Access is denied for this document.', 'SecurityError')
  }
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('list details choice', () => {
  it('is off when the viewer has not chosen anything yet', () => {
    vi.stubGlobal('localStorage', memoryStorage())

    expect(readListDetails()).toBe(false)
  })

  it('is on when the viewer switched it on before', () => {
    const storage = memoryStorage()
    storage.items.set(LIST_DETAILS_KEY, 'true')
    vi.stubGlobal('localStorage', storage)

    expect(readListDetails()).toBe(true)
  })

  it('is off when the stored value is not one this page wrote', () => {
    const storage = memoryStorage()
    storage.items.set(LIST_DETAILS_KEY, 'banana')
    vi.stubGlobal('localStorage', storage)

    expect(readListDetails()).toBe(false)
  })

  it('is off when storage cannot be read, so the page still works', () => {
    vi.stubGlobal('localStorage', blockedStorage())

    expect(readListDetails()).toBe(false)
  })

  it('gives back the choice that was written, so a later page starts with it', () => {
    const storage = memoryStorage()
    vi.stubGlobal('localStorage', storage)

    writeListDetails(true)

    expect(storage.items.get(LIST_DETAILS_KEY)).toBe('true')
    expect(readListDetails()).toBe(true)
  })

  it('does not throw when storage cannot be written, and the choice then lasts only for the page', () => {
    vi.stubGlobal('localStorage', blockedStorage())

    expect(() => writeListDetails(true)).not.toThrow()
    expect(readListDetails()).toBe(false)
  })
})
