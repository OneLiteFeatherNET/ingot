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

import { test, expect } from './support/fixtures.js'
import { siteUrl } from './support/api.js'

// Repositories that make the Repositories page taller than the viewport, so that it scrolls.
const FILLER_REPOSITORIES = 8

// True when the element is the topmost thing at its own centre: nothing, such as the app bar, covers it.
const isUncovered = (locator) => locator.evaluate((element) => {
  const box = element.getBoundingClientRect()
  const topmost = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)
  return topmost !== null && element.contains(topmost)
})

test.describe('administration page header while scrolling', () => {
  test('keeps the page heading and its action visible and uncovered by the app bar', async ({ adminPage: page, seed }) => {
    // Known regression on main: the heading and the page actions go under the app bar while the page scrolls.
    // The fixing change is rework-administration-layout, which removes this marker in its pull request.
    test.fail(true, 'rework-administration-layout')
    for (let index = 0; index < FILLER_REPOSITORIES; index++) {
      await seed.repository(`filler-${index}`)
    }
    await page.setViewportSize({ width: 1280, height: 600 })
    await page.goto(siteUrl('/settings/repositories'))
    const heading = page.getByRole('heading', { name: 'Repositories', level: 1 })
    const action = page.getByRole('button', { name: 'New repository', exact: true })
    await expect(heading).toBeVisible()

    await page.mouse.move(640, 400)
    await page.mouse.wheel(0, 240)
    await expect.poll(() => page.evaluate(() => window.scrollY), 'the page has scrolled').toBeGreaterThan(0)

    await expect.poll(() => isUncovered(heading), 'the heading is not covered by the app bar').toBe(true)
    await expect.poll(() => isUncovered(action), 'the page action is not covered by the app bar').toBe(true)
  })
})
