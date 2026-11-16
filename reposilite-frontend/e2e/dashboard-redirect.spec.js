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
import { createPrincipal, createPrincipalToken, deletePrincipal, setPrincipalNodes, siteUrl } from './support/api'

// The old Dashboard address opens Overview for every visitor: the administrator sees the status there, an anonymous
// visitor gets the sign in prompt at /settings, and a signed in visitor without an admin node gets the no access state.
test.describe('the retired Dashboard address', () => {
  test('opens Overview with the instance status for an administrator', async ({ adminPage: page }) => {
    await page.goto(siteUrl('/dashboard?from=email'))

    await expect(page).toHaveURL(siteUrl('/settings?from=email'))
    await expect(page.getByRole('region', { name: 'Instance status' })).toBeVisible()
  })

  test('opens Overview with the sign in prompt for an anonymous visitor', async ({ page }) => {
    await page.goto(siteUrl('/dashboard'))

    await expect(page).toHaveURL(siteUrl('/settings'))
    await expect(page.getByRole('button', { name: 'Sign in', exact: true }).first(), 'the sign in prompt is shown').toBeVisible()
  })

  test('opens Overview with the no access state for a signed in visitor without an admin node', async ({ page, seed }) => {
    const username = seed.name('no-node')
    const tokenName = `${username}-token`
    await createPrincipal({ kind: 'USER', name: username, displayName: 'No node' })

    try {
      await setPrincipalNodes(username, [])
      const secret = await createPrincipalToken(username, tokenName)

      await page.goto(siteUrl('/'))
      await page.getByRole('button', { name: 'Sign in', exact: true }).first().click()
      const dialog = page.getByRole('dialog', { name: 'Sign in' })
      await dialog.getByRole('button', { name: 'Use an access token instead' }).click()
      await dialog.getByLabel('Name', { exact: true }).fill(tokenName)
      await dialog.getByLabel('Secret', { exact: true }).fill(secret)
      await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
      await expect(dialog, 'the token signs the visitor in').toBeHidden()

      await page.goto(siteUrl('/dashboard'))

      await expect(page).toHaveURL(siteUrl('/settings'))
      await expect(page.getByText('You do not have access to this page.', { exact: true })).toBeVisible()
    } finally {
      await deletePrincipal(username)
    }
  })
})
