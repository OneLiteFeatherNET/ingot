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
import { siteUrl } from './support/api'
import { analyzeAccessibility } from './support/accessibility'
import { signInAsAdmin } from './support/session'

// axe reads the contrast of what is drawn at the moment it runs. A tab panel or a loaded list that is still fading in is
// drawn below full opacity, so its text is measured against a blend and fails a contrast it passes once the fade has
// ended. Under reduced motion every transition of the dashboard ends at once (src/style.css), so the check sees the
// final colours, the ones a visitor reads.
test.use({ reducedMotion: 'reduce' })

// The covered pages in the state an operator sees them. The sign in dialog is checked open, on the page it opens from.
// A page with a known fault carries its marker: the rules that fail today are named, and the issue that tracks them is cited.
const COVERED_PAGES = [
  {
    name: 'the sign in dialog',
    path: '/',
    open: 'sign-in',
    knownFault: 'issue #160: color-contrast and label-content-name-mismatch',
  },
  {
    name: 'the browse overview',
    path: '/',
    // The repository rows load after the page: the check waits for one row, so the rows it reports are on screen.
    settledLink: 'Browse releases',
    knownFault: 'issue #160: color-contrast and label-content-name-mismatch',
  },
  { name: 'the search', path: '/search' },
  { name: 'the access users page', path: '/access/users', signedIn: true },
  // The repositories table is the settle marker: its region is named for a screen reader, and the rows arrive with the page.
  { name: 'the repositories page', path: '/settings/repositories', signedIn: true, settledRegion: 'Repositories table' },
  // The buttons turn enabled when the settings domains arrive, and fade to their enabled colours over a
  // transition: the check waits for that state (see settled).
  { name: 'the Settings backup tab', path: '/maintenance?tab=backup', signedIn: true, settled: 'Import settings' },
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
      if (covered.settled) {
        await expect(page.getByRole('button', { name: covered.settled, exact: true })).toBeEnabled()
      }
      if (covered.settledRegion) {
        await expect(page.getByRole('region', { name: covered.settledRegion, exact: true })).toBeVisible()
      }
      if (covered.settledLink) {
        await expect(page.getByRole('link', { name: covered.settledLink, exact: true })).toBeVisible()
      }
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

// The tabs of one repository, in the order they are drawn. Each tab is checked at the three widths of the design.
const REPOSITORY_TABS = ['General', 'Storage', 'Access', 'Mirror and proxy', 'Policy', 'Advanced']

test.describe('accessibility of the repository tabs', () => {
  for (const width of [320, 600, 1440]) {
    test(`has no serious or critical violations on each repository tab at ${width} pixels`, async ({ adminPage: page, seed }, testInfo) => {
      const repository = await seed.repository('a11y-tabs')
      await page.setViewportSize({ width, height: 900 })
      await page.goto(siteUrl(`/settings/repositories/${repository}`))
      const tabs = page.getByRole('tablist', { name: 'Repository settings sections', exact: true })
      await expect(tabs.getByRole('tab', { name: 'General', exact: true }), 'the tab list is drawn').toBeVisible()

      for (const label of REPOSITORY_TABS) {
        await tabs.getByRole('tab', { name: label, exact: true }).click()
        await expect(tabs.getByRole('tab', { name: new RegExp(`^${label}`) }), `${label} is selected`).toHaveAttribute('aria-selected', 'true')

        const { gating, reported } = await analyzeAccessibility(page)
        await testInfo.attach(`axe-findings-${label}-${width}`, {
          body: JSON.stringify({ gating, reported }, null, 2),
          contentType: 'application/json',
        })
        expect(gating, `serious and critical violations on the ${label} tab at ${width} pixels`).toEqual([])
      }
    })
  }
})
