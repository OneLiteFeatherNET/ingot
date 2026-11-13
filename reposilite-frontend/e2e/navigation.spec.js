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
import { signInAsAdmin } from './support/session.js'

// Every Administration page that the side navigation of the tree is drawn on, for an instance admin.
const ADMINISTRATION_PAGES = [
  '/settings',
  '/settings/repositories',
  '/settings/audit',
  '/access/users',
  '/access/teams',
  '/audit',
]

// The links of the Administration side navigation, in order, with the current one marked. The settings domains join
// the tree when the session's domain list arrives, after the page itself, so the read waits for two of them first.
const administrationLinks = async (page) => {
  const tree = page.getByRole('navigation', { name: 'Administration', exact: true })
  await expect(tree.getByRole('link', { name: 'Javadoc', exact: true })).toBeVisible()
  await expect(tree.getByRole('link', { name: 'Audit settings', exact: true })).toBeVisible()
  return tree.getByRole('link').evaluateAll(links => links.map(link => ({
    name: link.textContent.trim(),
    current: link.getAttribute('aria-current') === 'page',
  })))
}

test.describe('administration navigation', () => {
  test('lists the same Administration entries in the same order on every page', async ({ page }) => {
    await signInAsAdmin(page)

    const listings = {}
    for (const path of ADMINISTRATION_PAGES) {
      await page.goto(siteUrl(path))
      listings[path] = (await administrationLinks(page)).map(link => link.name)
    }
    const [first, ...others] = ADMINISTRATION_PAGES
    for (const path of others) {
      expect(listings[path], `the Administration entries on ${path} compared with ${first}`).toEqual(listings[first])
    }
  })

  test('marks exactly one Administration entry as current on the audit page', async ({ page }) => {
    await signInAsAdmin(page)

    await page.goto(siteUrl('/settings/audit'))
    await expect(page.getByRole('navigation', { name: 'Administration', exact: true })).toBeVisible()
    const current = (await administrationLinks(page)).filter(link => link.current)
    expect(current.map(link => link.name), 'the current Administration entries').toHaveLength(1)
  })

  test('shows no Administration entry to a visitor without admin rights', async ({ page }) => {
    await page.goto(siteUrl('/'))
    await expect(page.getByRole('navigation', { name: 'Primary' }).first()).toBeVisible()
    await expect(page.getByRole('link', { name: 'Administration', exact: true })).toHaveCount(0)
  })

  // The page is clipped at its sides (the sheet is overflow-x: clip), so the document width alone misses content that is
  // cut off. This also lists every box that ends beyond the window, unless a scroll container around it can reach it.
  const contentBeyondWindow = (page) => page.evaluate(() => {
    const window = document.documentElement.clientWidth
    const reachable = (element) => {
      for (let node = element.parentElement; node && node !== document.body; node = node.parentElement) {
        const overflow = getComputedStyle(node).overflowX
        if (overflow === 'auto' || overflow === 'scroll') return true
      }
      return false
    }
    return [...document.body.querySelectorAll('*')]
      .filter(element => {
        const box = element.getBoundingClientRect()
        return box.width > 0 && box.height > 0 && box.right > window + 0.5 && !reachable(element)
      })
      .map(element => `${element.tagName.toLowerCase()} "${element.textContent.trim().slice(0, 40)}"`)
  })

  for (const width of [320, 375, 600]) {
    test(`keeps the side navigation reachable at ${width} px without horizontal scrolling`, async ({ page }) => {
      await signInAsAdmin(page)
      await page.setViewportSize({ width, height: 640 })

      await page.goto(siteUrl('/settings'))
      const audit = page.getByRole('link', { name: 'Audit log', exact: true })
      await expect(audit).toBeVisible()
      const box = await audit.boundingBox()
      expect(box.x, 'the link starts inside the viewport').toBeGreaterThanOrEqual(0)
      expect(box.x + box.width, 'the link ends inside the viewport').toBeLessThanOrEqual(width)

      await audit.click()
      await expect(page).toHaveURL(/\/audit$/)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow, 'horizontal overflow of the page in px').toBeLessThanOrEqual(0)
      expect(await contentBeyondWindow(page), 'boxes cut off at the window edge').toEqual([])
    })
  }
})
