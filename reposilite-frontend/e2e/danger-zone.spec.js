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

// The number of lines the label of a control is drawn on. A label that wraps has one text box per line, and the
// boxes of one line share a top edge.
const linesOf = (locator) => locator.evaluate((element) => {
  const range = document.createRange()
  range.selectNodeContents(element)
  const tops = new Set(Array.from(range.getClientRects(), rect => Math.round(rect.top)))
  return tops.size
})

test.describe('the danger zone of the system and support page', () => {
  for (const width of [320, 600, 1440]) {
    test(`keeps the factory reset label on one line at ${width} pixels`, async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/settings/system-info'))

      const reset = page.getByRole('button', { name: 'Factory reset', exact: true })
      await expect(reset).toBeVisible()
      expect(await linesOf(reset), 'the number of lines the factory reset label is drawn on').toBe(1)
    })
  }
})
