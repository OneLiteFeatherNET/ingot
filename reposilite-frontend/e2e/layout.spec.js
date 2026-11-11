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

const topOf = (locator) => locator.evaluate(element => element.getBoundingClientRect().top)

test.describe('administration page header while scrolling', () => {
  // The design (rework-administration-layout, D2) keeps the page header out of sticky positioning: the top bar is the
  // only sticky element, and the header and its actions scroll with the page, under the top bar when they reach it.
  // This test pins that behaviour: the header moves by exactly the scroll distance, and the top bar stays at the top.
  test('moves the page heading and its action with the page while the app bar stays at the top', async ({ adminPage: page, seed }) => {
    for (let index = 0; index < FILLER_REPOSITORIES; index++) {
      await seed.repository(`filler-${index}`)
    }
    await page.setViewportSize({ width: 1280, height: 600 })
    await page.goto(siteUrl('/settings/repositories'))
    const heading = page.getByRole('heading', { name: 'Repositories', level: 2 })
    const action = page.getByRole('button', { name: 'New repository', exact: true })
    const appBar = page.getByRole('banner')
    await expect(heading).toBeVisible()
    const headingBefore = await topOf(heading)
    const actionBefore = await topOf(action)

    await page.mouse.move(640, 400)
    await page.mouse.wheel(0, 240)
    await expect.poll(() => page.evaluate(() => window.scrollY), 'the page has scrolled').toBeGreaterThan(0)
    const scrolled = await page.evaluate(() => window.scrollY)

    await expect.poll(() => topOf(heading), 'the heading scrolls with the page, so it is not sticky').toBeCloseTo(headingBefore - scrolled, 0)
    await expect.poll(() => topOf(action), 'the page action scrolls with the page, so it is not sticky').toBeCloseTo(actionBefore - scrolled, 0)
    expect(await appBar.evaluate(element => getComputedStyle(element).position), 'the app bar is the sticky element').toBe('sticky')
    expect(await topOf(appBar), 'the app bar stays at the top of the screen').toBe(0)
  })
})
