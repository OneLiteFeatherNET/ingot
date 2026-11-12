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
import { signInAsAdmin, signInWithAccessToken, signOut } from './support/session.js'
import { siteUrl } from './support/api.js'

test.describe('sign in and sign out', () => {
  test('signs the instance admin in and out again', async ({ page }) => {
    await signInAsAdmin(page)
    await signOut(page)
    await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0)
  })

  test('signs in with an access token and keeps the session', async ({ page }) => {
    await page.goto(siteUrl('/'))
    await page.waitForLoadState('networkidle')

    // Every API call the page makes from here on, with the status it got.
    const answers = []
    page.on('response', (response) => {
      if (response.url().includes('/api/')) {
        answers.push({ path: new URL(response.url()).pathname, status: response.status() })
      }
    })
    await signInWithAccessToken(page)
    await page.waitForLoadState('networkidle')
    await page.goto(siteUrl('/settings'))
    await page.waitForLoadState('networkidle')

    const rejected = answers
      .filter(answer => answer.status === 401 && !answer.path.startsWith('/api/auth/'))
      .map(answer => `${answer.path} (401)`)
    expect(rejected, 'API calls the signed-in dashboard saw rejected').toEqual([])
    await expect(page.getByRole('link', { name: 'Administration' }).first()).toBeVisible()
  })

  test('a signed-out visitor sees the sign in prompt on the Administration pages', async ({ page }) => {
    await page.goto(siteUrl('/settings'))
    await expect(page.getByRole('heading', { name: 'Sign in to open Administration' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Logout', exact: true })).toHaveCount(0)
  })
})
