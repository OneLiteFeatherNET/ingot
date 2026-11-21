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

// Repositories that make the Repositories page taller than the window.
const FILLER_REPOSITORIES = 8

const topOf = (locator: Locator) => locator.evaluate(element => element.getBoundingClientRect().top)
const bottomOfBar = (page: Page) => page.getByRole('banner').evaluate(element => element.getBoundingClientRect().bottom)

// Opens another Administration page the way a visitor does: the tree link when the tree is beside the page, the
// Sections sheet below 1240 pixels.
const openAdministrationPage = async (page: Page, name: string) => {
  const link = page.getByRole('navigation', { name: 'Administration', exact: true }).getByRole('link', { name, exact: true })
  if (await link.isVisible()) {
    await link.click()
    return
  }
  await page.getByRole('button', { name: 'Sections', exact: true }).click()
  await page.getByRole('dialog', { name: 'Administration pages', exact: true }).getByRole('link', { name, exact: true }).click()
}

test.describe('the page title and the top bar', () => {
  for (const width of [320, 600, 1440]) {
    test.describe(`at ${width} pixels`, () => {
      test.beforeEach(async ({ adminPage: page, seed }) => {
        for (let index = 0; index < FILLER_REPOSITORIES; index++) {
          await seed.repository(`title-filler-${index}`)
        }
        // Tall enough that the tree link is in view at 320 pixels, so that Playwright does not scroll to it before the click.
        await page.setViewportSize({ width, height: 900 })
      })

      test('keeps the title below the top bar after a navigation from the top of Overview', async ({ adminPage: page }) => {
        await page.goto(siteUrl('/settings'))
        await expect(page.getByRole('heading', { level: 1, name: 'Overview', exact: true })).toBeVisible()
        await expect.poll(() => page.evaluate(() => window.scrollY), 'the precondition: Overview is at the top').toBe(0)

        await openAdministrationPage(page, 'Repositories')
        const title = page.getByRole('heading', { level: 1, name: 'Repositories', exact: true })
        await expect(title).toBeVisible()
        // A page change moves the focus to the heading of the new page.
        await expect(title).toBeFocused()

        expect(await topOf(title), 'the top of the title is below the bottom of the top bar').toBeGreaterThanOrEqual(await bottomOfBar(page))
      })

      test('keeps the title below the top bar after the focus moves to the page at the top', async ({ adminPage: page }) => {
        await page.goto(siteUrl('/settings/repositories'))
        const title = page.getByRole('heading', { level: 1, name: 'Repositories', exact: true })
        await expect(title).toBeVisible()
        await page.evaluate(() => {
          window.scrollTo(0, 0)
          const active = document.activeElement as HTMLElement | null
          active?.blur()
        })
        await expect.poll(() => page.evaluate(() => window.scrollY), 'the precondition: the page is at the top').toBe(0)

        await page.evaluate(() => document.getElementById('main')!.focus())
        await expect(page.locator('#main')).toBeFocused()

        expect(await topOf(title), 'the top of the title is below the bottom of the top bar').toBeGreaterThanOrEqual(await bottomOfBar(page))
      })
    })
  }
})
