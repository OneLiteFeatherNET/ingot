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

// The lines the text of an element occupies, counted from the boxes of its text. The box of a table cell is not
// used: a cell is as tall as its row, so a tall neighbour would count as lines of the name.
const linesOfText = (locator) => locator.evaluate((element) => {
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
      expect(await linesOfText(actions), 'lines of the Actions button').toBe(1)
    })
  }
})

test.describe('repository page controls', () => {
  test('shows one danger zone on a repository page, and no factory reset', async ({ adminPage: page, seed }, testInfo) => {
    const repository = await seed.create(unbrokenName(testInfo, 'zone'), { description: 'Danger zone check' })
    await page.goto(siteUrl(`/settings/repositories/${repository}`))

    await expect(page.getByRole('region', { name: 'Danger zone', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Danger zone', exact: true }), 'the repository has one danger zone, the one that deletes it').toHaveCount(1)
    await expect(page.getByRole('button', { name: 'Factory reset', exact: true }), 'a factory reset belongs to the settings page, not to one repository').toHaveCount(0)
  })
})
