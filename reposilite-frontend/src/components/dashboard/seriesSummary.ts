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

/**
 * One sentence per series, for readers who cannot see the canvas: the newest reading and the
 * highest one. Points are `[x, y]` pairs, and `x` is time, so "latest" is the point with the
 * largest `x` rather than the last one in the array.
 *
 * @param {{ name: string, data: [number, number][] }[]} series
 * @param {(value: number) => string} format
 * @returns {string[]}
 */
export const summariseSeries = (series, format) =>
  series.map(({ name, data }) => {
    if (data.length === 0) return `${name}: no data`

    const latest = data.reduce((newest, point) => (point[0] > newest[0] ? point : newest))
    const highest = data.reduce((peak, point) => Math.max(peak, point[1]), -Infinity)

    return `${name}: latest ${format(latest[1])}, highest ${format(highest)}`
  })
