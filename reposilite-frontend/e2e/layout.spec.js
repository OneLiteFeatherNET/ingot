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

// The wide screens of use-wide-screens (D2 and D4): the repository table shows more columns, the dashboard lays its
// charts out in more columns, a settings form stays at the reading measure, and the overview keeps the snippet card
// beside the browse list. The cases run at 1920 and 2560 pixels only.
test.describe('wide screens', () => {
  for (const width of [1920, 2560]) {
    test.describe(`at ${width} pixels`, () => {
      test.beforeEach(async ({ adminPage: page }) => {
        await page.setViewportSize({ width, height: 900 })
      })

      test('shows the address column, and the storage column only from 2560 pixels', async ({ adminPage: page, seed }) => {
        await seed.repository('wide-columns')
        await page.goto(siteUrl('/settings/repositories'))

        await expect(page.getByRole('columnheader', { name: 'Address', exact: true }), 'the address is shown').toBeVisible()
        const storage = page.getByRole('columnheader', { name: 'Storage', exact: true })
        if (width >= 2560) {
          await expect(storage, 'the storage column has room at 2560 pixels').toBeVisible()
        } else {
          await expect(storage, 'the storage column is still hidden at 1920 pixels').toBeHidden()
        }
      })

      test('lays the resource charts out in two columns at this width, with no empty third column', async ({ adminPage: page }) => {
        await page.goto(siteUrl('/dashboard'))
        const memory = page.getByRole('heading', { name: 'Memory', exact: true })
        const threads = page.getByRole('heading', { name: 'Threads', exact: true })
        await expect(memory).toBeVisible()
        await expect(threads).toBeVisible()

        // The layout settles while the charts load, so the boxes are read until the two headings share a row.
        const rowOf = async () => {
          const first = await memory.boundingBox()
          const second = await threads.boundingBox()
          return { sameRow: Math.abs(first.y - second.y) < 2, rightOf: second.x > first.x + first.width / 2 }
        }
        await expect.poll(rowOf, 'the two charts share one row, the threads chart to the right').toEqual({ sameRow: true, rightOf: true })

        // The charts sit in one grid: the innermost grid that holds both headings names the columns it draws.
        const chartGrid = page.locator('div.grid').filter({ has: memory }).filter({ has: threads }).last()
        const tracks = await chartGrid.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)
        expect(tracks, 'the number of chart columns at this width').toBe(2)
      })

      test('gives the getting started checklist the content width, with no blank block beside it', async ({ adminPage: page }) => {
        await page.goto(siteUrl('/dashboard'))
        // The checklist's own button sits at its right edge, so its right edge shows how wide the card is.
        const hide = page.getByRole('button', { name: 'Hide checklist' })
        await expect(hide).toBeVisible()

        const box = await hide.boundingBox()
        expect(box.x + box.width, 'the checklist reaches well past the middle of the window').toBeGreaterThan(width * 0.6)
      })

      test('keeps a settings form at most 960 pixels wide', async ({ adminPage: page }) => {
        await page.goto(siteUrl('/settings/audit'))
        const frame = page.getByRole('region', { name: 'Audit settings', exact: true })
        await expect(frame).toBeVisible()

        const box = await frame.boundingBox()
        expect(box.width, 'the width of the settings page in px').toBeLessThanOrEqual(960)
      })

      test('places the snippet card beside the browse list on the overview', async ({ adminPage: page, seed }) => {
        await seed.repository('wide-overview')
        await page.goto(siteUrl('/'))
        const list = page.getByRole('heading', { name: 'Repositories', exact: true })
        // The snippet card is found by its copy button, which every snippet of the card has.
        const card = page.getByRole('button', { name: 'Copy snippet' }).first()
        await expect(list).toBeVisible()
        await expect(card).toBeVisible()

        const listBox = await list.boundingBox()
        const cardBox = await card.boundingBox()
        expect(cardBox.x, 'the card starts to the right of the list').toBeGreaterThan(listBox.x + 100)
        expect(cardBox.y, 'the card starts on the same row as the list').toBeLessThan(listBox.y + listBox.height + 200)

        // The list takes the wider column: two thirds of the split, so it is at least 1.5 times the card.
        const listRegion = await page.getByRole('region', { name: 'Repositories', exact: true }).boundingBox()
        expect(listRegion.width, 'the browse list is the wider column').toBeGreaterThanOrEqual(cardBox.width * 1.5)
      })
    })
  }
})

// Below 1240 pixels the Overview page is the index and the page at once. Its order is the page header, Needs attention,
// the index with Overview marked current, and At a glance; no back arrow leads anywhere, because the index is on the page.
test.describe('the Overview page below 1240 pixels', () => {
  const topOf = (locator) => locator.evaluate(element => element.getBoundingClientRect().top)

  for (const width of [320, 900]) {
    test(`orders the header, Needs attention, the index and At a glance at ${width} pixels`, async ({ adminPage: page, seed }) => {
      // The seed prefixes the id with the test name, so the id it returns is the one the page lists.
      const repository = await seed.repository(`overview-draft-${width}`)
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/settings/repositories'))

      // A draft that is not saved: the description of the repository is changed, and the change stays in the page.
      await page.getByRole('button', { name: `Actions for ${repository}`, exact: true }).click()
      await page.getByRole('menuitem', { name: `Edit ${repository}`, exact: true }).click()
      await page.getByRole('textbox', { name: 'Description', exact: true }).fill('Changed on the way to Overview')
      await page.getByRole('link', { name: 'Back to Administration' }).click()

      await expect(page).toHaveURL(/\/settings$/)
      const heading = page.getByRole('heading', { name: 'Overview', level: 1 })
      const attention = page.getByRole('heading', { name: 'Needs attention', level: 2 })
      // The trail above the heading is also named Administration on narrow widths, so the index is the one with links.
      const index = page.getByRole('navigation', { name: 'Administration', exact: true }).filter({ has: page.getByRole('link') })
      const glance = page.getByRole('heading', { name: 'At a glance', level: 2 })
      await expect(attention).toBeVisible()
      await expect(page.getByRole('link', { name: 'Repositories, Unsaved changes', exact: true })).toBeVisible()

      const tops = [await topOf(heading), await topOf(attention), await topOf(index), await topOf(glance)]
      expect(tops, 'the header, Needs attention, the index and At a glance, top to bottom').toEqual([...tops].sort((a, b) => a - b))
      expect(await page.getByRole('link', { name: 'Back to Administration' }).count(), 'no back arrow on Overview').toBe(0)
      expect(await index.getByRole('link', { name: 'Overview', exact: true }).getAttribute('aria-current'), 'Overview is marked current in the index').toBe('page')
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow, 'no horizontal scrolling in px').toBeLessThanOrEqual(0)
    })
  }
})
