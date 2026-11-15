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
import { summariseSeries } from './seriesSummary'

const format = (value) => `${value} MB`

describe('summariseSeries', () => {
  it('states the latest and the highest value of a series', () => {
    const [summary] = summariseSeries(
      [{ name: 'Used memory', data: [[1000, 10], [2000, 40], [3000, 25]] }],
      format
    )

    expect(summary, 'a reader without the canvas should get the latest and the peak').toBe(
      'Used memory: latest 25 MB, highest 40 MB'
    )
  })

  it('takes the latest value by time, not by position in the array', () => {
    const [summary] = summariseSeries(
      [{ name: 'Used threads', data: [[3000, 7], [1000, 4]] }],
      (value) => `${value}`
    )

    expect(summary, 'the last reading in time is the latest, whatever order the data came in').toBe(
      'Used threads: latest 7, highest 7'
    )
  })

  it('says a series has no data rather than inventing a value', () => {
    const [summary] = summariseSeries([{ name: 'Other (3)', data: [] }], format)

    expect(summary, 'an empty series must not be summarised as zero').toBe('Other (3): no data')
  })

  it('summarises each series on its own line', () => {
    const summaries = summariseSeries(
      [
        { name: 'a', data: [[1, 1]] },
        { name: 'b', data: [[1, 2]] }
      ],
      (value) => `${value}`
    )

    expect(summaries, 'every series should get its own summary').toHaveLength(2)
  })
})
