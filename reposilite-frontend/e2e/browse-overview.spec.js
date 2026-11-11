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

test.describe('browse overview', () => {
  test('shows the full repository URL in the build snippet without horizontal scrolling', async ({ page, seed }) => {
    // Known regression on main: the snippet scrolls sideways instead of showing the address. The fixing change
    // is fix-browse-overview, which removes this marker.
    test.fail(true, 'fix-browse-overview')
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
    // Known regression on main: the filter field is named for its placeholder and does not say that it filters
    // the list. The fixing change is fix-browse-overview, which removes this marker.
    test.fail(true, 'fix-browse-overview')
    await page.goto(siteUrl('/'))
    await expect(page.getByRole('searchbox', { name: /filter/i }), 'the repository filter field').toBeVisible()
  })
})
