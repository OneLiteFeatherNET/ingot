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

import AxeBuilder from '@axe-core/playwright'
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

        // The header spans the frame; the reading measure caps the layout beneath it, where the settings form is.
        const box = await frame.locator(':scope > header + div').boundingBox()
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

// The Administration tree of rework-administration-side-navigation: from 1240 pixels it is sticky beside the page only while
// it fits between the top bar and the save bar, and it never has a scroll box of its own. Below 1240 pixels the Sections
// sheet opens the same tree from 600 pixels, and nothing is drawn below 600. The tree is found by its name and its links.
test.describe('the administration tree at wide widths', () => {
  const tree = (page) => page.getByRole('navigation', { name: 'Administration', exact: true }).filter({ has: page.getByRole('link') })

  // The number of elements inside the tree that scroll on their own, which must be none.
  const scrollBoxes = (locator) => locator.evaluate(nav => [nav, ...nav.querySelectorAll('*')]
    .filter(element => ['auto', 'scroll'].includes(getComputedStyle(element).overflowY)).length)
  const positionOf = (locator) => locator.evaluate(element => getComputedStyle(element).position)
  // The number of elements of the page content that are sticky, and the page content itself.
  const stickyInContent = (page) => page.locator('main').evaluate(main => [...main.querySelectorAll('*')]
    .filter(element => getComputedStyle(element).position === 'sticky').length)

  for (const [width, height] of [[1280, 1440], [2560, 1440]]) {
    test(`keeps the tree sticky beside the page when it fits, at ${width} by ${height} pixels`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height })
      await page.goto(siteUrl('/settings/repositories'))

      const navigation = tree(page)
      await expect(navigation).toBeVisible()
      await expect.poll(() => positionOf(navigation), 'the tree is sticky while it fits').toBe('sticky')
      expect(await scrollBoxes(navigation), 'no element of the tree scrolls on its own').toBe(0)
      expect(await stickyInContent(page), 'the tree is the only sticky element of the page content').toBe(1)
    })
  }

  for (const [width, height] of [[1366, 768], [1920, 1080]]) {
    test(`lets the window scroll the tree when it is taller than the window, at ${width} by ${height} pixels`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height })
      await page.goto(siteUrl('/settings/repositories'))

      const navigation = tree(page)
      await expect(navigation).toBeVisible()
      // The entries arrive with the settings domains, so the height is polled until the tree has its full height.
      const treeHeight = () => navigation.evaluate(element => element.offsetHeight)
      await expect.poll(treeHeight, 'the precondition: the tree is taller than the window').toBeGreaterThan(height)
      expect(await positionOf(navigation), 'the tree is not sticky when it does not fit').not.toBe('sticky')
      expect(await scrollBoxes(navigation), 'no element of the tree scrolls on its own').toBe(0)
      expect(await stickyInContent(page), 'no element of the page content is sticky').toBe(0)

      const last = navigation.getByRole('link').last()
      await last.scrollIntoViewIfNeeded()
      await expect(last, 'the last entry is reached by scrolling the window').toBeInViewport()
      expect(await page.evaluate(() => window.scrollY), 'the window itself scrolled').toBeGreaterThan(0)
    })
  }
})

test.describe('the Sections sheet below 1240 pixels', () => {
  const sectionsButton = (page) => page.getByRole('button', { name: 'Sections', exact: true })
  const sheet = (page) => page.getByRole('dialog', { name: 'Administration pages', exact: true })

  test('moves from a page to a sibling page through the sheet at 900 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 900, height: 900 })
    await page.goto(siteUrl('/access/ldap'))

    await sectionsButton(page).click()
    await expect(sheet(page), 'the sheet is open').toBeVisible()
    await sheet(page).getByRole('link', { name: 'Users', exact: true }).click()

    await expect(page).toHaveURL(/\/access\/users$/)
    await expect(sheet(page), 'the sheet closes after the choice').toBeHidden()
  })

  test('returns focus to the Sections button when Escape closes the sheet at 900 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 900, height: 900 })
    await page.goto(siteUrl('/access/users'))

    await sectionsButton(page).click()
    await expect(sheet(page)).toBeVisible()
    await page.keyboard.press('Escape')

    await expect(sheet(page), 'the sheet is closed').toBeHidden()
    await expect(sectionsButton(page), 'focus is back on the button').toBeFocused()
  })

  test('draws no Sections button on the index at /settings at 900 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 900, height: 900 })
    await page.goto(siteUrl('/settings'))

    await expect(page.getByRole('heading', { name: 'Overview', level: 1 })).toBeVisible()
    await expect(sectionsButton(page), 'the index is the tree already').toHaveCount(0)
  })

  test('draws no Sections button and no horizontal scroll at 320 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 320, height: 900 })
    await page.goto(siteUrl('/access/users'))

    await expect(page.getByRole('heading', { name: 'Users', level: 1 })).toBeVisible()
    await expect(sectionsButton(page), 'no Sections button on a phone').toBeHidden()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow, 'no horizontal scrolling in px').toBeLessThanOrEqual(0)
  })
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
      // The back arrow of a repository leads to its list; the list's arrow leads to the Administration index.
      await page.getByRole('link', { name: 'Back to Repositories', exact: true }).click()
      await page.getByRole('link', { name: 'Back to Administration', exact: true }).click()

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

