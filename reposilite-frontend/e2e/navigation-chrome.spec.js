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

// The chrome of the window: the navigation rail, the top bar and the account menu. The layout is measured in a real
// browser, because the alignment is a matter of boxes, not of classes.
import { test, expect } from './support/fixtures.js'
import { siteUrl } from './support/api.js'
import { ACCOUNT_NAME } from './support/session.js'

const DESKTOP_WIDTHS = [1280, 1920]

// The rail is the fixed column at the left window edge, from the medium window size up.
const rail = (page) => page.locator('.fixed.inset-y-0 > nav')
const railLabels = (page) => rail(page).locator('li a span:last-child, li button span:last-child')
const topBar = (page) => page.locator('header').first()
const accountButton = (page) => page.getByRole('button', { name: ACCOUNT_NAME, exact: true })

// The left edge of the first shared container of the page, past its side padding: where the page content starts.
const contentBox = (page, selector) => page.locator(selector).first().evaluate((element) => {
  const box = element.getBoundingClientRect()
  const style = getComputedStyle(element)
  return {
    left: box.left + parseFloat(style.paddingLeft),
    right: box.right - parseFloat(style.paddingRight)
  }
})

const edgesOf = (locator) => locator.evaluate((element) => {
  const box = element.getBoundingClientRect()
  return { left: box.left, right: box.right, top: box.top, bottom: box.bottom, width: box.width, height: box.height }
})

test.describe('navigation rail', () => {
  for (const width of DESKTOP_WIDTHS) {
    test(`keeps every label on one line at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))
      await expect(rail(page).getByRole('link', { name: 'Browse', exact: true })).toBeVisible()

      const labels = await railLabels(page).evaluateAll(elements => elements.map(element => ({
        text: element.textContent.trim(),
        height: element.getBoundingClientRect().height,
        lineHeight: parseFloat(getComputedStyle(element).lineHeight),
        cut: element.scrollWidth > element.clientWidth,
        title: element.getAttribute('title'),
      })))

      expect(labels.length, 'the rail lists the sections the signed in visitor may open').toBeGreaterThanOrEqual(2)
      for (const label of labels) {
        expect(label.height, `"${label.text}" takes one line`).toBeLessThanOrEqual(label.lineHeight * 1.5)
        if (label.cut) {
          expect(label.title, `"${label.text}" is cut, so it names itself in full on hover`).toBe(label.text)
        }
      }
    })

    test(`marks the page on screen as the current entry at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))

      await expect(rail(page).locator('[aria-current="page"]')).toHaveText('Browse')
    })
  }
})

