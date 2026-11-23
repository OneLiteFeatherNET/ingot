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
import { nextTick, ref } from 'vue'
import { useRowFade } from './useRowFade'

// Every test builds its own keys and its own composable, so no test depends on another.
const setup = (initial: string[], ready = true) => {
  const keys = ref(initial)
  const isReady = ref(ready)
  const { isAdded } = useRowFade(() => keys.value, () => isReady.value)
  return { keys, isReady, isAdded }
}

describe('useRowFade', () => {
  it('does not mark the rows of the initial load', () => {
    const { isAdded } = setup(['a', 'b'])

    expect(isAdded('a'), 'a row of the initial load does not fade').toBe(false)
    expect(isAdded('b'), 'a row of the initial load does not fade').toBe(false)
  })

  it('marks a row that is added after the initial load', async () => {
    const { keys, isAdded } = setup(['a'])

    keys.value = ['a', 'b']
    await nextTick()

    expect(isAdded('b'), 'the new row fades in').toBe(true)
    expect(isAdded('a'), 'the row that was there stays plain').toBe(false)
  })

  it('does not mark the rows below a removed row', async () => {
    const { keys, isAdded } = setup(['a', 'b', 'c'])

    keys.value = ['a', 'c']
    await nextTick()

    expect(isAdded('a'), 'a removal marks nothing').toBe(false)
    expect(isAdded('c'), 'a removal marks nothing').toBe(false)
  })

  it('does not mark rows that only change their order', async () => {
    const { keys, isAdded } = setup(['a', 'b'])

    keys.value = ['b', 'a']
    await nextTick()

    expect(isAdded('a'), 'a sort is not an addition').toBe(false)
    expect(isAdded('b'), 'a sort is not an addition').toBe(false)
  })

  it('waits for the load and treats the rows that arrive with it as the initial load', async () => {
    const { keys, isReady, isAdded } = setup([], false)

    keys.value = ['a', 'b']
    isReady.value = true
    await nextTick()

    expect(isAdded('a'), 'the first loaded rows do not fade').toBe(false)
    expect(isAdded('b'), 'the first loaded rows do not fade').toBe(false)
  })

  it('treats the next load as an initial load again', async () => {
    const { keys, isReady, isAdded } = setup(['a'])

    isReady.value = false
    await nextTick()
    keys.value = ['x', 'y']
    isReady.value = true
    await nextTick()

    expect(isAdded('x'), 'the rows of another directory do not fade').toBe(false)
    expect(isAdded('y'), 'the rows of another directory do not fade').toBe(false)
  })

  it('marks a row again when it is removed and added back', async () => {
    const { keys, isAdded } = setup(['a', 'b'])

    keys.value = ['a']
    await nextTick()
    keys.value = ['a', 'b']
    await nextTick()

    expect(isAdded('b'), 'a row that returns is an added row').toBe(true)
  })
})
