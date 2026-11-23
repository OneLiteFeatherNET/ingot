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
import { siteUrl, listRepositoryIds } from './support/api'

/*
 * The console tab is a web repl over the console socket. These tests run a command through the page and
 * decline a dangerous one, against the server that the global setup started.
 */
test.describe('console over the socket', () => {
  test('runs status and shows its output', async ({ adminPage: page }) => {
    await page.goto(siteUrl('/console'))
    const input = page.getByLabel('Console command')
    await expect(input).toBeEnabled()

    await input.fill('status')
    await input.press('Enter')

    const output = page.getByRole('region', { name: 'Console output' })
    await expect(output, 'the status report names the instance').toContainText('Status')
    await expect(output, 'the status report states whether the instance is active').toContainText('Active: true')
  })

  test('declines a token-revoke confirmation and leaves the token valid', async ({ adminPage: page }) => {
    await page.goto(siteUrl('/console'))
    const input = page.getByLabel('Console command')
    await expect(input).toBeEnabled()

    // The bootstrap token of this run is the one every other test seeds with, so revoking it would show at once.
    await input.fill(`token-revoke ${process.env.E2E_TOKEN_NAME}`)
    await input.press('Enter')

    const dialog = page.getByRole('dialog', { name: 'Run token-revoke?' })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await expect(dialog).toBeHidden()

    const output = page.getByRole('region', { name: 'Console output' })
    await expect(output, 'the console says the command did not run').toContainText('The command did not run, because it was not confirmed.')
    await expect(output, 'the revoke did not report success').not.toContainText('has been revoked')

    // The token still authorizes a request on the REST API.
    await expect(listRepositoryIds(), 'the bootstrap token still reads the repositories').resolves.toBeInstanceOf(Array)
  })
})
