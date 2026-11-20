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

import { expect, type Locator, type Page } from '@playwright/test'
import { siteUrl } from './api'

/*
 * Sign in through the dashboard the way an operator does. The suite signs in with the password account
 * that the setup of this run created (global-setup.js), because a page session through that form is a
 * cookie session. The access token form is driven by its own test (see sign-in.spec.js).
 */

// The name the account button of the top bar shows for the password account of this run (global-setup.js).
const ACCOUNT_NAME = 'e2e-operator'

// A credential that the setup of this run exported (global-setup.js).
const credential = (name: string): string => {
  const value = process.env[name]
  if (value === undefined) {
    throw new Error(`${name} is not set; the global setup of the suite exports it`)
  }
  return value
}

const openSignIn = async (page: Page): Promise<Locator> => {
  await page.goto(siteUrl('/'))
  await page.getByRole('button', { name: 'Sign in', exact: true }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Sign in' })
  await expect(dialog).toBeVisible()
  return dialog
}

const signInAsAdmin = async (page: Page): Promise<void> => {
  const dialog = await openSignIn(page)
  await dialog.getByLabel('Username', { exact: true }).fill(credential('E2E_ADMIN_USER'))
  await dialog.getByLabel('Password', { exact: true }).fill(credential('E2E_ADMIN_PASSWORD'))
  await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('button', { name: `Account menu, ${ACCOUNT_NAME}`, exact: true })).toBeVisible()
}

const signInWithAccessToken = async (page: Page): Promise<void> => {
  const dialog = await openSignIn(page)
  await dialog.getByRole('button', { name: 'Use an access token instead' }).click()
  await dialog.getByLabel('Name', { exact: true }).fill(credential('E2E_TOKEN_NAME'))
  await dialog.getByLabel('Secret', { exact: true }).fill(credential('E2E_TOKEN_SECRET'))
  await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
  // The token is only kept once the server has answered the sign in, so a navigation before the account button
  // shows would cancel that request. Waiting for network idle does not cover it: the page can already have been
  // idle before the click.
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('button', { name: /^Account menu, / })).toBeVisible()
}

const signOut = async (page: Page): Promise<void> => {
  await page.getByRole('button', { name: `Account menu, ${ACCOUNT_NAME}`, exact: true }).click()
  await page.getByRole('menuitem', { name: 'Logout', exact: true }).click()
  await expect(page.getByRole('button', { name: `Account menu, ${ACCOUNT_NAME}`, exact: true })).toBeHidden()
  await expect(page.getByRole('button', { name: 'Sign in', exact: true }).first()).toBeVisible()
}

export { signInAsAdmin, signInWithAccessToken, signOut, ACCOUNT_NAME }
