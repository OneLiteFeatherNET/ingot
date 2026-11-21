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

// A visitor who holds the dashboard node and no other admin node sees the instance status on Overview, and the
// browser asks the server for no settings. The account is a user of this run with one node and an access token.
test.describe('a visitor with the dashboard node alone', () => {
  test('sees the instance status on Overview and makes no settings request', async ({ page, seed }) => {
    const username = seed.name('status-reader')
    await createPrincipal({ kind: 'USER', name: username, displayName: 'Status reader' })

    try {
      await setPrincipalNodes(username, [{ node: 'ingot.admin.dashboard', allowed: true }])
      const secret = await createPrincipalToken(username, `${username}-token`)

      await page.goto(siteUrl('/'))
      await page.getByRole('button', { name: 'Sign in', exact: true }).first().click()
      const dialog = page.getByRole('dialog', { name: 'Sign in' })
      await dialog.getByRole('button', { name: 'Use an access token instead' }).click()
      await dialog.getByLabel('Name', { exact: true }).fill(`${username}-token`)
      await dialog.getByLabel('Secret', { exact: true }).fill(secret)
      await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
      await expect(dialog, 'the token signs the visitor in').toBeHidden()

      const requests = []
      page.on('request', request => requests.push(request.url()))
      await page.goto(siteUrl('/settings'))

      await expect(page.getByRole('region', { name: 'Instance status' }), 'the status is on Overview').toBeVisible()
      await expect(page.getByRole('heading', { name: 'Danger zone', exact: true }), 'no Danger zone without the settings node').toHaveCount(0)
      await expect(page.getByRole('heading', { name: 'At a glance', exact: true }), 'no counts without the settings node').toHaveCount(0)
      expect(requests.filter(url => url.includes('/api/settings/')), 'no settings request is made').toEqual([])
    } finally {
      await deletePrincipal(username)
    }
  })
})