test.describe('top bar alignment', () => {
  for (const width of DESKTOP_WIDTHS) {
    test(`starts the title where the page content starts at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))
      const title = await edgesOf(topBar(page).locator('h1'))
      const content = await contentBox(page, 'main .container')

      expect(Math.abs(title.left - content.left), 'the title and the content share one left edge').toBeLessThanOrEqual(2)
    })

    test(`starts the title where the administration content starts at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/settings/repositories'))
      await expect(page.getByRole('heading', { name: 'Administration', level: 1 })).toBeVisible()
      const title = await edgesOf(topBar(page).locator('h1'))
      const content = await contentBox(page, 'main .container')

      expect(Math.abs(title.left - content.left), 'the title and the frame share one left edge').toBeLessThanOrEqual(2)
    })

    test(`ends the account button at the content right edge at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))
      const button = await edgesOf(accountButton(page))
      const content = await contentBox(page, 'main .container')

      expect(Math.abs(button.right - content.right), 'the actions end where the content ends').toBeLessThanOrEqual(2)
    })

    test(`runs the top bar from the rail to the window edge at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))
      const bar = await edgesOf(topBar(page))
      const navigation = await edgesOf(rail(page))

      expect(bar.left, 'the bar starts at the right edge of the rail').toBe(navigation.right)
      expect(bar.right, 'the bar reaches the window edge').toBe(width)
      expect(bar.top, 'the bar is at the top of the window').toBe(0)
    })

    test(`draws the brand rule across the whole top bar at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))
      const bar = await edgesOf(topBar(page))
      const rule = await edgesOf(topBar(page).locator('.olf-brand-bar'))

      expect(rule.left, 'the rule starts at the bar edge').toBe(bar.left)
      expect(rule.right, 'the rule ends at the bar edge').toBe(bar.right)
      expect(rule.bottom, 'the rule sits on the bottom edge of the bar').toBe(bar.bottom)
    })

    test(`runs the rail the full height of the window at ${width}px`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 1080 })
      await page.goto(siteUrl('/'))
      const navigation = await edgesOf(rail(page).locator('xpath=..'))

      expect(navigation.top, 'the rail starts at the top window edge').toBe(0)
      expect(navigation.height, 'the rail reaches the bottom window edge').toBe(1080)
    })
  }

  test('leaves no empty corner above the rail', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))
    // The corner is the first cell of the rail: as tall as the top bar, with the logo centred in it.
    const corner = await edgesOf(rail(page).locator(':scope > div').first())
    const logo = await edgesOf(rail(page).getByRole('link', { name: /start page$/ }))

    expect(corner.top, 'the corner starts at the top window edge').toBe(0)
    expect(corner.height, 'the corner is as tall as the top bar').toBe(64)
    expect(logo.top, 'the logo sits inside the corner').toBeGreaterThanOrEqual(corner.top)
    expect(logo.bottom, 'the logo sits inside the corner').toBeLessThanOrEqual(corner.bottom)
    const entry = await edgesOf(rail(page).getByRole('link', { name: 'Browse', exact: true }))
    expect(entry.top, 'the entries start below the corner').toBeGreaterThanOrEqual(corner.bottom)
  })
})

test.describe('account menu', () => {
  test('shows the name of the signed in user beside the avatar from 900px up', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))

    const name = await edgesOf(accountButton(page).locator('[data-test="account-name"]'))
    expect(name.width, 'the name takes room in the bar').toBeGreaterThan(40)
  })

  test('takes the name out of the layout on a window narrower than 900px, and keeps the avatar', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 800, height: 1080 })
    await page.goto(siteUrl('/'))

    await expect(accountButton(page), 'the button is still there, and still named').toBeVisible()
    const name = await edgesOf(accountButton(page).locator('[data-test="account-name"]'))
    expect(name.width, 'the name is screen reader only, one pixel wide').toBeLessThanOrEqual(1)
  })

  test('opens from the keyboard, moves with the arrow keys, and returns the focus on Escape', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))
    const button = accountButton(page)
    await button.focus()

    await page.keyboard.press('Enter')
    const menu = page.getByRole('menu')
    await expect(menu, 'Enter opens the menu').toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'My account', exact: true })).toBeFocused()

    await page.keyboard.press('ArrowDown')
    await expect(page.getByRole('menuitemradio', { name: 'Light', exact: true }), 'an arrow key moves to the next item').toBeFocused()

    await page.keyboard.press('Escape')
    await expect(menu, 'Escape closes the menu').toBeHidden()
    await expect(button, 'the focus returns to the account button').toBeFocused()
  })

  test('offers the theme choice inside the menu and changes the theme', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))
    await accountButton(page).click()

    await page.getByRole('menuitemradio', { name: 'Dark', exact: true }).click()

    await expect(page.locator('html')).toHaveClass(/dark/)
  })

  test('signs the user out from the menu', async ({ adminPage: page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))
    await accountButton(page).click()

    await page.getByRole('menuitem', { name: 'Logout', exact: true }).click()

    await expect(accountButton(page)).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Sign in', exact: true }).first()).toBeVisible()
  })
})

test.describe('skip link', () => {
  test('is the first stop of the keyboard and moves the focus to the page content', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))

    await page.keyboard.press('Tab')
    const skip = page.getByRole('link', { name: 'Skip to main content', exact: true })
    await expect(skip, 'the first Tab reaches the skip link').toBeFocused()

    await page.keyboard.press('Enter')
    await expect(page.locator('#main'), 'the page content has the focus').toBeFocused()
  })
})

test.describe('top bar for a visitor who is not signed in', () => {
  test('keeps a visible Sign in button and no account menu', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 1080 })
    await page.goto(siteUrl('/'))

    await expect(topBar(page).getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
    await expect(accountButton(page)).toHaveCount(0)
  })
})
