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

import { describe, it, expect, beforeEach, vi } from 'vitest'

const jar = { name: 'example-1.0.jar', type: 'FILE' }
const checksum = { name: 'example-1.0.jar.sha1', type: 'FILE' }
const signature = { name: 'example-1.0.jar.asc', type: 'FILE' }

// The store keeps its flags at module scope, so every test gets a fresh copy of it.
const loadStore = async () => {
  vi.resetModules()
  localStorage.clear()
  const store = await import('./adjustments')
  return { ...store, adjustments: store.useAdjustments() }
}

describe('file browser adjustments', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('hides checksum and signature files by default', async () => {
    const { adjustments } = await loadStore()

    const visible = adjustments.applyAdjustments([jar, checksum, signature])

    expect(visible, 'utility files are hidden until the adjustment is switched on').toEqual([jar])
  })

  it('keeps checksum and signature files once they are displayed', async () => {
    const { adjustments } = await loadStore()
    adjustments.displayHashFiles.value = true

    const visible = adjustments.applyAdjustments([jar, checksum, signature])

    expect(visible, 'every file is listed when utility files are displayed').toEqual([jar, checksum, signature])
  })

  it('counts the utility files that are hidden', async () => {
    const { adjustments } = await loadStore()

    const hidden = adjustments.countHiddenFiles([jar, checksum, signature])

    expect(hidden, 'two utility files are hidden from the listing').toBe(2)
  })

  it('counts no hidden files while utility files are displayed', async () => {
    const { adjustments } = await loadStore()
    adjustments.displayHashFiles.value = true

    const hidden = adjustments.countHiddenFiles([jar, checksum, signature])

    expect(hidden, 'nothing is hidden when utility files are displayed').toBe(0)
  })

  it('does not count a regular file as a utility file', async () => {
    const { adjustments } = await loadStore()

    const hidden = adjustments.countHiddenFiles([jar])

    expect(hidden, 'the jar is listed, so nothing is hidden').toBe(0)
  })
})
