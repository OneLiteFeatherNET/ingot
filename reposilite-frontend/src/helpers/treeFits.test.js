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
import { treeFits } from './treeFits'

// Fixed heights only: a 1080 pixel window, a 64 pixel top bar and a 16 pixel margin. Between the bars the tree has
// 1080 - 64 - 16 - 16 = 984 pixels of room, and a save bar of 100 pixels takes 100 of them.
const WINDOW = { viewportHeight: 1080, topBarHeight: 64, margin: 16 }

describe('treeFits', () => {
  it('fits a tree with room to spare between the bars', () => {
    expect(treeFits({ ...WINDOW, navHeight: 900 }), 'a 900 pixel tree in 984 pixels of room').toBe(true)
  })

  it('fits a tree that is exactly as tall as the room between the bars', () => {
    expect(treeFits({ ...WINDOW, navHeight: 984 }), 'the boundary is inclusive').toBe(true)
  })

  it('does not fit a tree one pixel taller than the room between the bars', () => {
    expect(treeFits({ ...WINDOW, navHeight: 985 }), 'one pixel too tall').toBe(false)
  })

  it('takes the height of the save bar out of the room', () => {
    expect(treeFits({ ...WINDOW, navHeight: 900, saveBarHeight: 100 }), 'a 900 pixel tree in 884 pixels of room').toBe(false)
  })

  it('fits a tree that is exactly as tall as the room left beside the save bar', () => {
    expect(treeFits({ ...WINDOW, navHeight: 884, saveBarHeight: 100 }), 'the boundary holds with a save bar too').toBe(true)
  })
})
