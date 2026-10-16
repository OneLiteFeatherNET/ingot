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

import { describe, it, expect, beforeEach } from 'vitest'
import { RAIL_WIDTH_VARIABLE, publishRailWidth, clearRailWidth, railWidth } from './navigation-rail'

describe('navigation rail width', () => {
  let root

  beforeEach(() => {
    root = document.createElement('html')
  })

  it('publishes the rail width as its own variable, not the bottom bar height', () => {
    publishRailWidth(root)

    expect(root.style.getPropertyValue(RAIL_WIDTH_VARIABLE)).toBe(railWidth())
    expect(root.style.getPropertyValue('--app-bottom-bar-height'), 'the bottom bar variable is untouched').toBe('')
  })

  it('removes the variable when the rail goes away', () => {
    publishRailWidth(root)

    clearRailWidth(root)

    expect(root.style.getPropertyValue(RAIL_WIDTH_VARIABLE)).toBe('')
  })
})
