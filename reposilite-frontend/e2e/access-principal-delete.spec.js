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
import { createPrincipal, deletePrincipal, principalStatus, siteUrl } from './support/api.js'

// The user is disabled through its action menu, the state the operator reported, and then deleted through the
// dialog of its detail page.
test.describe('deleting a principal', () => {
  test('enables the delete button once the exact username is typed and deletes the user', async ({ adminPage: page, seed }) => {
    const username = seed.name('user')
    const displayName = 'Swapped display name'
    await createPrincipal({ kind: 'USER', name: username, displayName })

    try {
      await page.goto(siteUrl(`/access/users/${encodeURIComponent(username)}`))
      await expect(page.getByText(displayName, { exact: true }), 'the detail shows the display name').toBeVisible()

      const detail = page.locator('[data-ingot-component="principal-detail"]')
      await detail.getByRole('button', { name: `Actions for ${username}`, exact: true }).click()
      await page.getByRole('menuitem', { name: 'Disable', exact: true }).click()
      await expect(detail.locator('[data-test="status"]'), 'the user is disabled').toHaveText('Disabled')

      await detail.getByRole('button', { name: `Actions for ${username}`, exact: true }).click()
      await page.getByRole('menuitem', { name: 'Delete', exact: true }).click()

      const dialog = page.getByRole('dialog', { name: 'Delete user' })
      const typed = dialog.getByLabel('Type the username to confirm', { exact: true })
      const confirm = dialog.getByRole('button', { name: 'Delete', exact: true })

      await expect(dialog, 'the text names the username that has to be typed').toContainText(`${username} and their tokens are deleted`)
      await expect(dialog, 'the display name is not named as the user').not.toContainText(`${displayName} and their tokens`)

      await expect(confirm, 'nothing typed yet').toBeDisabled()

      await typed.pressSequentially(`${username}x`)
      await expect(confirm, 'a longer text does not count').toBeDisabled()

      await typed.fill(displayName)
      await expect(confirm, 'the display name does not count, only the username').toBeDisabled()

      await typed.fill(username)
      await expect(confirm, 'the exact username enables the button').toBeEnabled()

      await confirm.click()
      await expect(dialog, 'the dialog closes after the deletion').toBeHidden()
      await expect.poll(() => principalStatus(username), 'the user is gone on the server').toBe(404)
    } finally {
      await deletePrincipal(username)
    }
  })
})
