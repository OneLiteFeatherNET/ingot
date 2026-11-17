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
import { ACCOUNT_NAME } from './support/session'

// The account menu and the My account page as the signed-in operator sees them, at a phone, a tablet and a desktop width.
// Selectors are roles and accessible names only, and nothing waits for a fixed time: each expectation waits on its own state.
const WIDTHS = [320, 600, 1440]

const SECTIONS = ['Profile', 'Sign-in and security', 'My tokens', 'Your access']

const accountTrigger = (page) => page.getByRole('button', { name: `Account menu, ${ACCOUNT_NAME}`, exact: true })

for (const width of WIDTHS) {
  test.describe(`the account page at ${width} pixels`, () => {
    test.use({ viewport: { width, height: 900 } })

    test('shows the name once in the bar and never again inside the open menu', async ({ adminPage: page }) => {
      await page.goto(siteUrl('/account'))

      await accountTrigger(page).click()
      const menu = page.getByRole('menu')

      await expect(menu, 'the menu opens from the account button').toBeVisible()
      await expect(menu, 'the menu does not repeat the display name').not.toContainText(ACCOUNT_NAME)
    })

    test('opens the menu with Enter and closes it with Escape, which returns focus to the account button', async ({ adminPage: page }) => {
      await page.goto(siteUrl('/account'))

      const trigger = accountTrigger(page)
      await trigger.focus()
      await page.keyboard.press('Enter')

      const menu = page.getByRole('menu')
      await expect(menu).toBeVisible()

      await page.keyboard.press('Escape')
      await expect(menu).toBeHidden()
      await expect(trigger, 'focus goes back to the button that opened the menu').toBeFocused()
    })

    test('fits the window without a horizontal scroll', async ({ adminPage: page }) => {
      await page.goto(siteUrl('/account'))
      await expect(page.getByRole('heading', { name: 'Your access', exact: true })).toBeVisible()

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)

      expect(overflow, 'the page is no wider than the window').toBeLessThanOrEqual(0)
    })

    test('draws the four sections in order: Profile, Sign-in and security, My tokens, Your access', async ({ adminPage: page }) => {
      await page.goto(siteUrl('/account'))
      await expect(page.getByRole('heading', { name: 'Your access', exact: true })).toBeVisible()

      const headings = (await page.getByRole('heading', { level: 3 }).allTextContents()).filter((text) => SECTIONS.includes(text))

      expect(headings, 'each section has its heading, in this order').toEqual(SECTIONS)
    })

    test('shows Your access as read only, with its rights and an Explain link for each', async ({ adminPage: page }) => {
      await page.goto(siteUrl('/account'))

      const access = page.getByRole('region', { name: 'Your access', exact: true })
      await expect(access).toBeVisible()
      await expect(access.getByRole('button'), 'no button changes anything').toHaveCount(0)
      await expect(access.getByRole('textbox'), 'no field changes anything').toHaveCount(0)

      const explain = access.getByRole('link', { name: /^Explain / })
      await expect(explain.first(), 'the signed-in operator has at least one right to explain').toBeVisible()
      await expect(access.getByRole('link', { name: 'Explain ingot.admin.*', exact: true }), 'the instance administration right is named with its node').toBeVisible()
    })
  })
}
