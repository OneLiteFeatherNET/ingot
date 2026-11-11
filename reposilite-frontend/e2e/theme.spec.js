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

const LIGHT_LABEL = 'Theme: light. Switch to dark.'

test.describe('theme toggle', () => {
  test('keeps the chosen theme after a reload', async ({ page }) => {
    await page.goto(siteUrl('/'))
    const toggle = page.getByRole('button', { name: /^Theme: / })
    await toggle.click()
    await expect(page.getByRole('button', { name: LIGHT_LABEL, exact: true })).toBeVisible()

    await page.reload()
    await expect(page.getByRole('button', { name: LIGHT_LABEL, exact: true }), 'the theme after the reload').toBeVisible()
  })
})
