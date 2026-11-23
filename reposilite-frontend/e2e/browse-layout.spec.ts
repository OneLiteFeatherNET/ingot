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

import type { Locator, Page } from '@playwright/test'
import { test, expect } from './support/fixtures'
import { siteUrl } from './support/api'

// The start page and the browse view share one grid: the list on the left, the details card as a sidebar on the
// right from 1024 px. These tests measure the layout in a real browser, so they assert geometry, not class names.

const TOLERANCE_PX = 2

type Edges = { left: number, right: number, top: number, bottom: number }

const boxOf = (locator: Locator): Promise<Edges> => locator.evaluate((element) => {
  const rect = element.getBoundingClientRect()
  return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom }
})

// The first line of text of an element is measured as a range, so padding and icons do not count. The helpers run in
// the page, so each one carries its own copy of the text lookup.
const textTopOf = (locator: Locator) => locator.evaluate((element) => {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode()
  while (node && node.textContent!.trim() === '') node = walker.nextNode()
  const range = document.createRange()
  range.selectNodeContents(node!)
  return range.getBoundingClientRect().top
})

const lineCountOfText = (locator: Locator) => locator.evaluate((element) => {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  let node = walker.nextNode()
  while (node && node.textContent!.trim() === '') node = walker.nextNode()
  // The title is not rendered while the card loads, so there is no text to count yet.
  if (!node) return 0
  const range = document.createRange()
  range.selectNodeContents(node)
  return range.getClientRects().length
})

// The outlined box of the filter field, which holds the input, the icons and the outline. Its label sits inside it.
const fieldBox = (page: Page) => page.getByRole('searchbox', { name: /filter/i }).locator('xpath=ancestor::div[contains(@class, "relative")][1]')

const detailsToggle = (page: Page) => page.locator('[data-ingot-component="snippets-card"] button[aria-expanded]')
const detailsCard = (page: Page) => page.locator('[data-ingot-component="snippets-card"]')

// The action slot of a row, against the width its own buttons take: from the first button to the last one, and
// nothing when the row renders no button. Below the medium window a slot reserves nothing beyond its buttons.
const slotAgainstOwnButtons = (rows: Locator) => rows.evaluateAll(elements => elements.map((row) => {
  const slot = row.querySelector('[data-row-actions]')!
  const buttons = [...slot.querySelectorAll('button, a')].map(button => button.getBoundingClientRect())
  const ownWidth = buttons.length === 0
    ? 0
    : Math.max(...buttons.map(box => box.right)) - Math.min(...buttons.map(box => box.left))
  return { name: row.querySelector('a')!.textContent.trim(), slotWidth: slot.getBoundingClientRect().width, ownWidth }
}))

