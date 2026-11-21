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
import { indicatorTransform } from './useTabIndicator'

describe('indicatorTransform', () => {
  it('moves the one pixel indicator to the left edge of the tab and stretches it to the width of the tab', () => {
    expect(indicatorTransform(300, 150), 'the offset becomes the translation and the width the scale').toBe('translateX(300px) scaleX(150)')
  })

  it('keeps the first tab at the start of the list', () => {
    expect(indicatorTransform(0, 96), 'a tab at offset 0 is not moved').toBe('translateX(0px) scaleX(96)')
  })
})
