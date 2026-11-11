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
import { analyzeAccessibility } from './support/accessibility.js'
import { signInAsAdmin } from './support/session.js'

// The covered pages in the state an operator sees them. The sign in dialog is checked open, on the page it opens from.
// A page with a known fault carries its marker: the rules that fail today are named, and the issue that tracks them is cited.
const COVERED_PAGES = [
  {
    name: 'the sign in dialog',
    path: '/',
    open: 'sign-in',
    knownFault: 'issue #160: color-contrast, label-content-name-mismatch and scrollable-region-focusable',
  },
  {
    name: 'the browse overview',
    path: '/',
    knownFault: 'issue #160: color-contrast, label-content-name-mismatch and scrollable-region-focusable',
  },
  { name: 'the search', path: '/search' },
  { name: 'the dashboard', path: '/dashboard', signedIn: true },
  { name: 'the access users page', path: '/access/users', signedIn: true },
  { name: 'the repositories page', path: '/settings/repositories', signedIn: true },
  { name: 'the audit page', path: '/audit', signedIn: true },
]

test.describe('accessibility of the covered pages', () => {
  for (const covered of COVERED_PAGES) {
    test(`has no serious or critical violations on ${covered.name}`, async ({ page }, testInfo) => {
      if (covered.knownFault) {
        test.fail(true, covered.knownFault)
      }
      if (covered.signedIn) {
        await signInAsAdmin(page)
      }
      await page.goto(siteUrl(covered.path))
      if (covered.open === 'sign-in') {
        await page.getByRole('button', { name: 'Sign in', exact: true }).first().click()
        await expect(page.getByRole('dialog', { name: 'Sign in' })).toBeVisible()
      } else {
        await expect(page.getByRole('navigation', { name: 'Primary' }).first()).toBeVisible()
      }

      const { gating, reported } = await analyzeAccessibility(page)
      await testInfo.attach('axe-findings', {
        body: JSON.stringify({ gating, reported }, null, 2),
        contentType: 'application/json',
      })
      expect(gating, `serious and critical violations on ${covered.name}`).toEqual([])
    })
  }
})
