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
import { isNearBottom } from './scroll'

// Plain numbers stand in for the scroll metrics of an element, so the rule is tested without
// a layout engine.
describe('isNearBottom', () => {
  it('treats a panel scrolled to its end as following the output', () => {
    const metrics = { scrollTop: 800, clientHeight: 200, scrollHeight: 1000 }

    expect(isNearBottom(metrics), 'a panel at its end should keep following new lines').toBe(true)
  })

  it('treats a panel a few lines above its end as still following the output', () => {
    const metrics = { scrollTop: 776, clientHeight: 200, scrollHeight: 1000 }

    expect(isNearBottom(metrics), 'a small scroll up is a reader who is still reading the tail').toBe(true)
  })

  it('treats a panel scrolled well above its end as not following the output', () => {
    const metrics = { scrollTop: 100, clientHeight: 200, scrollHeight: 1000 }

    expect(isNearBottom(metrics), 'a reader who scrolled back to read must not be pulled down').toBe(false)
  })

  it('treats a panel whose content is shorter than the panel as at the bottom', () => {
    const metrics = { scrollTop: 0, clientHeight: 200, scrollHeight: 150 }

    expect(isNearBottom(metrics), 'with nothing to scroll, the next line is always in view').toBe(true)
  })
})
