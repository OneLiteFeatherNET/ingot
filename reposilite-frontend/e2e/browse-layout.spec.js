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

import { test, expect } from './support/fixtures'
import { siteUrl } from './support/api'

// The start page and the browse view share one grid: the list on the left, the details card as a sidebar on the
// right from 1024 px. These tests measure the layout in a real browser, so they assert geometry, not class names.

const TOLERANCE_PX = 2

const boxOf = (locator) => locator.evaluate((element) => {
  const rect = element.getBoundingClientRect()
  return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom }
})

// The first line of text of an element is measured as a range, so padding and icons do not count. The helpers run in
// the page, so each one carries its own copy of the text lookup.
const textTopOf = (locator) => locator.evaluate((element) => {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode()
  while (node && node.textContent.trim() === '') node = walker.nextNode()
  const range = document.createRange()
  range.selectNodeContents(node)
  return range.getBoundingClientRect().top
})

const lineCountOfText = (locator) => locator.evaluate((element) => {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode()
  while (node && node.textContent.trim() === '') node = walker.nextNode()
  const range = document.createRange()
  range.selectNodeContents(node)
  return range.getClientRects().length
})

// The outlined box of the filter field, which holds the input, the icons and the outline. Its label sits inside it.
const fieldBox = (page) => page.getByRole('searchbox', { name: /filter/i }).locator('xpath=ancestor::div[contains(@class, "relative")][1]')

const detailsToggle = (page) => page.locator('[data-ingot-component="snippets-card"] button[aria-expanded]')
const detailsCard = (page) => page.locator('[data-ingot-component="snippets-card"]')

const expectWithin = (box, container, what) => {
  expect(box.left, `${what}: left edge against the gutter, in px`).toBeGreaterThanOrEqual(container.left - 1)
  expect(box.right, `${what}: right edge against the gutter, in px`).toBeLessThanOrEqual(container.right + 1)
}

