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

import type { Page } from '@playwright/test'
import { test, expect } from './support/fixtures'
import { deletePrincipal, siteUrl } from './support/api'
import { createPasswordUser, currentStep, enrolAuthenticator, nextCode, secretOf, totpCode } from './support/second-factor'

// The second factor of a password user, end to end: the enrolment on the account page, the second step of the sign-in
// with an authenticator code and with a recovery code, and the reset by an administrator. Every test has a user of its
// own, so the shared administrator of the run never gets a factor. The server starts with a settings key
// (global-setup.ts), which authenticator enrolments need.

const openSignIn = async (page: Page) => {
  await page.goto(siteUrl('/'))
  await page.getByRole('button', { name: 'Sign in', exact: true }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Sign in' })
  await expect(dialog).toBeVisible()
  return dialog
}

const enterPassword = async (page: Page, username: string, password: string) => {
  const dialog = await openSignIn(page)
  await dialog.getByLabel('Username', { exact: true }).fill(username)
  await dialog.getByLabel('Password', { exact: true }).fill(password)
  await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
}

const accountButton = (page: Page, username: string) => page.getByRole('button', { name: `Account menu, ${username}`, exact: true })

const secondStep = (page: Page) => page.getByRole('dialog', { name: 'Two-step verification' })

test.describe('the second factor of a password user', () => {
  let username: string

  test.beforeEach(({ seed }) => {
    username = seed.name('mfa')
  })

  test.afterEach(async () => {
    await deletePrincipal(username)
  })

  test('enrols an authenticator on the account page and shows the recovery codes once', async ({ page }) => {
    const password = await createPasswordUser(username)
    await enterPassword(page, username, password)
    await expect(accountButton(page, username), 'a user without a factor signs in with the password alone').toBeVisible()

    await page.goto(siteUrl('/account'))
    const card = page.getByRole('region', { name: 'Second factors', exact: true })
    await expect(card, 'the card starts empty').toContainText('No second factor is set up yet.')

    await card.getByRole('button', { name: 'Add an authenticator app', exact: true }).click()
    const enrolment = page.getByRole('dialog', { name: 'Add an authenticator app' })
    await enrolment.getByRole('button', { name: 'Start', exact: true }).click()
    const address = enrolment.locator('pre')
    await expect(address, 'the dialog shows the otpauth address').toContainText('otpauth://totp/')
    const secret = secretOf((await address.textContent())!.trim())

    await enrolment.getByLabel('Code from the app', { exact: true }).fill(totpCode(secret, currentStep()))
    await enrolment.getByRole('button', { name: 'Confirm', exact: true }).click()

    const codesDialog = page.getByRole('dialog', { name: 'Your recovery codes' })
    await expect(codesDialog, 'the first factor brings the recovery codes').toBeVisible()
    const codes = (await codesDialog.locator('pre').textContent())!.split('\n').map(code => code.trim()).filter(Boolean)
    expect(codes, 'ten recovery codes are shown').toHaveLength(10)

    const done = codesDialog.getByRole('button', { name: 'Done', exact: true })
    await expect(done, 'the dialog stays until the codes are stored').toBeDisabled()
    await codesDialog.getByLabel('I have stored these codes').check()
    await done.click()

    await expect(codesDialog, 'the codes are gone once the dialog closes').toBeHidden()
    await expect(page.getByText(codes[0]), 'no recovery code is left on the page').toHaveCount(0)
    await expect(card, 'the authenticator is listed').toContainText('Authenticator app')
  })

  test('asks for the authenticator code after the password and signs in with it', async ({ page }) => {
    const password = await createPasswordUser(username)
    const { secret, step } = await enrolAuthenticator(username, password)

    await enterPassword(page, username, password)
    const verification = secondStep(page)
    await expect(verification, 'the password alone does not sign in').toBeVisible()
    await expect(accountButton(page, username), 'no session before the second step').toHaveCount(0)

    // A code of five minutes ago is outside the window the server accepts.
    await verification.getByLabel('Authenticator code', { exact: true }).fill(totpCode(secret, step - 10))
    await verification.getByRole('button', { name: 'Verify', exact: true }).click()
    await expect(verification.getByRole('alert'), 'a stale code keeps the step open').toBeVisible()

    await verification.getByLabel('Authenticator code', { exact: true }).fill(nextCode(secret, step))
    await verification.getByRole('button', { name: 'Verify', exact: true }).click()

    await expect(verification, 'the right code closes the step').toBeHidden()
    await expect(accountButton(page, username), 'the user is signed in').toBeVisible()
  })

  test('signs in with a recovery code in place of the authenticator code', async ({ page }) => {
    const password = await createPasswordUser(username)
    const { recoveryCodes } = await enrolAuthenticator(username, password)

    await enterPassword(page, username, password)
    const verification = secondStep(page)
    await verification.getByRole('button', { name: 'Use a recovery code', exact: true }).click()
    await verification.getByLabel('Recovery code', { exact: true }).fill(recoveryCodes[0])
    await verification.getByRole('button', { name: 'Verify', exact: true }).click()

    await expect(verification, 'the recovery code closes the step').toBeHidden()
    await expect(accountButton(page, username), 'the user is signed in').toBeVisible()
  })

  test('lets the user sign in with the password alone after an administrator reset the factors', async ({ adminPage, browser }) => {
    const password = await createPasswordUser(username)
    await enrolAuthenticator(username, password)

    await adminPage.goto(siteUrl(`/access/users/${encodeURIComponent(username)}`))
    const factors = adminPage.getByRole('region', { name: 'Second factors', exact: true })
    await expect(factors.locator('[data-test="factor-count"]'), 'the administrator sees the authenticator').toHaveCount(1)

    await factors.getByRole('button', { name: 'Reset second factors', exact: true }).click()
    const confirm = adminPage.getByRole('dialog', { name: 'Reset second factors' })
    await expect(confirm, 'the confirmation names the user').toContainText(`All sessions of ${username} end`)
    await confirm.getByRole('button', { name: 'Reset second factors', exact: true }).click()
    await expect(confirm).toBeHidden()
    await expect(factors.locator('[data-test="no-factors"]'), 'the user has no factor left').toBeVisible()

    const context = await browser.newContext()
    try {
      const page = await context.newPage()
      await enterPassword(page, username, password)
      await expect(accountButton(page, username), 'the password signs in without a second step').toBeVisible()
      await expect(secondStep(page)).toHaveCount(0)
    } finally {
      await context.close()
    }
  })
})
