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
import { signInAsAdmin } from './support/session'

test.describe('browse overview', () => {
  test('keeps the full repository URL of the build snippet on one line, scrolling sideways instead of breaking it', async ({ page, seed }) => {
    // A long identifier, close to the longest name the server accepts, so that the address is wider than the snippet.
    const repository = await seed.repository('long-address-snippet')
    await page.goto(siteUrl(`/browse/${repository}`))

    // The snippet is the block that holds the Maven repository element for this repository.
    const snippet = page.locator('[data-ingot-component="repository-snippet"]').first()
    await expect(snippet, 'the snippet with the repository address').toContainText(`/${repository}`)
    const url = snippet.locator('.snippet-url').first()
    expect(await url.evaluate(element => element.getClientRects().length), 'lines the url is broken into').toBe(1)
    // The snippet scrolls inside its own region; the page itself never scrolls sideways.
    const pageSideways = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(pageSideways, 'horizontal overflow of the page in px').toBeLessThanOrEqual(0)
  })

  test('shows the snippet copy button inside the code field only while the field is hovered or focused', async ({ page, seed }) => {
    const repository = await seed.repository('copy-hover-snippet')
    await page.goto(siteUrl(`/browse/${repository}`))

    // The code field is the box that holds the scrolling code region; the button is its child.
    const field = page.getByRole('region', { name: /snippet, scrolls sideways/ }).first().locator('..')
    const copy = field.getByRole('button', { name: 'Copy snippet' })
    await expect(copy, 'the copy button inside the code field').toBeVisible()
    // The button reveals itself through its wrapper, so the wrapper carries the opacity.
    const reveal = copy.locator('..')

    await expect(reveal, 'hidden while the pointer is elsewhere').toHaveCSS('opacity', '0')
    await field.hover()
    await expect(reveal, 'shown while the code field is hovered').toHaveCSS('opacity', '1')

    await page.mouse.move(0, 0)
    await expect(reveal, 'hidden again once the pointer leaves').toHaveCSS('opacity', '0')
    await copy.focus()
    await expect(reveal, 'shown while the button has keyboard focus').toHaveCSS('opacity', '1')
  })

  test('labels the repository filter as a filter of the list', async ({ page }) => {
    await page.goto(siteUrl('/'))
    await expect(page.getByRole('searchbox', { name: /filter/i }), 'the repository filter field').toBeVisible()
  })

  test('keeps the destination path of the upload heading inside a 320 px screen', async ({ page, seed }) => {
    const repository = await seed.repository('long-upload-heading')
    await signInAsAdmin(page)
    await page.setViewportSize({ width: 320, height: 640 })
    await page.goto(siteUrl(`/browse/${repository}`))

    const destination = page.locator('#browser-upload h2 span')
    await expect(destination, 'the destination of the upload heading').toContainText(repository)
    const beyond = await destination.evaluate(element => element.getBoundingClientRect().right - document.documentElement.clientWidth)
    expect(beyond, 'the destination path ends inside the window, in px past the edge').toBeLessThanOrEqual(0)
  })
})

test.describe('browse overview on a touch screen', () => {
  test.use({ hasTouch: true, isMobile: true })

  test('keeps the snippet copy button shown without a pointer hover', async ({ page, seed }) => {
    const repository = await seed.repository('copy-touch-snippet')
    await page.goto(siteUrl(`/browse/${repository}`))

    // Without hover, the button is not faded out: the wrapper that carries the opacity is fully shown.
    const copy = page.getByRole('region', { name: /snippet, scrolls sideways/ }).first().locator('..').getByRole('button', { name: 'Copy snippet' })
    await expect(copy, 'the copy button inside the code field').toBeVisible()
    await expect(copy.locator('..'), 'shown on a device without hover').toHaveCSS('opacity', '1')
  })
})
