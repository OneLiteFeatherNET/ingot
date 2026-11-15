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
import { statisticsState } from './statisticsState'

describe('statisticsState', () => {
  it('reports an error when the request failed, even before the server said anything', () => {
    const state = statisticsState({ enabled: null, failed: true, series: null })

    expect(state, 'a failed request must not look like a disabled feature').toBe('error')
  })

  it('reports an error rather than disabled when a request fails after the flag was read', () => {
    const state = statisticsState({ enabled: false, failed: true, series: null })

    expect(state, 'a failure is an error whatever the last known flag was').toBe('error')
  })

  it('reports disabled only when the server says statistics are off', () => {
    const state = statisticsState({ enabled: false, failed: false, series: null })

    expect(state, 'an explicit off flag from the server is the only route to disabled').toBe('disabled')
  })

  it('reports loading while nothing has been received yet', () => {
    const state = statisticsState({ enabled: null, failed: false, series: null })

    expect(state, 'before any answer the card should show a placeholder').toBe('loading')
  })

  it('reports empty when statistics are on but no series were recorded', () => {
    const state = statisticsState({ enabled: true, failed: false, series: [] })

    expect(state, 'enabled with no data is an empty state, not an error').toBe('empty')
  })

  it('reports ready when statistics are on and there are series', () => {
    const state = statisticsState({ enabled: true, failed: false, series: [{ name: 'a', data: [] }] })

    expect(state, 'a series to draw should be the ready state').toBe('ready')
  })
})
