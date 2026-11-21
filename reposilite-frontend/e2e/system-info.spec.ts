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

import type { Page } from '@playwright/test'
import { test, expect } from './support/fixtures'
import { siteUrl } from './support/api'
import { analyzeAccessibility } from './support/accessibility'
import { signInAsAdmin } from './support/session'

// The system information page, which also holds the support bundle. Tabs are found by role and name, and the layout is
// read from the boxes of the overview cards, so no test waits for a fixed time.
const TABS = ['Overview', 'Plugins', 'Environment', 'Configuration']
const DOWNLOAD = 'Download support bundle'

const tab = (page: Page, name: string) => page.getByRole('tab', { name, exact: true })

test.describe('system information and support page', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsAdmin(page)
  })

  test('redirects the old support address and keeps its query and fragment', async ({ page }) => {
    await page.goto(siteUrl('/settings/support?tab=configuration#support-configuration'))

    await expect(page).toHaveURL(/\/settings\/system-info\?tab=configuration#support-configuration$/)
    await expect(tab(page, 'Configuration')).toHaveAttribute('aria-selected', 'true')
  })

  test('opens the tab named in the address, and keeps it on reload', async ({ page }) => {
    await page.goto(siteUrl('/settings/system-info?tab=environment'))
    await expect(tab(page, 'Environment')).toHaveAttribute('aria-selected', 'true')

    await page.reload()

    await expect(tab(page, 'Environment')).toHaveAttribute('aria-selected', 'true')
  })

  test('opens the tab of a section anchor and scrolls the section into view', async ({ page }) => {
    await page.goto(siteUrl('/settings/system-info#support-properties'))

    await expect(tab(page, 'Environment')).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('heading', { name: 'System properties', exact: true })).toBeInViewport()
  })

  test('moves between the tabs with the arrow keys, Home and End', async ({ page }) => {
    await page.goto(siteUrl('/settings/system-info'))
    await tab(page, 'Overview').focus()

    await page.keyboard.press('ArrowRight')
    await expect(tab(page, 'Plugins')).toBeFocused()
    await expect(tab(page, 'Plugins')).toHaveAttribute('aria-selected', 'true')

    await page.keyboard.press('End')
    await expect(tab(page, 'Configuration')).toBeFocused()

    await page.keyboard.press('Home')
    await expect(tab(page, 'Overview')).toBeFocused()
  })

  test('offers the support bundle download on every tab', async ({ page }) => {
    await page.goto(siteUrl('/settings/system-info'))

    for (const name of TABS) {
      await tab(page, name).click()
      await expect(page.getByRole('button', { name: DOWNLOAD, exact: true })).toBeVisible()
    }
  })

  test('lays the overview cards out in three columns wide and one column at phone width', async ({ page }) => {
    const columnsAt = async (width: number) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/settings/system-info'))
      await expect(page.getByRole('article')).toHaveCount(6)
      const cards = await page.getByRole('article').all()
      const boxes = await Promise.all(cards.map((card) => card.boundingBox()))
      return new Set(boxes.map((box) => Math.round(box!.x))).size
    }

    expect(await columnsAt(1440), 'three columns at 1440 pixels').toBe(3)
    expect(await columnsAt(320), 'one column at 320 pixels').toBe(1)
  })

  test('uses the full width at 1440 pixels, and no card is wider than its box', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(siteUrl('/settings/system-info'))
    await expect(page.getByRole('article')).toHaveCount(6)

    const tabList = await page.getByRole('tablist').boundingBox()
    expect(tabList!.width, 'the tab bar runs past the 960 pixel reading measure').toBeGreaterThan(960)

    const overflowing = await page.getByRole('article').evaluateAll((cards) => cards
      .filter((card) => card.scrollWidth > card.clientWidth)
      .map((card) => card.getAttribute('aria-labelledby')))
    expect(overflowing, 'cards whose content is wider than the card').toEqual([])
  })

  test('does not scroll the page sideways at phone width', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 })
    await page.goto(siteUrl('/settings/system-info'))

    for (const name of TABS) {
      await tab(page, name).click()
      const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
      expect(fits, `no sideways scroll on ${name} at 320 pixels`).toBe(true)
    }
  })

  test('keeps all tabs on one row at 390 pixels, with the deep-linked tab in view and no sideways page scroll', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 900 })
    await page.goto(siteUrl('/settings/system-info?tab=configuration'))
    await expect(tab(page, 'Configuration')).toHaveAttribute('aria-selected', 'true')
    await expect(tab(page, 'Configuration')).toBeInViewport()
    expect(await page.evaluate(() => window.scrollY), 'a deep link does not scroll the page vertically').toBe(0)

    const rows = await Promise.all(TABS.map(async (name) => Math.round((await tab(page, name).boundingBox())!.y)))
    expect(new Set(rows).size, 'every tab sits on the same row').toBe(1)

    const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)
    expect(fits, 'the page does not scroll sideways at 390 pixels').toBe(true)
  })

  for (const width of [320, 600, 1440]) {
    test(`has no serious or critical violations on each tab at ${width} pixels`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/settings/system-info'))

      for (const name of TABS) {
        await tab(page, name).click()
        const { gating, reported } = await analyzeAccessibility(page)
        await testInfo.attach(`axe-findings-${name}-${width}`, {
          body: JSON.stringify({ gating, reported }, null, 2),
          contentType: 'application/json',
        })
        expect(gating, `serious and critical violations on the ${name} tab at ${width} pixels`).toEqual([])
      }
    })
  }
})
