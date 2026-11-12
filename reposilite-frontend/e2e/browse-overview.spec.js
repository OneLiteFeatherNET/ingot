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

test.describe('browse overview', () => {
  test('shows the full repository URL in the build snippet without horizontal scrolling', async ({ page, seed }) => {
    // A long identifier, close to the longest name the server accepts, so that the address is wider than the snippet.
    const repository = await seed.repository('long-address-snippet')
    await page.goto(siteUrl(`/browse/${repository}`))

    // The snippet is the block that holds the Maven repository element for this repository.
    const snippet = page.locator('pre, code').filter({ hasText: `/${repository}</url>` }).first()
    await expect(snippet, 'the snippet with the repository address').toContainText(`/${repository}`)
    const sideways = await snippet.evaluate((element) => {
      const scroller = [element, ...(function* ancestors() {
        for (let node = element.parentElement; node; node = node.parentElement) yield node
      })()].find(node => {
        const overflow = getComputedStyle(node).overflowX
        return overflow === 'auto' || overflow === 'scroll' || overflow === 'hidden'
      }) ?? element
      return scroller.scrollWidth - scroller.clientWidth
    })
    expect(sideways, 'horizontal overflow of the snippet container in px').toBeLessThanOrEqual(0)
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
