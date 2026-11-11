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
import { unbrokenName } from './support/names.js'

// The lines a box of text spans, from its height and its computed line height.
const linesOf = (locator) => locator.evaluate((element) => {
  const style = getComputedStyle(element)
  const lineHeight = parseFloat(style.lineHeight) || parseFloat(style.fontSize) * 1.2
  return Math.round(element.getBoundingClientRect().height / lineHeight)
})

test.describe('repositories table layout', () => {
  for (const width of [1280, 1920]) {
    test(`keeps a long name and address whole and the row actions on one line at ${width} px`, async ({ adminPage: page, seed }, testInfo) => {
      // Known regression on main: names and addresses break mid-word and the row actions wrap. The fixing
      // change is fix-administration-navigation, which removes this marker.
      test.fail(true, 'fix-administration-navigation')
      // One word, no hyphen: a break inside it can only be a mid-word break.
      const repository = await seed.create(unbrokenName(testInfo, 'long'), { description: 'Long name for the layout check' })
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl('/settings/repositories'))

      const row = page.getByRole('row', { name: new RegExp(repository) })
      await expect(row).toBeVisible()
      const name = row.getByRole('rowheader')
      const address = row.locator('code')
      const actions = row.getByRole('link', { name: `Edit ${repository}`, exact: true })
        .or(row.getByRole('button', { name: `Copy address of ${repository}`, exact: true }))
        .or(row.getByRole('button', { name: `Delete ${repository}`, exact: true }))

      expect(await linesOf(name), `lines of the name ${repository}`).toBe(1)
      expect(await linesOf(address), 'lines of the address').toBe(1)
      const tops = await actions.evaluateAll(elements => elements.map(element => Math.round(element.getBoundingClientRect().top)))
      expect(new Set(tops).size, `the top edges of the three row actions ${tops}`).toBe(1)
    })
  }
})
