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
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { ROUTES, NAVIGATION_SEGMENTS, type AppRoute } from './routes'

// The one list of dashboard segments. The router, the dashboard server and the backend read it,
// and the Helm chart keeps a committed copy that CI compares with this file. Vitest runs from the
// frontend module, so the path is taken from there; import.meta.url is not a file URL under jsdom.
const readSegmentList = (): string[] =>
  JSON.parse(readFileSync(resolve(process.cwd(), 'src/router/dashboard-segments.json'), 'utf8')) as string[]

// The first path segment of every route that has a fixed one. The root route and the catch-all
// have none and are left out, and so is a parameter in the first position. A navigation segment is left out too: the
// server claims it only while a reader page needs it, so it is not one of the segments every deployment reserves.
const topLevelSegmentsOf = (routes: AppRoute[]) => [
  ...new Set(
    routes
      .map(route => route.path.split('/')[1])
      .filter(segment => segment !== undefined && segment !== '' && !segment.startsWith(':'))
      .filter(segment => !NAVIGATION_SEGMENTS.includes(segment))
  )
].sort()

describe('dashboard segment list', () => {
  it('lists exactly the top-level segments of the router', () => {
    const listed = [...readSegmentList()].sort()

    expect(listed, 'segments of ROUTES missing from dashboard-segments.json, or listed without a route')
      .toEqual(topLevelSegmentsOf(ROUTES))
  })

  it('lists every segment once, in lower case', () => {
    const listed = readSegmentList()

    expect(new Set(listed).size, 'a segment is listed twice in dashboard-segments.json').toBe(listed.length)
    expect(listed.every(segment => /^[a-z]+$/.test(segment)), 'segments must be lower case words').toBe(true)
  })

  it('keeps the reader segment out of the reserved list', () => {
    expect(readSegmentList(), 'plugins is claimed only while a reader page exists, so it is not reserved').not.toContain('plugins')
    expect(NAVIGATION_SEGMENTS, 'the reader pages live under plugins').toContain('plugins')
  })

  it('lists the analytics segment of the usage page', () => {
    expect(readSegmentList(), 'the analytics page needs its segment in the list').toContain('analytics')
  })
})