const expectWithin = (box: Edges, container: Edges, what: string) => {
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
      // The page shell around the grid carries the gutter, as the container of the hero does.
      const pageShell = grid.locator('xpath=..')
      const hero = page.locator('.bg-surface-container-low.border-b > .container').first()
      await expect(grid, 'the overview grid').toBeVisible()
      const gridBox = await boxOf(grid)
      const shellBox = await boxOf(pageShell)
      const heroBox = await boxOf(hero)
      expect(Math.abs(shellBox.left - heroBox.left), 'left gutter of the page against the hero, in px').toBeLessThanOrEqual(1)
      expect(Math.abs(shellBox.right - heroBox.right), 'right gutter of the page against the hero, in px').toBeLessThanOrEqual(1)
      const heroGutter = await hero.evaluate(element => parseFloat(getComputedStyle(element).paddingLeft))
      expect(Math.abs(gridBox.left - (heroBox.left + heroGutter)), 'left edge of the grid against the content of the hero, in px').toBeLessThanOrEqual(1)
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
      await expect.poll(() => lineCountOfText(detailsToggle(page)), { message: 'lines of the details heading' }).toBe(1)
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
    const code = snippet.locator('pre')
    expect(await code.evaluate(element => getComputedStyle(element).whiteSpace), 'white-space of the snippet').toBe('pre')
    // Every line of the text is one line on screen: no line is broken at the edge of the card.
    const lines = await code.evaluate(element => ({
      shown: Math.round(element.getBoundingClientRect().height / parseFloat(getComputedStyle(element).lineHeight)),
      written: (element.textContent ?? '').split('\n').length,
    }))
    expect(lines.shown, 'lines on screen against lines written').toBe(lines.written)
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

  // The action slot is shared by the rows from the medium window (600 px) up, so the sizes line up there.
  for (const width of [600, 1440]) {
    test(`right-aligns the file sizes of a version directory in one column at ${width} px`, async ({ page, seed }) => {
      await page.setViewportSize({ width, height: 900 })
      const repository = await seed.repository('size-column')
      const directory = 'com/example/demo/1.0'
      // Rows with no action, one action (delete only) and two actions (view or javadoc, and delete) side by side.
      await seed.upload(repository, `${directory}/demo-1.0.jar`, 'jar bytes\n')
      await seed.upload(repository, `${directory}/demo-1.0.pom`, '<project/>\n')
      await seed.upload(repository, `${directory}/maven-metadata.xml`, '<metadata/>\n')
      await seed.upload(repository, `${directory}/demo-1.0-javadoc.jar`, 'javadoc bytes, a bit longer\n')
      await seed.upload(repository, `${directory}/demo-1.0-sources.jar`, 'sources\n')
      await page.goto(siteUrl(`/browse/${repository}/${directory}`))
      // The size sits on the name line only in the compact list, which the toggle switches on.
      await page.getByRole('button', { name: 'Compact list' }).click()

      const rows = page.locator('[data-ingot-component="detailed-list-entry"]')
      await expect(rows, 'one row per uploaded file').toHaveCount(5)
      const sizeRights = await rows.evaluateAll(elements => elements.map((row) => {
        const size = row.querySelector('.md-label-small')!
        return { name: row.querySelector('a')!.textContent.trim(), right: size.getBoundingClientRect().right }
      }))
      const reference = sizeRights[0].right
      for (const { name, right } of sizeRights) {
        expect(Math.abs(right - reference), `right edge of the size of ${name} against the first row, in px`).toBeLessThanOrEqual(0.5)
      }
    })
  }

  test('shows the file names of a signed-out listing in full at 320 px', async ({ page, seed }) => {
    await page.setViewportSize({ width: 320, height: 900 })
    const repository = await seed.repository('names-320')
    const directory = 'com/example/demo/1.0'
    await seed.upload(repository, `${directory}/demo-1.0.jar`, 'jar bytes\n')
    await seed.upload(repository, `${directory}/demo-1.0.pom`, '<project/>\n')
    await seed.upload(repository, `${directory}/maven-metadata.xml`, '<metadata/>\n')
    await page.goto(siteUrl(`/browse/${repository}/${directory}`))
    await page.getByRole('button', { name: 'Compact list' }).click()

    // The jar and the pom are the names the slot could cut. maven-metadata.xml keeps the view action of its row,
    // which main reserves as well, so its name is not part of this check.
    for (const name of ['demo-1.0.jar', 'demo-1.0.pom']) {
      const label = page.locator('[data-ingot-component="detailed-list-entry"]').filter({ hasText: name }).locator('span.md-body-large')
      await expect(label, `the row of ${name}`).toHaveCount(1)
      const shownInFull = await label.evaluate(element => element.scrollWidth <= element.clientWidth)
      expect(shownInFull, `${name} is shown in full at 320 px`).toBe(true)
    }
    await expect(page.getByRole('link', { name: /^demo-1\.0\.pom\b/ }), 'the pom name is shown in full').toBeVisible()
    // A signed-out row reserves only its own buttons, so each name has the room it has on main.
    for (const { name, slotWidth, ownWidth } of await slotAgainstOwnButtons(page.locator('[data-ingot-component="detailed-list-entry"]'))) {
      expect(Math.abs(slotWidth - ownWidth), `the action slot of ${name} against its own buttons, in px`).toBeLessThanOrEqual(0.5)
    }
  })

  test('shows the file names of a signed-in listing at 320 px with no more room reserved than its buttons', async ({ adminPage, seed }) => {
    await adminPage.setViewportSize({ width: 320, height: 900 })
    const repository = await seed.repository('names-320-signed-in')
    const directory = 'com/example/demo/1.0'
    await seed.upload(repository, `${directory}/demo-1.0.jar`, 'jar bytes\n')
    await seed.upload(repository, `${directory}/demo-1.0.pom`, '<project/>\n')
    await seed.upload(repository, `${directory}/maven-metadata.xml`, '<metadata/>\n')
    await seed.upload(repository, `${directory}/demo-1.0-javadoc.jar`, 'javadoc bytes, a bit longer\n')
    await adminPage.goto(siteUrl(`/browse/${repository}/${directory}`))
    await adminPage.getByRole('button', { name: 'Compact list' }).click()

    const rows = adminPage.locator('[data-ingot-component="detailed-list-entry"]')
    await expect(rows, 'one row per uploaded file').toHaveCount(4)
    for (const { name, slotWidth, ownWidth } of await slotAgainstOwnButtons(rows)) {
      expect(Math.abs(slotWidth - ownWidth), `the action slot of ${name} against its own buttons, in px`).toBeLessThanOrEqual(0.5)
    }
  })

  test('reserves no action room in a signed-out listing without actions at 320 px', async ({ page, seed }) => {
    await page.setViewportSize({ width: 320, height: 900 })
    const repository = await seed.repository('no-actions-320')
    const directory = 'com/example/demo/2.0'
    await seed.upload(repository, `${directory}/demo-2.0.jar`, 'jar bytes\n')
    await seed.upload(repository, `${directory}/demo-2.0-sources.jar`, 'sources\n')
    await page.goto(siteUrl(`/browse/${repository}/${directory}`))
    await page.getByRole('button', { name: 'Compact list' }).click()

    const rows = page.locator('[data-ingot-component="detailed-list-entry"]')
    await expect(rows, 'one row per uploaded file').toHaveCount(2)
    const slotWidths = await rows.locator('[data-row-actions]').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().width))
    expect(slotWidths, 'the action slot of each row, in px').toEqual([0, 0])
  })
})