// The header of an administration page at the widths an operator reads it at: the crumbs and their separators sit on one
// row, the description keeps its room when the actions are beside it, a reading page spans its header like a wide page,
// and a detail title never breaks inside a word.
test.describe('the administration page header', () => {
  // Every child of the breadcrumb items, in reading order: a crumb, then its separator when it has one.
  const trailParts = (page) => page.evaluate(() => [...document.querySelectorAll('nav[aria-label="Breadcrumb"] li')]
    .flatMap(item => [...item.children].map(child => {
      const box = child.getBoundingClientRect()
      return { separator: child.getAttribute('aria-hidden') === 'true', top: box.top, bottom: box.bottom, left: box.left, right: box.right }
    })))

  test('draws the crumbs and their separators on one baseline with even spacing at 1440 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(siteUrl('/access/users/e2e-operator'))
    await expect(page.locator('nav[aria-label="Breadcrumb"] li')).toHaveCount(3)

    const parts = await trailParts(page)
    expect(parts.filter(part => part.separator).length, 'a slash between each pair of crumbs').toBe(2)
    const centres = parts.map(part => (part.top + part.bottom) / 2)
    expect(Math.max(...centres) - Math.min(...centres), 'every crumb and separator shares one baseline, in px').toBeLessThan(1)

    const gaps = []
    for (let index = 0; index + 2 < parts.length; index += 2) {
      gaps.push(parts[index + 1].left - parts[index].right, parts[index + 2].left - parts[index + 1].right)
    }
    expect(Math.max(...gaps) - Math.min(...gaps), 'the space on both sides of each slash is the same, in px').toBeLessThan(1)
  })

  test('keeps a detail title on whole words at 320 pixels, with the actions wrapped below it', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 320, height: 900 })
    await page.goto(siteUrl('/access/users/e2e-operator'))
    const title = page.locator('#administration-title')
    await expect(title).toBeVisible()

    const lineHeight = await title.evaluate(element => parseFloat(getComputedStyle(element).lineHeight))
    const height = await title.evaluate(element => element.getBoundingClientRect().height)
    expect(Number.isNaN(lineHeight), 'the title has a pixel line height').toBe(false)
    expect(height, 'the title takes one line, not one line per letter, in px').toBeLessThan(lineHeight * 1.5)

    const titleBox = await title.boundingBox()
    const checkBox = await page.locator('[data-test="check-permission"]').boundingBox()
    expect(checkBox.y, 'the actions start below the title').toBeGreaterThanOrEqual(titleBox.y + titleBox.height - 1)
  })

  test('keeps the description at least 300 pixels wide at 600 pixels, with the actions wrapped below it', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 600, height: 900 })
    await page.goto(siteUrl('/settings'))
    await expect(page.getByRole('button', { name: 'Download as JSON', exact: true })).toBeVisible()

    const text = page.locator('section[data-ingot-component="administration-frame"] > header > div').first()
    const box = await text.boundingBox()
    expect(box.width, 'the text column of the header in px').toBeGreaterThanOrEqual(300)
  })

  test('spans the shortcuts of a reading page to the edge of the header, as a wide page does, at 1440 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(siteUrl('/maintenance'))
    const shortcut = page.getByRole('navigation', { name: 'Related pages', exact: true }).getByRole('link').last()
    await expect(shortcut).toBeVisible()

    const box = await shortcut.boundingBox()
    expect(box.x + box.width, 'the last shortcut ends near the right edge of the container, in px').toBeGreaterThan(1200)
  })

  test('keeps the content column of a reading page at its 960 pixel measure at 1440 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(siteUrl('/maintenance'))
    // The wrapper of the layout is the element after the header of the frame.
    const measure = page.locator('section[data-ingot-component="administration-frame"] > header + div')
    await expect(page.getByRole('heading', { name: 'Maintenance', level: 1 })).toBeVisible()

    const box = await measure.boundingBox()
    expect(box.width, 'the measure of the reading page in px').toBeLessThanOrEqual(960)
  })
})

// The bottom navigation bar below 600 pixels: its four destinations share the width equally, and every label fits its
// share whole, so none is cut with an ellipsis. At 320 pixels the Administration destination shows its short label.
test.describe('the bottom navigation bar at phone width', () => {
  test('shares the width equally and keeps every label whole at 320 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 320, height: 640 })
    await page.goto(siteUrl('/dashboard'))

    const bar = page.locator('[data-ingot-component="md-navigation-bar"]')
    await expect(bar).toBeVisible()
    const labels = bar.locator('li span.md-label-medium')
    await expect(labels, 'the bar has its four destinations').toHaveCount(4)

    const labelFits = await labels.evaluateAll(elements => elements.map(element => ({
      text: element.textContent.trim(),
      scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth,
    })))
    for (const { text, scrollWidth, clientWidth } of labelFits) {
      expect(scrollWidth, `the label "${text}" fits its item in px`).toBeLessThanOrEqual(clientWidth)
    }
    expect(labelFits.map(({ text }) => text), 'the destinations, in order').toEqual(['Browse', 'Search', 'Dashboard', 'Admin'])

    const widths = await bar.locator('li').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().width))
    expect(Math.max(...widths) - Math.min(...widths), 'the four destinations have equal widths in px').toBeLessThanOrEqual(1)
  })

  test('names the short Admin destination by its full name without an accessibility violation at 320 pixels', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 320, height: 640 })
    await page.goto(siteUrl('/dashboard'))

    const bar = page.locator('[data-ingot-component="md-navigation-bar"]')
    await expect(bar).toBeVisible()
    await expect(bar.getByRole('link', { name: 'Administration', exact: true }).or(bar.getByRole('button', { name: 'Administration', exact: true }))).toBeVisible()

    // label-content-name-mismatch is off in the default run: it is enabled here, so the visible label must be part of the name.
    const results = await new AxeBuilder({ page })
      .include('[data-ingot-component="md-navigation-bar"]')
      .withRules(['label-content-name-mismatch'])
      .analyze()
    expect(results.violations.map(violation => violation.id), 'no label-in-name violation in the bar').toEqual([])
  })
})