test.describe('browse layout', () => {
  for (const width of [1440, 1920]) {
    test(`puts the details card heading on the line of the repositories heading at ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/'))
      const heading = page.getByRole('heading', { name: 'Repositories', exact: true })
      const toggle = detailsToggle(page)
      await expect(toggle, 'the details toggle of the card').toBeVisible()

      const headingTop = await textTopOf(heading)
      const cardTop = await textTopOf(toggle)
      expect(Math.abs(cardTop - headingTop), 'vertical distance of the two headings, in px').toBeLessThanOrEqual(TOLERANCE_PX)
    })

    test(`keeps both columns inside the hero gutters at ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/'))
      const grid = page.locator('[data-ingot-component="repository-overview"]')
      const hero = page.locator('.bg-surface-container-low.border-b > .container').first()
      await expect(grid, 'the overview grid').toBeVisible()
      const gridBox = await boxOf(grid)
      const heroBox = await boxOf(hero)
      expect(Math.abs(gridBox.left - heroBox.left), 'left gutter of the grid against the hero, in px').toBeLessThanOrEqual(1)
      expect(Math.abs(gridBox.right - heroBox.right), 'right gutter of the grid against the hero, in px').toBeLessThanOrEqual(1)
      expectWithin(await boxOf(detailsCard(page)), gridBox, 'the details card')
      expectWithin(await boxOf(page.locator('section[aria-labelledby="overview-repositories-title"]')), gridBox, 'the repository list')
    })

    test(`shows the filter field and both chip groups on one line at ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/'))
      const field = await boxOf(fieldBox(page))
      const typeGroup = await boxOf(page.getByRole('group', { name: 'Repository type' }).locator('xpath=..'))
      const derivedGroup = await boxOf(page.getByRole('group', { name: 'Derived type' }).locator('xpath=..'))
      expect(Math.abs(field.top - typeGroup.top), 'top of the filter field against the type filter, in px').toBeLessThanOrEqual(TOLERANCE_PX)
      expect(Math.abs(field.top - derivedGroup.top), 'top of the filter field against the derived filter, in px').toBeLessThanOrEqual(TOLERANCE_PX)
    })
  }

  for (const width of [600, 320]) {
    test(`wraps the chip groups below the filter field at ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/'))
      const field = await boxOf(fieldBox(page))
      const typeGroup = await boxOf(page.getByRole('group', { name: 'Repository type' }).locator('xpath=..'))
      expect(field.bottom, 'bottom of the filter field against the top of the type filter, in px').toBeLessThanOrEqual(typeGroup.top + 1)
    })
  }

  for (const width of [1440, 1920]) {
    test(`keeps the details toggle in place when the card is folded at ${width} px`, async ({ page, seed }) => {
      await page.setViewportSize({ width, height: 900 })
      const repository = await seed.repository('toggle-in-place')
      await page.goto(siteUrl(`/browse/${repository}`))
      const toggle = detailsToggle(page)
      await expect(toggle, 'the details toggle').toBeVisible()
      await expect(toggle, 'the details toggle starts expanded').toHaveAttribute('aria-expanded', 'true')

      const before = await boxOf(toggle)
      const cardBefore = await boxOf(detailsCard(page))
      await toggle.click()
      await expect(toggle, 'the details toggle is folded').toHaveAttribute('aria-expanded', 'false')
      const after = await boxOf(toggle)
      const cardAfter = await boxOf(detailsCard(page))

      expect(Math.abs(after.top - before.top), 'vertical move of the toggle, in px').toBeLessThanOrEqual(0.5)
      expect(Math.abs(after.left - before.left), 'horizontal move of the toggle, in px').toBeLessThanOrEqual(0.5)
      expect(Math.abs(cardAfter.left - cardBefore.left), 'sidebar left edge after folding, in px').toBeLessThanOrEqual(0.5)
      expect(Math.abs(cardAfter.right - cardBefore.right), 'sidebar right edge after folding, in px').toBeLessThanOrEqual(0.5)
      await expect(toggle, 'focus stays on the toggle').toBeFocused()
    })

    test(`does not let the toggle focus ring reach the body at ${width} px`, async ({ page, seed }) => {
      await page.setViewportSize({ width, height: 900 })
      const repository = await seed.repository('toggle-focus-ring')
      await page.goto(siteUrl(`/browse/${repository}`))
      const toggle = detailsToggle(page)
      await toggle.focus()
      const body = page.locator(`[id="${await toggle.getAttribute('aria-controls')}"]`)
      const ring = await toggle.evaluate((element) => {
        const style = getComputedStyle(element)
        return parseFloat(style.outlineWidth) + Math.max(0, parseFloat(style.outlineOffset))
      })
      const toggleBox = await boxOf(toggle)
      const bodyBox = await boxOf(body)
      expect(toggleBox.bottom + ring, 'bottom of the focus ring against the top of the body, in px').toBeLessThanOrEqual(bodyBox.top)
    })

    test(`shows the repository details heading on one line at ${width} px`, async ({ page, seed }) => {
      await page.setViewportSize({ width, height: 900 })
      const repository = await seed.repository('details-heading')
      await page.goto(siteUrl(`/browse/${repository}`))
      await expect(detailsToggle(page), 'the details toggle').toBeVisible()
      expect(await lineCountOfText(detailsToggle(page)), 'lines of the details heading').toBe(1)
    })
  }

  test('keeps the snippet tokens on one line for a group with long names at 1440 px', async ({ page, seed }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const member = await seed.repository('member-long-name')
    const group = await seed.repository('group-long-names', {
      proxied: [{ reference: member }],
    })
    await page.goto(siteUrl(`/browse/${group}`))
    const snippet = page.locator('[data-ingot-component="repository-snippet"]').first()
    await expect(snippet, 'the snippet of the group').toBeVisible()
    expect(await snippet.evaluate(element => getComputedStyle(element).whiteSpace), 'white-space of the snippet').toBe('pre')
    const brokenTokens = await snippet.evaluate((element) => [...element.querySelectorAll('span')]
      .filter(span => span.getClientRects().length > 1)
      .map(span => span.textContent.trim()))
    expect(brokenTokens, 'tokens broken across lines').toEqual([])
  })

  test('aligns the includes block of a group with the listing at 1440 px', async ({ page, seed }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    const member = await seed.repository('includes-member')
    const group = await seed.repository('includes-long-group', {
      proxied: [{ reference: member }],
    })
    await page.goto(siteUrl(`/browse/${group}`))
    const includes = page.locator('#browser-includes')
    await expect(includes, 'the includes block').toBeVisible()
    // The surface of the member list is compared with the surface of a listing: the same column edge.
    const memberListBox = await boxOf(includes.locator('ul'))
    const listingBox = await boxOf(page.locator('#browser-list'))
    expect(Math.abs(memberListBox.left - listingBox.left), 'left edge of the member list against the listing, in px').toBeLessThanOrEqual(0.5)
  })
})
