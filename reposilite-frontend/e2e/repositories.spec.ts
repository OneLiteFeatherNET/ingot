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

import type { Locator } from '@playwright/test'
import { test, expect } from './support/fixtures'
import { siteUrl } from './support/api'
import { unbrokenName } from './support/names'

// The lines the text of an element occupies, counted from the boxes of its text. The box of a table cell is not
// used: a cell is as tall as its row, so a tall neighbour would count as lines of the name.
const linesOfText = (locator: Locator) => locator.evaluate((element) => {
  const range = document.createRange()
  range.selectNodeContents(element)
  return new Set([...range.getClientRects()].map(box => Math.round(box.top))).size
})

test.describe('repositories table layout', () => {
  for (const width of [1280, 1920]) {
    test(`keeps a long name, its address and the Actions button on one line each at ${width} px`, async ({ adminPage: page, seed }, testInfo) => {
      // One word, no hyphen: a break inside it can only be a mid-word break.
      const repository = await seed.create(unbrokenName(testInfo, 'long'), { description: 'Long name for the layout check' })
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/settings/repositories'))

      const row = page.getByRole('row', { name: new RegExp(repository) })
      await expect(row).toBeVisible()
      const name = row.getByRole('link', { name: repository, exact: true })
      const address = row.locator('code')
      const actions = row.getByRole('button', { name: `Actions for ${repository}`, exact: true })

      expect(await linesOfText(name), `lines of the name ${repository}`).toBe(1)
      expect(await linesOfText(address), 'lines of the address').toBe(1)
      // The Actions button is an icon with no visible text, so it cannot wrap: its box stays one touch target tall.
      const actionsBox = await actions.boundingBox()
      expect(actionsBox!.height, 'the height of the Actions button').toBeLessThanOrEqual(56)
    })
  }

  test('centres the type badge, the name and the Actions button of a row on one line at 1920 px', async ({ adminPage: page, seed }, testInfo) => {
    const repository = await seed.create(unbrokenName(testInfo, 'centre'), { description: 'Centre line check' })
    await page.setViewportSize({ width: 1920, height: 900 })
    await page.goto(siteUrl('/settings/repositories'))

    const row = page.getByRole('row', { name: new RegExp(repository) })
    await expect(row).toBeVisible()
    const badge = row.locator('[data-test="type-badge"]').first()
    const name = row.getByRole('link', { name: repository, exact: true })
    const actions = row.getByRole('button', { name: `Actions for ${repository}`, exact: true })

    // The vertical centre of each element, from its box. Differing centres are the visible asymmetry of the row.
    const centres = await Promise.all([badge, name, actions].map(async (locator) => {
      const box = await locator.boundingBox()
      return box!.y + box!.height / 2
    }))
    const spread = Math.max(...centres) - Math.min(...centres)
    expect(spread, `the centres of the badge, the name and the Actions button of ${repository} differ by ${spread.toFixed(1)} px`).toBeLessThanOrEqual(2)
  })
})

test.describe('repository page controls', () => {
  test('shows one danger zone on a repository page, and no factory reset', async ({ adminPage: page, seed }, testInfo) => {
    const repository = await seed.create(unbrokenName(testInfo, 'zone'), { description: 'Danger zone check' })
    await page.goto(siteUrl(`/settings/repositories/${repository}`))

    await expect(page.getByRole('region', { name: 'Danger zone', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Danger zone', exact: true }), 'the repository has one danger zone, the one that deletes it').toHaveCount(1)
    await expect(page.getByRole('button', { name: 'Factory reset', exact: true }), 'a factory reset belongs to the settings page, not to one repository').toHaveCount(0)
  })

  test('enters the tab panel of a repository from the side of the new tab and keeps the address on the selection', async ({ adminPage: page, seed }) => {
    const repository = await seed.repository('tab-motion')
    await page.goto(siteUrl(`/settings/repositories/${repository}`))
    const tab = (name: string) => page.getByRole('tab', { name, exact: true })
    await expect(tab('General')).toHaveAttribute('aria-selected', 'true')
    // Records every motion class a tab panel is given from now on, so the direction is read from the classes the
    // transition used and no test waits for a time.
    await page.evaluate(() => {
      const seen = new Set<string>()
      ;(window as unknown as { __motionClasses: Set<string> }).__motionClasses = seen
      new MutationObserver((records) => {
        for (const record of records) {
          for (const name of (record.target as Element).classList) if (name.startsWith('motion-shared-x')) seen.add(name)
        }
      }).observe(document.body, { subtree: true, attributes: true, attributeFilter: ['class'] })
    })
    const classes = () => page.evaluate(() => [...(window as unknown as { __motionClasses: Set<string> }).__motionClasses])

    await tab('Storage').click()
    await expect(page).toHaveURL(/\?tab=storage$/)
    await expect.poll(classes, { message: 'a tab to the right enters from the right' }).toContain('motion-shared-x-enter-active')
    expect(await classes(), 'a forward change uses no backward class').not.toContain('motion-shared-x-back-enter-active')

    await tab('General').click()
    await expect(page).toHaveURL(/\/settings\/repositories\/[^?]+(\?tab=general)?$/)
    await expect.poll(classes, { message: 'a tab to the left enters from the left' }).toContain('motion-shared-x-back-enter-active')
  })
})
