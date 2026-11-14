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

// A detail page of the administration names its item in one heading, the h1 of the page frame, and has one way back to
// the list it belongs to. Below 1240 pixels that way back is the back arrow of the frame; from 1240 pixels it is the list
// crumb of the trail. The page content draws no second title and no second back control.
const WIDTHS = [320, 600, 1440]

const headerLinksTo = (page, path) => page
  .locator('section[data-ingot-component="administration-frame"] > header')
  .locator(`a[href="${path}"]`)

// Every control named "Back to ..." on the page, as a link or a button. Below 1240 pixels the frame draws one (its arrow);
// from 1240 pixels the way back is the list crumb, which is named by the list and not by "Back to".
const backControls = (page) => page.getByRole('link', { name: /^Back to / }).or(page.getByRole('button', { name: /^Back to / }))
const expectedBackControls = (width) => (width < 1240 ? 1 : 0)

for (const width of WIDTHS) {
  test.describe(`a detail page at ${width} pixels`, () => {
    test.beforeEach(async ({ adminPage: page }) => {
      await page.setViewportSize({ width, height: 900 })
    })

    test('the user detail has one h1 and one way back to the users', async ({ adminPage: page }) => {
      await page.goto(siteUrl('/access/users/e2e-operator'))
      await expect(page.locator('[data-ingot-component="principal-detail"] [data-test="username"]'), 'the detail has loaded').toHaveText('e2e-operator')

      await expect(page.locator('h1'), 'exactly one h1 on the page').toHaveCount(1)
      await expect(page.locator('h1')).toHaveText('e2e-operator')
      await expect(page.getByText('People who sign in, with their permissions and tokens.', { exact: true }), 'the list description is not reused').toHaveCount(0)
      await expect(headerLinksTo(page, '/access/users'), 'one link to the users in the header').toHaveCount(1)
      await expect(backControls(page), `back controls named "Back to" at ${width} pixels`).toHaveCount(expectedBackControls(width))
    })

    test('the repository detail has one h1 and one way back to the repositories', async ({ adminPage: page, seed }) => {
      const id = await seed.repository('detail-heading')
      await page.goto(siteUrl(`/settings/repositories/${id}`))
      await expect(page.locator('[data-ingot-component="repository-detail"]'), 'the detail has loaded').toBeVisible()

      await expect(page.locator('h1'), 'exactly one h1 on the page').toHaveCount(1)
      await expect(page.locator('h1')).toHaveText(id)
      await expect(page.getByText('Hosted, proxied and group repositories, their policies and Maven options.', { exact: true }), 'the list description is not reused').toHaveCount(0)
      await expect(headerLinksTo(page, '/settings/repositories'), 'one link to the repositories in the header').toHaveCount(1)
      await expect(backControls(page), `back controls named "Back to" at ${width} pixels`).toHaveCount(expectedBackControls(width))
    })
  })
}
